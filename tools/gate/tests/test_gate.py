"""quality-gate 집계 상태 테스트 (AC-06·AC-08 의 집계 fixture 계층).

실행: python -m unittest discover -s tools/gate/tests -v
"""

from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import gate  # noqa: E402
from result import REQUIRED_CHECKS, make_finding, make_result  # noqa: E402

CTX = {
    "repository": "jaezero/AIPRGATE", "pr_number": 7, "head_sha": "h" * 40, "base_sha": "b" * 40,
    "tested_sha": "m" * 40, "run_id": "1001", "run_attempt": 1, "policy_revision": "sha256:rev",
}
EXPECTED = gate.Expected(run_id="1001", run_attempt=1, head_sha="h" * 40, base_sha="b" * 40,
                         policy_revision="sha256:rev", repository="jaezero/AIPRGATE", pr_number=7)
TOOL = {"name": "fixture", "version": "1", "rule_revision": None}


def result(check_id: str, status: str = "completed", findings=None, error=None, ctx=None, **extra):
    return make_result(check_id=check_id, context=ctx or CTX, tool=TOOL, execution_status=status,
                       started_at="2026-09-21T00:00:00+00:00", finished_at="2026-09-21T00:01:00+00:00",
                       findings=findings, error=error, raw_report="fixture", extra=extra or None)


def block(policy="POL-X", rule="rule-x"):
    return make_finding(policy_id=policy, rule_id=rule, message="위반", policy_action="BLOCK",
                        file="app/src/main/X.kt", line=3)


def warn():
    return make_finding(policy_id="POL-W", rule_id="rule-w", message="경고", policy_action="WARN")


