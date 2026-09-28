import io
import json
import unittest
from contextlib import redirect_stderr
from urllib.error import URLError
from unittest.mock import patch

from sync_sources import append_missing, canonical_url, fetch_sources


class SourceSyncTest(unittest.TestCase):
    def test_preserves_comments_disabled_entries_and_is_idempotent(self):
        content = '# Keep retired sources disabled.\n[[sources]]\nurl = "https://github.com/old/patches"\nenabled = false\n'
        sources = ["https://github.com/old/patches", "https://github.com/SysAdminDoc/hushfeed"]
        updated, count = append_missing(content, sources * 2)
        self.assertTrue(updated.startswith(content))
        self.assertEqual(1, count)
        self.assertIn('url = "https://github.com/SysAdminDoc/hushfeed"', updated)
        self.assertEqual((updated, 0), append_missing(updated, sources))

    def test_branch_snapshots_remain_separate(self):
        content = '[[sources]]\nurl = "https://github.com/shared/patches"\n'
        dev, _ = append_missing(content, ["https://github.com/dev/patches"])
        main, _ = append_missing(content, ["https://github.com/main/patches"])
        self.assertNotIn("github.com/dev/", main)
        self.assertNotIn("github.com/main/", dev)

    def test_normalizes_trailing_slashes_without_duplicates(self):
        content = '[[sources]]\nurl = "https://github.com/example/patches"\n'
        self.assertEqual((content, 0), append_missing(
            content, ["https://github.com/example/patches/"]))

    def test_reads_every_page_even_when_server_returns_short_pages(self):
        pages = [[{"id": 4, "url": "https://github.com/a/patches"}],
                 [{"id": 9, "url": "https://gitlab.com/group/subgroup/patches"}], []]
        cursors = []

        def request(req, timeout):
            cursors.append(json.loads(req.data)["variables"]["after"])
            self.assertEqual("https://dev.example/hasura/v1/graphql", req.full_url)
            return io.BytesIO(json.dumps({"data": {"source": pages.pop(0)}}).encode())

        self.assertEqual(2, len(fetch_sources("https://dev.example/", request)))
        self.assertEqual([0, 4, 9], cursors)

    def test_skips_invalid_rows_and_advances_past_invalid_only_pages(self):
        pages = [
            [{"id": 1, "url": "https://github.com/SysAdminDoc/hushfeed"},
             {"id": 2, "url": "https://github.com/example/patches/releases"}],
            [{"id": 3, "url": "https://alice:secret@unknown.test/a/b"}],
            [{"id": 4, "url": "https://github.com/example/valid-patches"}],
            [],
        ]
        cursors = []

        def request(req, timeout):
            cursors.append(json.loads(req.data)["variables"]["after"])
            return io.BytesIO(json.dumps({"data": {"source": pages.pop(0)}}).encode())

        warnings = io.StringIO()
        with redirect_stderr(warnings):
            sources = fetch_sources("https://example.com", request)

        self.assertEqual([0, 2, 3, 4], cursors)
        self.assertEqual(["https://github.com/SysAdminDoc/hushfeed",
                          "https://github.com/example/valid-patches"], sources)
        self.assertIn("Skipping source 2", warnings.getvalue())
        self.assertIn("Skipping source 3", warnings.getvalue())
        self.assertNotIn("secret", warnings.getvalue())
        self.assertNotIn("alice", warnings.getvalue())
        content = '[[sources]]\nurl = "https://github.com/existing/patches"\n'
        updated, count = append_missing(content, sources)
        self.assertEqual(2, count)
        self.assertNotIn("/releases", updated)
        self.assertNotIn("unknown.test", updated)

    def test_transport_failure_after_valid_page_remains_fatal(self):
        responses = [io.BytesIO(json.dumps({"data": {"source": [
            {"id": 1, "url": "https://github.com/a/b"}
        ]}}).encode()), URLError("offline")]
        with patch("sync_sources.urlopen", side_effect=responses) as request:
            with self.assertRaises(URLError):
                fetch_sources("https://example.com", request)

    def test_graphql_failure_after_valid_page_remains_fatal(self):
        pages = [{"data": {"source": [{"id": 1, "url": "https://github.com/a/b"}]}},
                 {"errors": [{"message": "unavailable"}]}]
        with self.assertRaisesRegex(ValueError, "GraphQL errors"):
            fetch_sources("https://example.com", lambda *args, **kwargs:
                          io.BytesIO(json.dumps(pages.pop(0)).encode()))

    def test_rejects_graphql_errors_and_nonadvancing_pages(self):
        for response in [
            {"errors": [{"message": "unavailable"}]},
            {"data": {"source": None}},
            {"data": {"source": [{"id": 0, "url": "https://github.com/a/b"}]}},
        ]:
            with self.subTest(response=response), self.assertRaises(ValueError):
                fetch_sources("https://example.com", lambda *args, **kwargs:
                              io.BytesIO(json.dumps(response).encode()))

    def test_rejects_invalid_urls_before_writing(self):
        for url in ["https://github.com/a/b/releases", "https://github.com/a/b.git",
                    "https://alice:secret@github.com/a/b", "https://github.com/a/b?q=x",
                    "https://github.com/a/b\n", "https://unknown.test/a/b"]:
            with self.subTest(url=url), self.assertRaises(ValueError):
                canonical_url(url)

    def test_supports_configured_hosts(self):
        with patch.dict("os.environ", {"BACKEND_GIT_HOSTS": "git.example=gitlab"}):
            self.assertEqual("https://git.example/group/subgroup/patches",
                             canonical_url("https://git.example/group/subgroup/patches"))

    def test_malformed_manifest_is_not_replaced(self):
        with self.assertRaises(Exception):
            append_missing("invalid toml =", ["https://github.com/a/b"])


if __name__ == "__main__":
    unittest.main()
