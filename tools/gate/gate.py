"""quality-gate 집계 (GAT-01~04, CI-04·06·07).

같은 run 의 결과 JSON 을 모두 검사해 PASS / POLICY_FAIL / CHECK_ERROR 를 판정한다.
- 필수 검사가 모두 정상 완료되고 BLOCK 이 없을 때만 PASS.
- BLOCK finding 은 정책 위반(POLICY_FAIL), 누락·중복·스키마 오류·식별값 불일치·skipped·cancelled·error 는
  검사 오류(CHECK_ERROR). 둘 다 있으면 두 사유를 모두 출력한다 (GAT-02).
- WARN·REPORT 는 표시만 하고 차단하지 않는다 (GAT-04).
- 최신 PR head/base/본문 재조회(GAT-05)는 Step 5 범위이며 아직 구현하지 않았다.
"""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

sys.path.insert(0, str(Path(__file__).resolve().parent))

from result import (  # noqa: E402
    EXECUTION_STATUSES, FINDING_FIELDS, POLICY_ACTIONS, REQUIRED_CHECKS, REQUIRED_FIELDS, SCHEMA_VERSION,
)

PASS = "PASS"
POLICY_FAIL = "POLICY_FAIL"
CHECK_ERROR = "CHECK_ERROR"


@dataclass
class Expected:
    run_id: str
    run_attempt: int
    head_sha: str
    base_sha: str
    policy_revision: str
    repository: str = ""
    pr_number: int | None = None


@dataclass
class Verdict:
    verdict: str = PASS
    policy_violations: list[dict[str, Any]] = field(default_factory=list)
    check_errors: list[dict[str, Any]] = field(default_factory=list)
    warnings: list[dict[str, Any]] = field(default_factory=list)
    checks: dict[str, str] = field(default_factory=dict)
    tested_sha: str | None = None

    def to_dict(self) -> dict[str, Any]:
        return {
            "verdict": self.verdict,
            "checks": self.checks,
            "tested_sha": self.tested_sha,
            "policy_violations": self.policy_violations,
            "check_errors": self.check_errors,
            "warnings": self.warnings,
        }


def _err(v: Verdict, check_id: str, code: str, message: str) -> None:
    v.check_errors.append({"check_id": check_id, "code": code, "message": message})


def validate_schema(data: Any) -> list[str]:
    """결과 JSON 구조 오류 목록. 비어 있으면 통과."""
    if not isinstance(data, dict):
        return ["최상위 값이 객체가 아님"]
    problems = [f"필드 누락: {k}" for k in REQUIRED_FIELDS if k not in data]
    if problems:
        return problems
    if data["schema_version"] != SCHEMA_VERSION:
        problems.append(f"schema_version={data['schema_version']} (기대 {SCHEMA_VERSION})")
    if data["execution_status"] not in EXECUTION_STATUSES:
        problems.append(f"execution_status 값 오류: {data['execution_status']}")
    if not isinstance(data["findings"], list):
        problems.append("findings 가 배열이 아님")
    else:
        for i, f in enumerate(data["findings"]):
            if not isinstance(f, dict):
                problems.append(f"findings[{i}] 가 객체가 아님")
                continue
            missing = [k for k in FINDING_FIELDS if k not in f]
            if missing:
                problems.append(f"findings[{i}] 필드 누락: {', '.join(missing)}")
            elif f["policy_action"] not in POLICY_ACTIONS:
                problems.append(f"findings[{i}] policy_action 값 오류: {f['policy_action']}")
    if not isinstance(data["tool"], dict):
        problems.append("tool 이 객체가 아님")
    return problems


def load_results(results_dir: Path, v: Verdict) -> dict[str, list[dict[str, Any]]]:
    """디렉터리 아래 모든 *.json 을 읽는다. 읽거나 파싱할 수 없는 파일은 검사 오류다."""
    by_check: dict[str, list[dict[str, Any]]] = {}
    if not results_dir.is_dir():
        return by_check
    for path in sorted(results_dir.rglob("*.json")):
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, UnicodeDecodeError, json.JSONDecodeError) as e:
            _err(v, path.stem, "PARSE_ERROR", f"{path.name}: {e}")
            continue
        problems = validate_schema(data)
        if problems:
            check_id = data.get("check_id", path.stem) if isinstance(data, dict) else path.stem
            _err(v, str(check_id), "SCHEMA_ERROR", f"{path.name}: " + "; ".join(problems))
            continue
        by_check.setdefault(data["check_id"], []).append(data)
    return by_check


