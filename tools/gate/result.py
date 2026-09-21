"""검사 결과 정규화 JSON 공통 모듈 (SRS 5.2 데이터 계약).

각 검사 job 은 이 모듈로 결과 JSON 을 만든다. 표준 라이브러리만 사용한다.
리포트 내용은 데이터로만 다루며 실행하지 않는다 (SRS 5.2, NFR-02).
"""

from __future__ import annotations

import argparse
import datetime as _dt
import hashlib
import json
import os
import subprocess
import sys
from pathlib import Path
from typing import Any

SCHEMA_VERSION = 1

# quality-gate 가 반드시 받아야 하는 검사 ID (CI-02)
REQUIRED_CHECKS = ("pr-info", "app-check", "quality-check", "secret-check")

EXECUTION_STATUSES = ("completed", "error", "cancelled", "skipped")
POLICY_ACTIONS = ("BLOCK", "WARN", "REPORT")

# 판정 체계 자체를 이루는 파일. 이 파일들의 해시를 policy_revision 으로 기록한다 (SRS 5.3).
REVISION_FILES = (".github/workflows/pr-check.yml",)
REVISION_DIRS = ("tools/gate", "config")

REQUIRED_FIELDS = (
    "schema_version", "repository", "pr_number", "head_sha", "base_sha", "tested_sha",
    "run_id", "run_attempt", "check_id", "policy_revision", "tool", "execution_status",
    "started_at", "finished_at", "findings", "error", "raw_report",
)
FINDING_FIELDS = (
    "policy_id", "rule_id", "file", "line", "tool_severity", "policy_action", "message", "evidence_ref",
)


def now_iso() -> str:
    return _dt.datetime.now(_dt.timezone.utc).replace(microsecond=0).isoformat()


def policy_revision(root: Path) -> str:
    """판정 체계 파일의 내용 해시. 테스트 코드와 캐시는 제외한다."""
    files: list[Path] = []
    for rel in REVISION_FILES:
        p = root / rel
        if p.is_file():
            files.append(p)
    for rel in REVISION_DIRS:
        d = root / rel
        if d.is_dir():
            for p in sorted(d.rglob("*")):
                parts = set(p.relative_to(root).parts)
                if p.is_file() and "tests" not in parts and "__pycache__" not in parts:
                    files.append(p)
    digest = hashlib.sha256()
    for p in sorted(files, key=lambda x: x.relative_to(root).as_posix()):
        digest.update(p.relative_to(root).as_posix().encode("utf-8"))
        digest.update(b"\0")
        # 줄바꿈 차이로 로컬과 CI 의 해시가 달라지지 않도록 LF 로 정규화한다.
        digest.update(p.read_bytes().replace(b"\r\n", b"\n"))
        digest.update(b"\0")
    return "sha256:" + digest.hexdigest()


def git_head(root: Path) -> str:
    try:
        out = subprocess.run(
            ["git", "rev-parse", "HEAD"], cwd=root, check=True, capture_output=True, text=True
        )
        return out.stdout.strip()
    except (OSError, subprocess.CalledProcessError):
        return ""


def context_from_env(root: Path | None = None) -> dict[str, Any]:
    """workflow 가 환경 변수로 넘긴 실행 식별값을 읽는다. PR 본문 같은 자유 텍스트는 읽지 않는다."""
    root = root or Path.cwd()
    pr = os.environ.get("PR_NUMBER", "")
    attempt = os.environ.get("GITHUB_RUN_ATTEMPT", "")
    return {
        "repository": os.environ.get("GITHUB_REPOSITORY", ""),
        "pr_number": int(pr) if pr.isdigit() else None,
        "head_sha": os.environ.get("HEAD_SHA", ""),
        "base_sha": os.environ.get("BASE_SHA", ""),
        "tested_sha": os.environ.get("TESTED_SHA") or git_head(root),
        "run_id": os.environ.get("GITHUB_RUN_ID", ""),
        "run_attempt": int(attempt) if attempt.isdigit() else None,
        "policy_revision": policy_revision(root),
    }


def make_finding(
    *, policy_id: str, rule_id: str, message: str, policy_action: str,
    file: str | None = None, line: int | None = None, tool_severity: str | None = None,
    evidence_ref: str | None = None,
) -> dict[str, Any]:
    if policy_action not in POLICY_ACTIONS:
        raise ValueError(f"unknown policy_action: {policy_action}")
    return {
        "policy_id": policy_id,
        "rule_id": rule_id,
        "file": file,
        "line": line,
        "tool_severity": tool_severity,
        "policy_action": policy_action,
        "message": message,
        "evidence_ref": evidence_ref,
    }


def make_result(
    *, check_id: str, context: dict[str, Any], tool: dict[str, Any], execution_status: str,
    started_at: str, finished_at: str, findings: list[dict[str, Any]] | None = None,
    error: dict[str, Any] | None = None, raw_report: str | None = None,
    extra: dict[str, Any] | None = None,
) -> dict[str, Any]:
    if execution_status not in EXECUTION_STATUSES:
        raise ValueError(f"unknown execution_status: {execution_status}")
    result: dict[str, Any] = {"schema_version": SCHEMA_VERSION}
    result.update({k: context.get(k) for k in (
        "repository", "pr_number", "head_sha", "base_sha", "tested_sha", "run_id", "run_attempt",
    )})
    result.update({
        "check_id": check_id,
        "policy_revision": context.get("policy_revision"),
        "tool": tool,
        "execution_status": execution_status,
        "started_at": started_at,
        "finished_at": finished_at,
        "findings": findings or [],
        "error": error,
        "raw_report": raw_report,
    })
    if extra:
        result.update(extra)
    return result


def write_result(result: dict[str, Any], out_dir: Path) -> Path:
    out_dir.mkdir(parents=True, exist_ok=True)
    path = out_dir / f"{result['check_id']}.json"
    path.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    return path


def _cmd_placeholder(args: argparse.Namespace) -> int:
    """아직 연결하지 않은 검사의 결과. skipped 로 기록하며 성공으로 표시하지 않는다."""
    ctx = context_from_env()
    ts = now_iso()
    result = make_result(
        check_id=args.check_id,
        context=ctx,
        tool={"name": "not-connected", "version": None, "rule_revision": None},
        execution_status="skipped",
        started_at=ts,
        finished_at=ts,
        error={"code": "NOT_CONNECTED", "message": args.reason},
    )
    path = write_result(result, Path(args.out))
    print(f"{args.check_id}: NOT_CONNECTED — {args.reason} ({path})")
    return 1


def _cmd_revision(args: argparse.Namespace) -> int:
    print(policy_revision(Path(args.root)))
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    p = sub.add_parser("placeholder", help="미연결 검사 결과를 skipped 로 기록하고 실패 종료")
    p.add_argument("--check-id", required=True, choices=REQUIRED_CHECKS)
    p.add_argument("--reason", required=True)
    p.add_argument("--out", required=True)
    p.set_defaults(func=_cmd_placeholder)
    r = sub.add_parser("revision", help="policy_revision 출력")
    r.add_argument("--root", default=".")
    r.set_defaults(func=_cmd_revision)
    args = parser.parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
