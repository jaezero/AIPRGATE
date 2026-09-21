"""app-check 결과 adapter (CI-03).

debug 빌드와 JVM 테스트의 종료 코드·로그·JUnit XML 을 정규화 결과로 바꾼다.

판정 원칙:
- 컴파일 오류는 코드 문제이므로 BLOCK finding(APP-BUILD)으로 기록한다.
- 컴파일 오류 흔적 없이 빌드가 실패하면(의존성 다운로드 실패 등) 실행 오류로 기록한다.
- 빌드 실패로 테스트를 못 돌리면 test 하위 검사를 skipped 로 남긴다. gate 는 이를 필수 미완료로 본다.
- 실패한 테스트는 테스트 이름별 BLOCK finding(APP-TEST)으로 기록한다.
- 테스트 task 가 실패했는데 실패 테스트를 XML 에서 찾지 못하면 실행 오류로 기록한다.
  파싱할 수 없는 결과를 빈 findings 로 바꾸지 않는다 (SRS 5.2).
"""

from __future__ import annotations

import argparse
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any

sys.path.insert(0, str(Path(__file__).resolve().parent))

from result import context_from_env, make_finding, make_result, now_iso, write_result  # noqa: E402

# Kotlin 컴파일러 오류 줄, 또는 컴파일·KSP task 실패 문구
_COMPILE_ERROR = re.compile(
    r"^e: |Execution failed for task ':app:(compile\w*Kotlin|compile\w*JavaWithJavac|ksp\w*Kotlin)'",
    re.MULTILINE,
)
_KOTLIN_ERROR_LOCATION = re.compile(r"^e: (?:file:///)?(?P<file>[^:\n]+?\.kts?):(?P<line>\d+):\d+ (?P<msg>.*)$", re.MULTILINE)


def _read(path: str | None) -> str:
    if not path:
        return ""
    p = Path(path)
    return p.read_text(encoding="utf-8", errors="replace") if p.is_file() else ""


def _relative(file: str, root: Path) -> str:
    """저장소 기준 상대 경로. 운영체제별 구분자와 선행 '/' 차이를 무시하고 비교한다."""
    norm = file.replace("\\", "/").lstrip("/")
    root_norm = root.as_posix().replace("\\", "/").strip("/") + "/"
    return norm[len(root_norm):] if norm.lower().startswith(root_norm.lower()) else norm


def parse_junit(results_dir: Path) -> dict[str, Any]:
    """JUnit XML 을 모두 읽어 건수와 실패 테스트를 모은다. 파싱 실패 파일은 따로 기록한다."""
    summary: dict[str, Any] = {"files": 0, "tests": 0, "failures": 0, "errors": 0, "skipped": 0,
                               "failed_cases": [], "parse_errors": []}
    if not results_dir.is_dir():
        return summary
    for xml_file in sorted(results_dir.rglob("*.xml")):
        try:
            root = ET.parse(xml_file).getroot()
        except ET.ParseError as e:
            summary["parse_errors"].append(f"{xml_file.name}: {e}")
            continue
        suites = [root] if root.tag == "testsuite" else list(root.iter("testsuite"))
        summary["files"] += 1
        for suite in suites:
            for key in ("tests", "failures", "errors", "skipped"):
                summary[key] += int(suite.get(key, "0") or 0)
            for case in suite.iter("testcase"):
                problem = case.find("failure")
                if problem is None:
                    problem = case.find("error")
                if problem is not None:
                    text = (problem.get("message") or problem.text or "").strip().splitlines()
                    summary["failed_cases"].append({
                        "class": case.get("classname", ""),
                        "name": case.get("name", ""),
                        "message": text[0][:300] if text else "",
                    })
    return summary