def evaluate(
    results_dir: Path, expected: Expected, needs: dict[str, Any] | None = None,
    required: tuple[str, ...] = REQUIRED_CHECKS,
) -> Verdict:
    v = Verdict()
    by_check = load_results(results_dir, v)
    tested_shas: set[str] = set()

    for check_id in by_check:
        if check_id not in required:
            v.warnings.append({"check_id": check_id, "code": "UNKNOWN_CHECK",
                               "message": "필수 목록에 없는 검사 결과. 판정에 사용하지 않음"})

    for check_id in required:
        results = by_check.get(check_id, [])
        if not results:
            _err(v, check_id, "MISSING", "결과가 없습니다 (job 미실행·실패·artifact 누락).")
            v.checks[check_id] = "missing"
            continue
        if len(results) > 1:
            _err(v, check_id, "DUPLICATE", f"결과가 {len(results)}개입니다.")
            v.checks[check_id] = "duplicate"
            continue
        r = results[0]
        status = r["execution_status"]
        v.checks[check_id] = status

        mismatches = []
        if str(r["run_id"]) != str(expected.run_id):
            mismatches.append(f"run_id {r['run_id']} != {expected.run_id}")
        if r["run_attempt"] != expected.run_attempt:
            mismatches.append(f"run_attempt {r['run_attempt']} != {expected.run_attempt}")
        if r["head_sha"] != expected.head_sha:
            mismatches.append(f"head_sha {r['head_sha']} != {expected.head_sha}")
        if r["base_sha"] != expected.base_sha:
            mismatches.append(f"base_sha {r['base_sha']} != {expected.base_sha}")
        if r["policy_revision"] != expected.policy_revision:
            mismatches.append("policy_revision 불일치")
        if expected.repository and r["repository"] != expected.repository:
            mismatches.append(f"repository {r['repository']} != {expected.repository}")
        if expected.pr_number is not None and r["pr_number"] != expected.pr_number:
            mismatches.append(f"pr_number {r['pr_number']} != {expected.pr_number}")
        if not r["tested_sha"]:
            mismatches.append("tested_sha 없음")
        if mismatches:
            _err(v, check_id, "IDENTITY_MISMATCH", "; ".join(mismatches))
            continue
        tested_shas.add(r["tested_sha"])

        if status != "completed":
            code = (r.get("error") or {}).get("code") or status.upper()
            msg = (r.get("error") or {}).get("message") or f"execution_status={status}"
            _err(v, check_id, code, msg)
        for name, sub in (r.get("subchecks") or {}).items():
            if isinstance(sub, dict) and sub.get("status") != "completed":
                reason = sub.get("reason") or sub.get("status")
                _err(v, check_id, f"SUBCHECK_{str(sub.get('status')).upper()}",
                     f"{name} 하위 검사 미완료: {reason}")

        for f in r["findings"]:
            item = {"check_id": check_id, **f}
            if f["policy_action"] == "BLOCK":
                v.policy_violations.append(item)
            else:
                v.warnings.append(item)

    if len(tested_shas) > 1:
        _err(v, "*", "TESTED_SHA_MISMATCH", f"검사마다 다른 코드를 검사했습니다: {sorted(tested_shas)}")
    v.tested_sha = next(iter(tested_shas)) if len(tested_shas) == 1 else None

    # job 자체가 취소·생략되었으면 artifact 가 남아 있어도 통과시키지 않는다.
    for job, info in (needs or {}).items():
        res = info.get("result") if isinstance(info, dict) else None
        if res in ("cancelled", "skipped"):
            _err(v, job, f"JOB_{res.upper()}", f"job 결과가 {res} 입니다.")

    if v.check_errors:
        v.verdict = CHECK_ERROR
    elif v.policy_violations:
        v.verdict = POLICY_FAIL
    else:
        v.verdict = PASS
    return v


