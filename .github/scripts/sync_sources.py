"""Append database-only sources to the current branch's tracked manifest."""
import argparse
import json
import os
from pathlib import Path
import re
import sys
import tomllib
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


def fetch_sources(endpoint, request=urlopen):
    query = """query Sources($after: Int!, $limit: Int!) {
      source(where: {id: {_gt: $after}}, order_by: {id: asc}, limit: $limit) {
        id url
      }
    }"""
    sources = []
    after = 0
    while True:
        body = json.dumps({"query": query, "variables": {"after": after, "limit": 100}}).encode()
        req = Request(endpoint.rstrip("/") + "/hasura/v1/graphql",
                      data=body, headers={"Content-Type": "application/json",
                                          "Accept": "application/json",
                                          "User-Agent": "revanced-external-bundles-source-sync"})
        with request(req, timeout=60) as response:
            payload = json.load(response)
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
    parser.add_argument("--endpoint", required=True)
    parser.add_argument("--manifest", type=Path, default=Path("src/main/resources/sources.toml"))
    args = parser.parse_args()
    sources = fetch_sources(args.endpoint)
    content = args.manifest.read_text(encoding="utf-8")
    updated, count = append_missing(content, sources)
    if count:
        args.manifest.write_text(updated, encoding="utf-8", newline="\n")
    print(f"Added {count} missing source(s) to {args.manifest}")


if __name__ == "__main__":
    main()
