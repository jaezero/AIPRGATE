"""app-check adapter 테스트 (CI-03, AC-06 의 원인 구분)."""

from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import app_check  # noqa: E402

ROOT = Path("/work/repo")

PASS_XML = """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.example.FooTest" tests="2" skipped="0" failures="0" errors="0">
  <testcase name="a" classname="com.example.FooTest" time="0.01"/>
  <testcase name="b" classname="com.example.FooTest" time="0.01"/>
</testsuite>"""

FAIL_XML = """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.example.FooTest" tests="2" skipped="0" failures="1" errors="0">
  <testcase name="a" classname="com.example.FooTest" time="0.01"/>
  <testcase name="공백_입력은_저장하지_않는다" classname="com.example.FooTest" time="0.01">
    <failure message="expected:&lt;[]&gt; but was:&lt;[x]&gt;" type="java.lang.AssertionError">trace</failure>
  </testcase>
</testsuite>"""

COMPILE_LOG = """> Task :app:compileDebugKotlin FAILED
e: file:///work/repo/app/src/main/java/com/example/aiprgate/MainActivity.kt:21:17 Unresolved reference 'repositry'.
FAILURE: Build failed with an exception.
* What went wrong:
Execution failed for task ':app:compileDebugKotlin'.
"""

INFRA_LOG = """* What went wrong:
Could not resolve all files for configuration ':app:debugRuntimeClasspath'.
> Could not GET 'https://dl.google.com/...'. Connection reset
"""


class AppCheckTest(unittest.TestCase):

    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.dir = Path(self._tmp.name)

    def tearDown(self):
        self._tmp.cleanup()

    def junit(self, *xmls):
        for i, x in enumerate(xmls):
            (self.dir / f"TEST-{i}.xml").write_text(x, encoding="utf-8")
        return app_check.parse_junit(self.dir)

    def test_빌드와_테스트가_성공하면_completed_이고_finding_없음(self):
        out = app_check.evaluate(build_exit=0, build_log="", test_exit=0, junit=self.junit(PASS_XML), root=ROOT)
        self.assertEqual("completed", out["execution_status"])
        self.assertEqual([], out["findings"])
        self.assertEqual(2, out["subchecks"]["test"]["tests"])

    def test_컴파일_오류는_위치가_있는_BLOCK_이고_테스트는_skipped(self):
        out = app_check.evaluate(build_exit=1, build_log=COMPILE_LOG, test_exit=None, junit=self.junit(), root=ROOT)
        self.assertEqual("completed", out["execution_status"])
        f = out["findings"][0]
        self.assertEqual(("APP-BUILD", "BLOCK"), (f["policy_id"], f["policy_action"]))
        self.assertEqual("app/src/main/java/com/example/aiprgate/MainActivity.kt", f["file"])
        self.assertEqual(21, f["line"])
        self.assertEqual("skipped", out["subchecks"]["test"]["status"])
        self.assertEqual("build_failed", out["subchecks"]["test"]["reason"])

    def test_컴파일_외_빌드_실패는_실행_오류이고_코드_위반이_아니다(self):
        out = app_check.evaluate(build_exit=1, build_log=INFRA_LOG, test_exit=None, junit=self.junit(), root=ROOT)
        self.assertEqual("error", out["execution_status"])
        self.assertEqual("BUILD_EXECUTION_ERROR", out["error"]["code"])
        self.assertEqual([], out["findings"])

    def test_실패한_테스트는_이름별_BLOCK(self):
        out = app_check.evaluate(build_exit=0, build_log="", test_exit=1, junit=self.junit(FAIL_XML), root=ROOT)
        self.assertEqual("completed", out["execution_status"])
        self.assertEqual(1, len(out["findings"]))
        f = out["findings"][0]
        self.assertEqual("APP-TEST", f["policy_id"])
        self.assertIn("공백_입력은_저장하지_않는다", f["message"])

    def test_테스트_task_실패인데_실패_테스트가_없으면_실행_오류(self):
        out = app_check.evaluate(build_exit=0, build_log="", test_exit=1, junit=self.junit(PASS_XML), root=ROOT)
        self.assertEqual("error", out["execution_status"])
        self.assertEqual("TEST_EXECUTION_ERROR", out["error"]["code"])

    def test_JUnit_결과가_없으면_실행_오류(self):
        out = app_check.evaluate(build_exit=0, build_log="", test_exit=0, junit=self.junit(), root=ROOT)
        self.assertEqual("error", out["execution_status"])
        self.assertEqual("TEST_REPORT_MISSING", out["error"]["code"])

    def test_깨진_XML_은_빈_결과로_바꾸지_않는다(self):
        out = app_check.evaluate(build_exit=0, build_log="", test_exit=0,
                                 junit=self.junit(PASS_XML, "<testsuite"), root=ROOT)
        self.assertEqual("error", out["execution_status"])
        self.assertEqual("TEST_REPORT_PARSE_ERROR", out["error"]["code"])

    def test_성공_종료인데_XML_에_실패가_있으면_불일치_오류(self):
        out = app_check.evaluate(build_exit=0, build_log="", test_exit=0, junit=self.junit(FAIL_XML), root=ROOT)
        self.assertEqual("error", out["execution_status"])
        self.assertEqual("TEST_RESULT_INCONSISTENT", out["error"]["code"])


if __name__ == "__main__":
    unittest.main()