def _md_escape(text: Any) -> str:
    return str(text if text is not None else "").replace("|", "\\|").replace("\n", " ")


def render_summary(v: Verdict, expected: Expected) -> str:
    icon = {"PASS": "✅", "POLICY_FAIL": "❌", "CHECK_ERROR": "⚠️"}[v.verdict]
    lines = [
        f"## quality-gate: {icon} {v.verdict}",
        "",
        f"- PR head `{expected.head_sha}` / base `{expected.base_sha}` / tested `{v.tested_sha}`",
        f"- run `{expected.run_id}` attempt `{expected.run_attempt}` / policy `{expected.policy_revision[:19]}…`",
        "",
        "| 검사 | 실행 상태 |",
        "|---|---|",
    ]
    lines += [f"| {c} | {s} |" for c, s in v.checks.items()]
    if v.policy_violations:
        lines += ["", "### 정책 위반 (BLOCK)", "", "| 검사 | 정책 | 룰 | 위치 | 내용 |", "|---|---|---|---|---|"]
        for f in v.policy_violations:
            loc = f"{f.get('file') or ''}{':' + str(f['line']) if f.get('line') else ''}"
            lines.append(f"| {f['check_id']} | {f['policy_id']} | {f['rule_id']} | {_md_escape(loc)} | "
                         f"{_md_escape(f['message'])} |")
    if v.check_errors:
        lines += ["", "### 검사 오류 (코드 위반과 별개, 통과 금지)", "", "| 검사 | 코드 | 내용 |", "|---|---|---|"]
        for e in v.check_errors:
            lines.append(f"| {e['check_id']} | {e['code']} | {_md_escape(e['message'])} |")
    if v.warnings:
        lines += ["", "### 경고·보고 (차단하지 않음)", "", "| 검사 | 코드/정책 | 내용 |", "|---|---|---|"]
        for w in v.warnings:
            lines.append(f"| {w['check_id']} | {w.get('policy_id') or w.get('code')} | {_md_escape(w.get('message'))} |")
    lines += ["", "재검사: 코드를 수정해 새 커밋을 push 하거나, 코드 변경 없이 실패한 run 을 다시 실행합니다.",
              "사람 승인은 이 판정과 별개로 저장소 보호 규칙이 요구합니다."]
    return "\n".join(lines) + "\n"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--results", required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--run-attempt", type=int, required=True)
    parser.add_argument("--head-sha", required=True)
    parser.add_argument("--base-sha", required=True)
    parser.add_argument("--policy-revision", required=True)
    parser.add_argument("--repository", default="")
    parser.add_argument("--pr-number", type=int, default=None)
    parser.add_argument("--needs-json", default="", help="workflow 의 toJSON(needs)")
    parser.add_argument("--summary", default="", help="Markdown 요약 출력 경로 (GITHUB_STEP_SUMMARY)")
    parser.add_argument("--out", default="", help="판정 JSON 출력 경로")
    args = parser.parse_args(argv)

    expected = Expected(run_id=args.run_id, run_attempt=args.run_attempt, head_sha=args.head_sha,
                        base_sha=args.base_sha, policy_revision=args.policy_revision,
                        repository=args.repository, pr_number=args.pr_number)
    needs: dict[str, Any] = {}
    if args.needs_json:
        try:
            needs = json.loads(args.needs_json)
        except json.JSONDecodeError as e:
            needs = {}
            print(f"needs JSON 파싱 실패: {e}", file=sys.stderr)
    v = evaluate(Path(args.results), expected, needs)
    if args.needs_json and not needs:
        _err(v, "*", "NEEDS_PARSE_ERROR", "job 결과 목록을 읽지 못했습니다.")
        v.verdict = CHECK_ERROR

    summary = render_summary(v, expected)
    print(summary)
    if args.summary:
        with open(args.summary, "a", encoding="utf-8") as fh:
            fh.write(summary)
    if args.out:
        Path(args.out).write_text(json.dumps(v.to_dict(), ensure_ascii=False, indent=2), encoding="utf-8")
    return 0 if v.verdict == PASS else 1


if __name__ == "__main__":
    sys.exit(main())
