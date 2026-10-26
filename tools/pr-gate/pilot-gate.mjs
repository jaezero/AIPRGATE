import { readFile, mkdir, writeFile } from "node:fs/promises";
import { resolve, join } from "node:path";
import { parseArgs } from "node:util";
import { fileURLToPath } from "node:url";
import {
  CHECKS, validatePolicy, createContext, digest, applyPolicy, aggregate,
} from "./core.mjs";
import { parseLint } from "./reports.mjs";

const { values } = parseArgs({
  options: Object.fromEntries(["baseline", "lint-pilot", "out"].map((key) => [key, { type: "string" }])),
  strict: true,
});
if (!values.baseline || !values["lint-pilot"] || !values.out) {
  throw new Error("--baseline, --lint-pilot and --out are required");
}
const root = fileURLToPath(new URL("../..", import.meta.url));
const readJson = async (path) => JSON.parse(await readFile(path, "utf8"));
const policyText = await readFile(join(root, "policy/checks.json"), "utf8");
const config = await readFile(join(root, "policy/gitleaks.toml"), "utf8");
const ignore = await readFile(join(root, "policy/gitleaks.ignore"), "utf8");
const policy = validatePolicy(JSON.parse(policyText));
const baseline = resolve(values.baseline);
const receipts = await Promise.all(CHECKS.map((check) => readJson(join(baseline, `${check}.json`))));
const event = await readJson(join(root, "tools/pr-gate/fixtures/pr-valid.json"));
const context = createContext(event, digest(JSON.stringify([policyText, config, ignore])),
  receipts[0].runId, receipts[0].runAttempt);
const initial = {
  policy, context, latest: context, receipts,
  jobs: Object.fromEntries(CHECKS.map((check) => [check, "success"])),
};
const scenarios = [];
function record(name, expected, mutate = () => {}) {
  const input = structuredClone(initial);
  mutate(input);
  const started = performance.now();
  const actual = aggregate(input);
  scenarios.push({
    name, expected, actual: actual.decision,
    matched: actual.decision === expected,
    evaluationMs: Number((performance.now() - started).toFixed(3)),
    provenance: name === "normal-real-local-receipts"
      ? "Real local check receipts; synthetic PR identity, not GitHub."
      : "Synthetic gate-input mutation, not a GitHub run or merge.",
    errors: actual.errors,
  });
}
record("normal-real-local-receipts", "PASS");
record("missing-check-result", "ERROR", (input) => { input.receipts.pop(); });
record("cancelled-job", "ERROR", (input) => { input.jobs.quality = "cancelled"; });
record("skipped-job", "ERROR", (input) => { input.jobs.quality = "skipped"; });
record("timeout-without-receipt", "ERROR", (input) => {
  input.receipts.pop(); input.jobs.secrets = "failure";
});
record("latest-commit-changed", "ERROR", (input) => { input.latest.headSha = "c".repeat(40); });
record("body-edited-same-commit", "ERROR", (input) => { input.latest.metadataDigest = digest("edited"); });
record("previous-run-result", "ERROR", (input) => { input.receipts[0].runId = "999"; });
record("previous-attempt-result", "ERROR", (input) => { input.receipts[0].runAttempt = "99"; });
record("required-metadata-missing", "BLOCK", (input) => {
  const finding = applyPolicy(policy, "pr-info", [
    { ruleId: "PR_REQUIRED", file: "PR_BODY", line: 1, detail: "missing-or-invalid:purpose" },
  ]);
  input.receipts[0] = { ...input.receipts[0], status: "BLOCK", findings: finding };
  input.jobs["pr-info"] = "failure";
});

const lintPilot = resolve(values["lint-pilot"]);
const lintCases = [];
for (const name of ["unsafe", "safe"]) {
  const evidence = await readJson(join(lintPilot, name, "run.json"));
  if (!evidence.compiledClassPresent || evidence.executionError || !evidence.expectedDetection) {
    throw new Error("Actual Lint pilot did not execute successfully");
  }
  const findings = applyPolicy(policy, "quality", parseLint(
    await readFile(join(lintPilot, name, "lint-results-debug.xml"), "utf8"),
    [evidence.workingDirectory],
  ));
  const decision = findings.some((finding) => finding.decision === "block") ? "BLOCK" : "PASS";
  const expected = name === "unsafe" ? "BLOCK" : "PASS";
  lintCases.push({
    name, expected, actual: decision, matched: decision === expected,
    toolExitCode: evidence.exitCode,
    blocks: findings.filter((finding) => finding.decision === "block"),
    warnings: findings.filter((finding) => finding.decision === "warn").length,
    provenance: "Policy classification of actual compiled fixture Lint report; not a PR gate run.",
  });
}
const out = resolve(values.out);
await mkdir(out, { recursive: true });
const summary = {
  recordedAt: new Date().toISOString(), context,
  actualCheckElapsedMs: Object.fromEntries(receipts.map((receipt) => [receipt.check, receipt.elapsedMs])),
  scenarios, lintCases,
  matched: [...scenarios, ...lintCases].every((entry) => entry.matched),
};
await writeFile(join(out, "scenario-results.json"), JSON.stringify(summary, null, 2) + "\n", "utf8");
console.log(`Gate scenarios: ${scenarios.filter((entry) => entry.matched).length}/${scenarios.length}`);
console.log(`Actual Lint policy cases: ${lintCases.filter((entry) => entry.matched).length}/${lintCases.length}`);
process.exitCode = summary.matched ? 0 : 1;