def evaluate(
    *, build_exit: int, build_log: str, test_exit: int | None, junit: dict[str, Any], root: Path,
) -> dict[str, Any]:
    """빌드·테스트 결과를 하위 검사 상태, findings, 실행 오류로 정리한다."""
    findings: list[dict[str, Any]] = []
    error: dict[str, Any] | None = None
    build: dict[str, Any] = {"status": "completed", "exit_code": build_exit}
    test: dict[str, Any] = {"status": "completed", "exit_code": test_exit}

    if build_exit != 0:
        if _COMPILE_ERROR.search(build_log):
            build["result"] = "failed"
            locations = list(_KOTLIN_ERROR_LOCATION.finditer(build_log))
            for m in locations[:20]:
                findings.append(make_finding(
                    policy_id="APP-BUILD", rule_id="compile-error",
                    file=_relative(m.group("file"), root), line=int(m.group("line")),
                    tool_severity="error", policy_action="BLOCK",
                    message=m.group("msg")[:300], evidence_ref="build.log",
                ))
            if not locations:
                findings.append(make_finding(
                    policy_id="APP-BUILD", rule_id="compile-error", tool_severity="error",
                    policy_action="BLOCK", message="컴파일 단계가 실패했습니다. build.log 를 확인하세요.",
                    evidence_ref="build.log",
                ))
        else:
            build["status"] = "error"
            error = {"code": "BUILD_EXECUTION_ERROR",
                     "message": "컴파일 오류가 아닌 원인으로 빌드가 실패했습니다. build.log 를 확인하세요."}
        test = {"status": "skipped", "exit_code": None, "reason": "build_failed"}
    elif test_exit is None:
        test = {"status": "skipped", "exit_code": None, "reason": "not_run"}
    else:
        test.update({k: junit[k] for k in ("files", "tests", "failures", "errors", "skipped")})
        if junit["parse_errors"]:
            test["status"] = "error"
            error = {"code": "TEST_REPORT_PARSE_ERROR", "message": "; ".join(junit["parse_errors"])[:500]}
        elif junit["files"] == 0 or junit["tests"] == 0:
            test["status"] = "error"
            error = {"code": "TEST_REPORT_MISSING", "message": "JUnit 결과가 없거나 실행된 테스트가 0건입니다."}
        elif test_exit != 0 and not junit["failed_cases"]:
            test["status"] = "error"
            error = {"code": "TEST_EXECUTION_ERROR",
                     "message": "테스트 task 가 실패했지만 실패한 테스트를 찾지 못했습니다. test.log 를 확인하세요."}
        elif test_exit == 0 and junit["failed_cases"]:
            test["status"] = "error"
            error = {"code": "TEST_RESULT_INCONSISTENT",
                     "message": "테스트 task 는 성공했지만 XML 에 실패가 있습니다."}
        for case in junit["failed_cases"]:
            findings.append(make_finding(
                policy_id="APP-TEST", rule_id="failed-test",
                file=case["class"], line=None, tool_severity="failure", policy_action="BLOCK",
                message=f"{case['name']}: {case['message']}", evidence_ref="test-results/",
            ))

    if build["status"] == "error" or test["status"] == "error":
        execution_status = "error"
    else:
        execution_status = "completed"
    return {"execution_status": execution_status, "findings": findings, "error": error,
            "subchecks": {"build": build, "test": test}}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--build-exit", type=int, required=True)
    parser.add_argument("--build-log", required=True)
    parser.add_argument("--test-exit", type=int, default=None, help="테스트를 실행하지 않았으면 생략")
    parser.add_argument("--test-results", required=True, help="JUnit XML 디렉터리")
    parser.add_argument("--started-at", default=None)
    parser.add_argument("--tool-version", default="", help="Gradle·JDK 버전 요약")
    parser.add_argument("--out", required=True)
    args = parser.parse_args(argv)

    root = Path.cwd()
    junit = parse_junit(Path(args.test_results))
    outcome = evaluate(build_exit=args.build_exit, build_log=_read(args.build_log),
                       test_exit=args.test_exit, junit=junit, root=root)
    result = make_result(
        check_id="app-check",
        context=context_from_env(root),
        tool={"name": "gradle", "version": args.tool_version or None,
              "rule_revision": "tasks::app:assembleDebug,:app:testDebugUnitTest"},
        execution_status=outcome["execution_status"],
        started_at=args.started_at or now_iso(),
        finished_at=now_iso(),
        findings=outcome["findings"],
        error=outcome["error"],
        raw_report="build.log, test.log, test-results/",
        extra={"subchecks": outcome["subchecks"]},
    )
    path = write_result(result, Path(args.out))
    blocked = any(f["policy_action"] == "BLOCK" for f in outcome["findings"])
    print(f"app-check: status={outcome['execution_status']} build={outcome['subchecks']['build']} "
          f"test={outcome['subchecks']['test']} block_findings={blocked} ({path})")
    ok = outcome["execution_status"] == "completed" and not blocked \
        and outcome["subchecks"]["test"]["status"] == "completed"
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