class GateTest(unittest.TestCase):

    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.dir = Path(self._tmp.name) / "results"
        self.dir.mkdir()
        # 판정 출력 파일은 결과 디렉터리 밖에 둔다. 안에 두면 다음 집계가 결과로 읽는다.
        self.out = Path(self._tmp.name) / "out"
        self.out.mkdir()

    def tearDown(self):
        self._tmp.cleanup()

    def put(self, data, name=None, sub=None):
        target = self.dir / (sub or (data["check_id"] if isinstance(data, dict) else "x"))
        target.mkdir(parents=True, exist_ok=True)
        path = target / (name or f"{data['check_id']}.json")
        path.write_text(json.dumps(data) if not isinstance(data, str) else data, encoding="utf-8")

    def all_ok(self, skip=()):
        for c in REQUIRED_CHECKS:
            if c not in skip:
                self.put(result(c))

    def run_gate(self, needs=None):
        return gate.evaluate(self.dir, EXPECTED, needs)

    # ── 기본 판정 ──

    def test_모든_필수_검사_완료_BLOCK_없음이면_PASS(self):
        self.all_ok()
        v = self.run_gate()
        self.assertEqual(gate.PASS, v.verdict)
        self.assertEqual("m" * 40, v.tested_sha)

    def test_BLOCK_finding_이_있으면_POLICY_FAIL(self):
        self.all_ok(skip=("quality-check",))
        self.put(result("quality-check", findings=[block()]))
        v = self.run_gate()
        self.assertEqual(gate.POLICY_FAIL, v.verdict)
        self.assertEqual(1, len(v.policy_violations))
        self.assertEqual([], v.check_errors)

    def test_WARN_만_있으면_통과하고_경고로_표시(self):
        self.all_ok(skip=("quality-check",))
        self.put(result("quality-check", findings=[warn()]))
        v = self.run_gate()
        self.assertEqual(gate.PASS, v.verdict)
        self.assertEqual(1, len(v.warnings))

    def test_위반과_검사오류가_함께_있으면_두_사유를_모두_기록(self):
        self.all_ok(skip=("quality-check", "secret-check"))
        self.put(result("quality-check", findings=[block()]))
        self.put(result("secret-check", status="error", error={"code": "TIMEOUT", "message": "시간 초과"}))
        v = self.run_gate()
        self.assertEqual(gate.CHECK_ERROR, v.verdict)
        self.assertEqual(1, len(v.policy_violations))
        self.assertIn("TIMEOUT", [e["code"] for e in v.check_errors])

    # ── 검사 오류 (GAT-03) ──

    def test_결과_누락은_CHECK_ERROR(self):
        self.all_ok(skip=("secret-check",))
        v = self.run_gate()
        self.assertEqual(gate.CHECK_ERROR, v.verdict)
        self.assertEqual("missing", v.checks["secret-check"])

    def test_결과가_하나도_없으면_CHECK_ERROR(self):
        v = self.run_gate()
        self.assertEqual(gate.CHECK_ERROR, v.verdict)
        self.assertEqual(len(REQUIRED_CHECKS), len(v.check_errors))

    def test_중복_결과는_CHECK_ERROR(self):
        self.all_ok()
        self.put(result("app-check"), name="app-check-copy.json", sub="other")
        v = self.run_gate()
        self.assertEqual(gate.CHECK_ERROR, v.verdict)
        self.assertIn("DUPLICATE", [e["code"] for e in v.check_errors])

    def test_JSON_파싱_오류는_CHECK_ERROR이고_빈_결과로_바꾸지_않는다(self):
        self.all_ok(skip=("pr-info",))
        (self.dir / "pr-info").mkdir()
        (self.dir / "pr-info" / "pr-info.json").write_text("{not json", encoding="utf-8")
        v = self.run_gate()
        self.assertEqual(gate.CHECK_ERROR, v.verdict)
        codes = [e["code"] for e in v.check_errors]
        self.assertIn("PARSE_ERROR", codes)
        self.assertIn("MISSING", codes)

    def test_스키마_필드_누락은_CHECK_ERROR(self):
        self.all_ok(skip=("app-check",))
        bad = result("app-check")
        del bad["findings"]
        self.put(bad)
        v = self.run_gate()
        self.assertEqual(gate.CHECK_ERROR, v.verdict)
        self.assertIn("SCHEMA_ERROR", [e["code"] for e in v.check_errors])

    def test_알수없는_policy_action은_스키마_오류(self):
        self.all_ok(skip=("app-check",))
        bad = result("app-check", findings=[block()])
        bad["findings"][0]["policy_action"] = "IGNORE"
        self.put(bad)
        v = self.run_gate()
        self.assertEqual(gate.CHECK_ERROR, v.verdict)

    def test_다른_run_의_결과는_사용하지_않는다(self):
        self.all_ok(skip=("app-check",))
        self.put(result("app-check", ctx={**CTX, "run_id": "999"}))
        v = self.run_gate()
        self.assertEqual(gate.CHECK_ERROR, v.verdict)
        self.assertIn("IDENTITY_MISMATCH", [e["code"] for e in v.check_errors])

    def test_다른_attempt_의_결과는_사용하지_않는다(self):
        self.all_ok(skip=("app-check",))
        self.put(result("app-check", ctx={**CTX, "run_attempt": 2}))
        self.assertEqual(gate.CHECK_ERROR, self.run_gate().verdict)

    def test_오래된_head_sha_결과는_CHECK_ERROR(self):
        self.all_ok(skip=("app-check",))
        self.put(result("app-check", ctx={**CTX, "head_sha": "o" * 40}))
        self.assertEqual(gate.CHECK_ERROR, self.run_gate().verdict)

    def test_정책_revision_불일치는_CHECK_ERROR(self):
        self.all_ok(skip=("quality-check",))
        self.put(result("quality-check", ctx={**CTX, "policy_revision": "sha256:other"}))
        self.assertEqual(gate.CHECK_ERROR, self.run_gate().verdict)

    def test_검사마다_tested_sha_가_다르면_CHECK_ERROR(self):
        self.all_ok(skip=("secret-check",))
        self.put(result("secret-check", ctx={**CTX, "tested_sha": "z" * 40}))
        v = self.run_gate()
        self.assertEqual(gate.CHECK_ERROR, v.verdict)
        self.assertIn("TESTED_SHA_MISMATCH", [e["code"] for e in v.check_errors])

    def test_skipped_결과는_통과가_아니다(self):
        self.all_ok(skip=("pr-info",))
        self.put(result("pr-info", status="skipped",
                        error={"code": "NOT_CONNECTED", "message": "아직 연결하지 않음"}))
        v = self.run_gate()
        self.assertEqual(gate.CHECK_ERROR, v.verdict)
        self.assertIn("NOT_CONNECTED", [e["code"] for e in v.check_errors])

    def test_cancelled_결과는_통과가_아니다(self):
        self.all_ok(skip=("app-check",))
        self.put(result("app-check", status="cancelled"))
        self.assertEqual(gate.CHECK_ERROR, self.run_gate().verdict)

    def test_timeout_은_검사_오류로_구분된다(self):
        self.all_ok(skip=("app-check",))
        self.put(result("app-check", status="error", error={"code": "TIMEOUT", "message": "30분 초과"}))
        v = self.run_gate()
        self.assertEqual(gate.CHECK_ERROR, v.verdict)
        self.assertEqual([], v.policy_violations)

    def test_하위_검사가_skipped_이면_필수_미완료(self):
        self.all_ok(skip=("app-check",))
        self.put(result("app-check", findings=[block("APP-BUILD", "compile-error")],
                        subchecks={"build": {"status": "completed", "result": "failed"},
                                   "test": {"status": "skipped", "reason": "build_failed"}}))
        v = self.run_gate()
        self.assertEqual(gate.CHECK_ERROR, v.verdict)
        self.assertEqual(1, len(v.policy_violations))
        self.assertIn("SUBCHECK_SKIPPED", [e["code"] for e in v.check_errors])

    def test_job_이_cancelled_이면_artifact_가_있어도_통과하지_않는다(self):
        self.all_ok()
        v = self.run_gate(needs={"app-check": {"result": "cancelled"}})
        self.assertEqual(gate.CHECK_ERROR, v.verdict)

    def test_필수_목록에_없는_결과는_판정에_쓰지_않고_경고만(self):
        self.all_ok()
        self.put(result("extra-check", findings=[block()]))
        v = self.run_gate()
        self.assertEqual(gate.PASS, v.verdict)
        self.assertIn("UNKNOWN_CHECK", [w.get("code") for w in v.warnings])

    # ── CLI ──

    def test_CLI_는_PASS_일때만_종료코드_0(self):
        self.all_ok()
        args = ["--results", str(self.dir), "--run-id", "1001", "--run-attempt", "1",
                "--head-sha", "h" * 40, "--base-sha", "b" * 40, "--policy-revision", "sha256:rev",
                "--summary", str(self.out / "summary.md"), "--out", str(self.out / "verdict.json")]
        self.assertEqual(0, gate.main(args))
        self.assertIn("PASS", (self.out / "summary.md").read_text(encoding="utf-8"))
        (self.dir / "pr-info" / "pr-info.json").unlink()
        self.assertEqual(1, gate.main(args))

    def test_needs_JSON_을_읽지_못하면_CHECK_ERROR(self):
        self.all_ok()
        args = ["--results", str(self.dir), "--run-id", "1001", "--run-attempt", "1",
                "--head-sha", "h" * 40, "--base-sha", "b" * 40, "--policy-revision", "sha256:rev",
                "--needs-json", "{broken", "--out", str(self.out / "verdict.json")]
        self.assertEqual(1, gate.main(args))
        verdict = json.loads((self.out / "verdict.json").read_text(encoding="utf-8"))
        self.assertEqual(gate.CHECK_ERROR, verdict["verdict"])

    def test_요약의_파이프_문자는_이스케이프된다(self):
        self.all_ok(skip=("quality-check",))
        f = block()
        f["message"] = "a|b\nc"
        self.put(result("quality-check", findings=[f]))
        v = self.run_gate()
        text = gate.render_summary(v, EXPECTED)
        self.assertIn("a\\|b c", text)


