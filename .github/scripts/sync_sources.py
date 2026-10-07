"""Append database-only sources to the current branch's tracked manifest."""
import argparse
from email.utils import parsedate_to_datetime
import json
import os
from pathlib import Path
import re
import sys
import time
import tomllib
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import Request, urlopen


def canonical_url(value):
    """Accept repository roots on the same configured hosts as the backend."""
    hosts = {"github.com": "github", "gitlab.com": "gitlab",
             "codeberg.org": "gitea", "gitea.com": "gitea"}
    for entry in os.environ.get("BACKEND_GIT_HOSTS", "").split(","):
        authority, separator, kind = entry.strip().partition("=")
        if separator and kind.strip().lower() in {"github", "gitlab", "gitea"}:
            hosts[authority.strip().lower()] = kind.strip().lower()
    if not isinstance(value, str) or re.search(r"[\s\\\\]", value):
        raise ValueError("Invalid source URL")
    parsed = urlsplit(value)
    kind = hosts.get(parsed.netloc.lower())
    parts = parsed.path.strip("/").split("/")
    if (parsed.scheme not in {"https", "http"} or not kind
            or parsed.username or parsed.password or parsed.query or parsed.fragment
            or len(parts) < 2 or any(part in {"", ".", "..", "-"} for part in parts)
            or parts[-1].lower().endswith(".git")
            or (kind != "gitlab" and len(parts) != 2)):
        raise ValueError("Source must be a supported repository root")
    return f"{parsed.scheme}://{parsed.netloc.lower()}/{'/'.join(parts)}"


def _retry_delay(error, attempt):
    delay = 2 ** (attempt + 1)
    header = error.headers.get("Retry-After") if isinstance(error, HTTPError) and error.headers else None
    if not header:
        return delay
    try:
        return max(delay, int(header))
    except (ValueError, TypeError):
        try:
            retry_at = parsedate_to_datetime(header)
            if retry_at.tzinfo is not None:
                return max(delay, retry_at.timestamp() - time.time())
        except (ValueError, TypeError, OverflowError):
            pass
    return delay


def fetch_sources(endpoint, request=urlopen, *, total_timeout=90, request_timeout=15):
    query = """query Sources($after: Int!, $limit: Int!) {
      source(where: {id: {_gt: $after}}, order_by: {id: asc}, limit: $limit) {
        id url
      }
    }"""
    sources = []
    after = 0
    deadline = time.monotonic() + total_timeout
    while True:
        body = json.dumps({"query": query, "variables": {"after": after, "limit": 100}}).encode()
        req = Request(endpoint.rstrip("/") + "/hasura/v1/graphql",
                      data=body, headers={"Content-Type": "application/json",
                                          "Accept": "application/json",
                                          "User-Agent": "revanced-external-bundles-source-sync"})
        for attempt in range(3):
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise TimeoutError("Source export exceeded its total time limit")
            try:
                with request(req, timeout=min(request_timeout, remaining)) as response:
                    payload = json.load(response)
                break
            except (URLError, TimeoutError, ConnectionError) as error:
                # Retry transport failures and temporary HTTP errors on the same page.
                if isinstance(error, HTTPError) and error.code != 429 and error.code < 500:
                    raise
                if attempt == 2:
                    raise
                delay = _retry_delay(error, attempt)
                if isinstance(error, HTTPError):
                    error.close()
                if deadline - time.monotonic() <= delay:
                    raise TimeoutError("Source export exceeded its total time limit") from error
                print(f"Source export page after ID {after} failed; retrying in {delay}s",
                      file=sys.stderr)
                time.sleep(delay)
        # Socket timeouts apply to blocking operations, not the complete response.
        # A response that finishes late must not be accepted as a successful export.
        if time.monotonic() >= deadline:
            raise TimeoutError("Source export exceeded its total time limit")
        if payload.get("errors"):
            raise ValueError("Source export returned GraphQL errors")
        batch = payload["data"]["source"]
        if not isinstance(batch, list):
            raise ValueError("Source export did not return a source list")
        if not batch:
            return sources
        for source in batch:
            source_id = source["id"]
            if type(source_id) is not int or source_id <= after:
                raise ValueError("Source export did not advance its pagination cursor")
            # Advance even for rejected rows, including pages with no valid sources.
            after = source_id
            try:
                url = canonical_url(source["url"])
            except ValueError:
                # Report the row ID without exposing credentials in malformed URLs.
                print(f"Skipping source {source_id}: invalid or unsupported repository URL",
                      file=sys.stderr)
                continue
            sources.append(url)


def append_missing(content, sources):
    entries = tomllib.loads(content)["sources"]
    known = {canonical_url(entry["url"]) for entry in entries}
    missing = sorted({canonical_url(url) for url in sources} - known)
    if not missing:
        return content, 0
    # Preserve comments and all explicit enabled=false decisions. Database-only entries
    # default to enabled, recovering the former omission-based disabling behavior.
    addition = "".join(f"\n[[sources]]\nurl = {json.dumps(url)}\n" for url in missing)
    updated = content.rstrip() + "\n" + addition
    tomllib.loads(updated)
    return updated, len(missing)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--endpoint")
    source.add_argument("--sources-file", type=Path, help="Use a previously exported source snapshot")
    parser.add_argument("--export", type=Path, help="Export a snapshot without modifying the manifest")
    parser.add_argument("--manifest", type=Path, default=Path("src/main/resources/sources.toml"))
    args = parser.parse_args()
    if args.export and not args.endpoint:
        parser.error("--export requires --endpoint")
    if args.sources_file:
        sources = json.loads(args.sources_file.read_text(encoding="utf-8"))
        if not isinstance(sources, list):
            raise ValueError("Source snapshot must contain a list")
        sources = [canonical_url(url) for url in sources]
    else:
        sources = fetch_sources(args.endpoint)
    if args.export:
        args.export.write_text(json.dumps(sources) + "\n", encoding="utf-8")
        print(f"Exported {len(sources)} source(s) to {args.export}")
        return
    content = args.manifest.read_text(encoding="utf-8")
    updated, count = append_missing(content, sources)
    if count:
        args.manifest.write_text(updated, encoding="utf-8", newline="\n")
    print(f"Added {count} missing source(s) to {args.manifest}")


if __name__ == "__main__":
    main()
