import { describe, test, expect } from "bun:test";
import { readFileSync } from "node:fs";
import {
  CHECKS, digest, validatePolicy, createContext, checkPrInfo, applyPolicy, makeReceipt, aggregate,
} from "./core.mjs";

const basePolicy = JSON.parse(readFileSync(new URL("../../policy/checks.json", import.meta.url), "utf8"));
const policy = () => structuredClone(basePolicy);
const fields = {
  purpose: "입력 검증 변경", "self-review": "공백 처리 경계를 확인함",
  "test-evidence": "단위 테스트 실행 로그: docs/results/example.txt",
  "ai-used": "yes", "ai-evidence": "docs/ai-usage-20261025.md",
};
const body = (overrides = {}) => Object.entries({ ...fields, ...overrides })
  .map(([key, value]) => `<!-- aiprgate:${key} -->\n${value}\n`).join("\n") +
  "\n<!-- aiprgate:end -->\n";
const event = () => ({
  repository: { full_name: "example/ai-pr-gate" }, number: 1,
  pull_request: {
    title: "검증용 합성 PR", body: body(), user: { login: "example-author" },
    head: { sha: "a".repeat(40), ref: "feature/example" },
    base: { sha: "b".repeat(40), ref: "main" },
  },
});
const context = () => createContext(event(), digest("test-policy"), "100", "1");
const receipts = (ctx = context()) => CHECKS.map((check) => makeReceipt(check, ctx, [], 1));
const jobs = () => Object.fromEntries(CHECKS.map((check) => [check, "success"]));
const evaluate = (overrides = {}) => aggregate({
  policy: policy(), context: context(), latest: context(), receipts: receipts(), jobs: jobs(),
  today: "2026-10-25", ...overrides,
});

describe("PR metadata", () => {
  test("AI use with evidence is accepted", () => expect(checkPrInfo(body())).toEqual([]));
  test("no AI use does not require AI evidence", () =>
    expect(checkPrInfo(body({ "ai-used": "no", "ai-evidence": "" }))).toEqual([]));
  for (const field of ["purpose", "self-review", "test-evidence", "ai-used", "ai-evidence"]) {
    test(`missing ${field} blocks`, () => {
      expect(checkPrInfo(body({ [field]: "" })).some((finding) => finding.ruleId === "PR_REQUIRED")).toBe(true);
    });
  }
  test("template TODO is not evidence", () =>
    expect(checkPrInfo(body({ "test-evidence": "TODO" }))).not.toEqual([]));
  test("next heading cannot fill an empty field", () =>
    expect(checkPrInfo(body({ purpose: "\n## 다음 항목" }))).not.toEqual([]));
  test("AI flag must be yes or no", () =>
    expect(checkPrInfo(body({ "ai-used": "maybe" }))).not.toEqual([]));
  test("duplicate machine marker is an error", () =>
    expect(() => checkPrInfo(body() + "\n<!-- aiprgate:purpose -->\nextra")).toThrow());
});

describe("policy and scoped exceptions", () => {
  test("shipped policy validates", () => expect(validatePolicy(policy())).toEqual(basePolicy));
  for (const mutate of [
    (p) => delete p.checks.secrets,
    (p) => { p.checks.quality.timeoutSeconds = 0; },
    (p) => { p.rules.PR_REQUIRED.decision = "warn"; },
    (p) => { p.rules.BUILD_DEBUG.rationale = ""; },
  ]) {
    test("invalid required policy fails closed", () => {
      const p = policy();
      mutate(p);
      expect(() => validatePolicy(p)).toThrow();
    });
  }
  const finding = { ruleId: "lint:TrustAllX509TrustManager", file: "app/src/Example.kt", line: 10 };
  const exception = {
    ruleId: finding.ruleId, file: finding.file, reason: "isolated test fixture",
    reviewer: "example-reviewer", expiresOn: "2026-10-25",
  };
  test("unexpired exact file exception warns and records reviewer", () => {
    const p = policy();
    p.exceptions = [exception];
    validatePolicy(p);
    expect(applyPolicy(p, "quality", [finding], "2026-10-25")[0]).toMatchObject({
      decision: "warn", exception: { reviewer: "example-reviewer" },
    });
    expect(applyPolicy(p, "quality", [finding], "2026-10-26")[0].decision).toBe("block");
    expect(applyPolicy(p, "quality", [{ ...finding, file: "app/src/Other.kt" }], "2026-10-25")[0].decision).toBe("block");
  });
  for (const changes of [
    { reviewer: "" }, { reason: "" }, { file: "**/*.kt" }, { file: "../Example.kt" },
    { expiresOn: "2026-02-30" }, { ruleId: "UNIT_TESTS" },
  ]) {
    test("incomplete or broad exception is rejected", () => {
      const p = policy();
      p.exceptions = [{ ...exception, ...changes }];
      expect(() => validatePolicy(p)).toThrow();
    });
  }
});