class AnnotationTest(unittest.TestCase):

    def test_주석은_판정과_사유를_담고_명령_문자를_이스케이프한다(self):
        v = gate.Verdict(verdict=gate.CHECK_ERROR)
        f = block()
        f["message"] = "줄1\n::error::주입"
        v.policy_violations.append({"check_id": "quality-check", **f})
        v.check_errors.append({"check_id": "pr-info", "code": "NOT_CONNECTED", "message": "100% 미연결"})
        lines = gate.render_annotations(v)
        self.assertTrue(lines[0].startswith("::error title=quality-gate CHECK_ERROR::"))
        self.assertIn("file=app/src/main/X.kt,line=3,", lines[1])
        self.assertNotIn("\n", "".join(lines))
        self.assertIn("줄1%0A::error::주입", lines[1])
        self.assertIn("100%25 미연결", lines[2])
        self.assertEqual(3, len(lines))

    def test_주석은_annotations_옵션이_있을_때만_출력한다(self):
        import contextlib, io
        with tempfile.TemporaryDirectory() as d:
            base = ["--results", d, "--run-id", "1", "--run-attempt", "1", "--head-sha", "h",
                    "--base-sha", "b", "--policy-revision", "r"]
            buf = io.StringIO()
            with contextlib.redirect_stdout(buf):
                gate.main(base)
            self.assertNotIn("::error", buf.getvalue())
            buf = io.StringIO()
            with contextlib.redirect_stdout(buf):
                gate.main(base + ["--annotations"])
            self.assertIn("::error title=quality-gate CHECK_ERROR::", buf.getvalue())

    def test_PASS_는_notice_로_표시한다(self):
        self.assertTrue(gate.render_annotations(gate.Verdict())[0].startswith("::notice "))


if __name__ == "__main__":
    unittest.main()
