import io
import json
import unittest
from contextlib import redirect_stderr
from pathlib import Path
from tempfile import TemporaryDirectory
from urllib.error import HTTPError, URLError
from unittest.mock import patch

from sync_sources import append_missing, canonical_url, fetch_sources, main


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
        ]}}).encode()), URLError("offline"), URLError("offline"), URLError("offline")]
        with patch("sync_sources.urlopen", side_effect=responses) as request, \
                patch("sync_sources.time.sleep"):
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

    def test_timeout_retries_same_page_without_duplicate_sources(self):
        pages = [
            [{"id": 1, "url": "https://github.com/a/b"}],
            TimeoutError("read timed out"),
            [{"id": 2, "url": "https://github.com/c/d"}],
            [],
        ]
        cursors = []

        def request(req, timeout):
            cursors.append(json.loads(req.data)["variables"]["after"])
            self.assertLessEqual(timeout, 15)
            response = pages.pop(0)
            if isinstance(response, Exception):
                raise response
            return io.BytesIO(json.dumps({"data": {"source": response}}).encode())

        with patch("sync_sources.time.sleep") as sleep, redirect_stderr(io.StringIO()):
            self.assertEqual(["https://github.com/a/b", "https://github.com/c/d"],
                             fetch_sources("https://example.com", request))
        self.assertEqual([0, 1, 1, 2], cursors)
        sleep.assert_called_once_with(2)

    def test_total_budget_applies_across_pages(self):
        with patch("sync_sources.time.monotonic", side_effect=[0, 0, 11]), \
                patch("sync_sources.urlopen", return_value=io.BytesIO(json.dumps(
                    {"data": {"source": [{"id": 1, "url": "https://github.com/a/b"}]}}
                ).encode())) as request:
            with self.assertRaisesRegex(TimeoutError, "total time limit"):
                fetch_sources("https://example.com", request, total_timeout=10)
        self.assertEqual(1, request.call_count)
        self.assertEqual(10, request.call_args.kwargs["timeout"])

    def test_rejects_response_completed_after_total_deadline(self):
        with patch("sync_sources.time.monotonic", side_effect=[0, 0, 11]), \
                patch("sync_sources.urlopen", return_value=io.BytesIO(
                    b'{"data": {"source": []}}')) as request:
            with self.assertRaisesRegex(TimeoutError, "total time limit"):
                fetch_sources("https://example.com", request, total_timeout=10)
        self.assertEqual(1, request.call_count)

    def test_respects_retry_after_before_repeating_request(self):
        for status in [429, 503]:
            for header in ["30", "Thu, 01 Jan 1970 00:00:30 GMT"]:
                with self.subTest(status=status, header=header):
                    responses = [
                        HTTPError("https://example.com", status, "Busy",
                                  {"Retry-After": header}, None),
                        io.BytesIO(b'{"data": {"source": []}}'),
                    ]
                    with patch("sync_sources.urlopen", side_effect=responses) as request, \
                            patch("sync_sources.time.monotonic", return_value=0), \
                            patch("sync_sources.time.time", return_value=0), \
                            patch("sync_sources.time.sleep") as sleep, \
                            redirect_stderr(io.StringIO()):
                        self.assertEqual([], fetch_sources("https://example.com", request))
                    self.assertEqual(2, request.call_count)
                    sleep.assert_called_once_with(30)

    def test_retry_after_beyond_budget_stops_without_an_early_retry(self):
        error = HTTPError("https://example.com", 429, "Busy", {"Retry-After": "120"}, None)
        with patch("sync_sources.urlopen", side_effect=[
                error, io.BytesIO(b'{"data": {"source": []}}')]) as request, \
                patch("sync_sources.time.monotonic", return_value=0), \
                patch("sync_sources.time.sleep") as sleep:
            with self.assertRaisesRegex(TimeoutError, "total time limit"):
                fetch_sources("https://example.com", request)
        self.assertEqual(1, request.call_count)
        sleep.assert_not_called()

    def test_persistent_timeout_has_only_three_attempts(self):
        with patch("sync_sources.time.monotonic", return_value=0), \
                patch("sync_sources.urlopen", side_effect=TimeoutError("offline")) as request, \
                patch("sync_sources.time.sleep") as sleep, redirect_stderr(io.StringIO()):
            with self.assertRaises(TimeoutError):
                fetch_sources("https://example.com", request)
        self.assertEqual(3, request.call_count)
        self.assertEqual([2, 4], [call.args[0] for call in sleep.call_args_list])

    def test_permanent_http_errors_are_not_retried(self):
        error = HTTPError("https://example.com", 403, "Forbidden", {}, None)
        with patch("sync_sources.urlopen", side_effect=error) as request, \
                patch("sync_sources.time.sleep") as sleep:
            with self.assertRaises(HTTPError):
                fetch_sources("https://example.com", request)
        self.assertEqual(1, request.call_count)
        sleep.assert_not_called()

    def test_temporary_http_error_is_retried(self):
        responses = [HTTPError("https://example.com", 503, "Unavailable", {}, None),
                     io.BytesIO(b'{"data": {"source": []}}')]
        with patch("sync_sources.urlopen", side_effect=responses) as request, \
                patch("sync_sources.time.sleep"), redirect_stderr(io.StringIO()):
            self.assertEqual([], fetch_sources("https://example.com", request))
        self.assertEqual(2, request.call_count)

    def test_export_then_reuse_snapshot_without_network_or_manifest_loss(self):
        with TemporaryDirectory() as directory:
            manifest = Path(directory) / "sources.toml"
            snapshot = Path(directory) / "sources.json"
            original = '# Keep disabled\n[[sources]]\nurl = "https://github.com/a/b"\nenabled = false\n'
            manifest.write_text(original, encoding="utf-8")
            with patch("sys.argv", ["sync_sources", "--endpoint", "https://example.com",
                                    "--export", str(snapshot), "--manifest", str(manifest)]), \
                    patch("sync_sources.fetch_sources", return_value=["https://github.com/a/b",
                                                                    "https://github.com/c/d"]):
                main()
            self.assertEqual(original, manifest.read_text(encoding="utf-8"))
            # Another push can change the manifest; the cached export must merge into it.
            manifest.write_text(original + '\n[[sources]]\nurl = "https://github.com/e/f"\n',
                                encoding="utf-8")
            with patch("sys.argv", ["sync_sources", "--sources-file", str(snapshot),
                                    "--manifest", str(manifest)]), \
                    patch("sync_sources.fetch_sources") as fetch:
                main()
                once = manifest.read_text(encoding="utf-8")
                main()
            fetch.assert_not_called()
            self.assertEqual(once, manifest.read_text(encoding="utf-8"))
            self.assertIn("enabled = false", once)
            self.assertIn("github.com/e/f", once)
            self.assertIn("github.com/c/d", once)

    def test_invalid_snapshot_does_not_modify_manifest(self):
        with TemporaryDirectory() as directory:
            manifest = Path(directory) / "sources.toml"
            snapshot = Path(directory) / "sources.json"
            original = '[[sources]]\nurl = "https://github.com/a/b"\n'
            manifest.write_text(original, encoding="utf-8")
            snapshot.write_text('["https://github.com/c/d", "bad-url"]', encoding="utf-8")
            with patch("sys.argv", ["sync_sources", "--sources-file", str(snapshot),
                                    "--manifest", str(manifest)]):
                with self.assertRaises(ValueError):
                    main()
            self.assertEqual(original, manifest.read_text(encoding="utf-8"))

    def test_malformed_manifest_is_not_replaced(self):
        with self.assertRaises(Exception):
            append_missing("invalid toml =", ["https://github.com/a/b"])


if __name__ == "__main__":
    unittest.main()