describe("four-result fail-closed gate", () => {
  test("all checks pass, but human approval is not fabricated", () => {
    expect(evaluate()).toMatchObject({ decision: "PASS", readyForHumanReview: true, humanApprovalVerified: false });
  });
  test("validated violation blocks even though the tool can exit zero", () => {
    const results = receipts();
    const findings = applyPolicy(policy(), "quality", [
      { ruleId: "lint:TrustAllX509TrustManager", file: "app/src/Example.kt", line: 10 },
    ]);
    results[2] = makeReceipt("quality", context(), findings, 3);
    expect(evaluate({ receipts: results, jobs: { ...jobs(), quality: "failure" } }).decision).toBe("BLOCK");
  });
  test("unknown Lint issue is warning, not an invented blocking rule", () => {
    const results = receipts();
    results[2] = makeReceipt("quality", context(), applyPolicy(policy(), "quality", [
      { ruleId: "lint:UnusedResources", file: "app/src/colors.xml", line: 2 },
    ]), 3);
    expect(evaluate({ receipts: results })).toMatchObject({ decision: "PASS", warnings: [{ decision: "warn" }] });
  });
  for (const value of [[], receipts().slice(0, 3), [...receipts(), receipts()[0]], null, {}]) {
    test("missing extra or invalid result collection fails", () =>
      expect(evaluate({ receipts: value }).decision).toBe("ERROR"));
  }
  test("duplicate result cannot replace a missing check", () => {
    const results = receipts();
    results[3] = results[0];
    expect(evaluate({ receipts: results }).decision).toBe("ERROR");
  });
  for (const state of ["failure", "skipped", "cancelled", "timed_out", undefined]) {
    test(`job ${state} cannot look successful`, () =>
      expect(evaluate({ jobs: { ...jobs(), quality: state } }).decision).toBe("ERROR"));
  }
  for (const field of ["headSha", "baseSha", "metadataDigest", "policyDigest", "runId", "runAttempt"]) {
    test(`old or mismatched ${field} cannot be reused`, () => {
      const results = receipts();
      results[0][field] = "stale";
      expect(evaluate({ receipts: results }).decision).toBe("ERROR");
    });
  }
  for (const field of ["headSha", "baseSha", "metadataDigest"]) {
    test(`new live PR ${field} invalidates the run`, () =>
      expect(evaluate({ latest: { ...context(), [field]: "changed" } }).decision).toBe("ERROR"));
  }
  test("body edit changes metadata digest even with the same commit", () => {
    const edited = event();
    edited.pull_request.body += "\nchanged";
    expect(createContext(edited, digest("test-policy"), "100", "1").metadataDigest)
      .not.toBe(context().metadataDigest);
  });
  test("artifact supplied warning cannot downgrade a block", () => {
    const results = receipts();
    results[2].findings = [{
      ruleId: "lint:TrustAllX509TrustManager", file: "app/src/Example.kt", line: 10, decision: "warn",
    }];
    expect(evaluate({ receipts: results }).decision).toBe("ERROR");
  });
  test("tool execution error is different from a policy violation", () => {
    const results = receipts();
    results[3] = makeReceipt("secrets", context(), [], 100, "COMMAND_TIMEOUT");
    expect(evaluate({ receipts: results, jobs: { ...jobs(), secrets: "failure" } }).decision).toBe("ERROR");
  });
  test("malformed receipt does not pass", () => {
    const results = receipts();
    results[0].elapsedMs = -1;
    expect(evaluate({ receipts: results }).decision).toBe("ERROR");
  });
});
