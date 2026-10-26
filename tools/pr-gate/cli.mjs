import { readFile, writeFile, mkdir, readdir, rm } from "node:fs/promises";
import { resolve, join } from "node:path";
import { parseArgs } from "node:util";
import {
  CHECKS, digest, validatePolicy, createContext, checkPrInfo, applyPolicy, makeReceipt, aggregate,
} from "./core.mjs";
import { parseJUnit, parseLint } from "./reports.mjs";
import { runCommand } from "./process.mjs";
import { scanSecrets } from "./secrets.mjs";
import { installGitleaks } from "./bootstrap-gitleaks.mjs";

const command = process.argv[2];
const check = command === "check" ? process.argv[3] : null;
const { values } = parseArgs({
  args: process.argv.slice(command === "check" ? 4 : 3),
  options: Object.fromEntries([
    "root", "control", "event", "out", "run-id", "attempt", "windows-root",
    "latest-file", "needs-file", "results", "gitleaks",
  ].map((name) => [name, { type: "string" }])),
  strict: true,
});
if (process.env.GITHUB_ACTIONS === "true" &&
    (command === "local" || values["latest-file"] || values.event || values["run-id"] || values.attempt)) {
  throw new Error("CI_IDENTITY_OVERRIDE_FORBIDDEN");
}
const root = resolve(values.root ?? ".");
const control = resolve(values.control ?? ".");
const out = resolve(values.out ?? "gate-results");
const policyText = await readFile(join(control, "policy/checks.json"), "utf8");
const leakConfig = await readFile(join(control, "policy/gitleaks.toml"), "utf8");
const leakIgnore = await readFile(join(control, "policy/gitleaks.ignore"), "utf8");
const policy = validatePolicy(JSON.parse(policyText));
const event = JSON.parse(await readFile(values.event ?? process.env.GITHUB_EVENT_PATH, "utf8"));
const context = createContext(
  event, digest(JSON.stringify([policyText, leakConfig, leakIgnore])),
  values["run-id"] ?? process.env.GITHUB_RUN_ID,
  values.attempt ?? process.env.GITHUB_RUN_ATTEMPT,
);
await mkdir(out, { recursive: true });

async function save(name, value) {
  await writeFile(join(out, name), JSON.stringify(value, null, 2) + "\n", "utf8");
}

function gradleCommand(task) {
  if (values["windows-root"]) {
    const controlWindows = control.startsWith("/mnt/")
      ? control.slice(5, 6).toUpperCase() + ":\\" + control.slice(7).replaceAll("/", "\\")
      : control;
    return [
      "/mnt/c/Windows/System32/WindowsPowerShell/v1.0/powershell.exe",
      "-NoProfile", "-ExecutionPolicy", "Bypass",
      "-File", `${controlWindows}\\tools\\pr-gate\\windows-gradle.ps1`,
      "-Project", values["windows-root"], "-Task", task,
    ];
  }
  return ["bash", "./gradlew",
    ...(task === "BuildTest" ? [":app:assembleDebug", ":app:testDebugUnitTest"] : [":app:lintDebug"]),
    "--no-daemon", "--console=plain"];
}

async function execute(name) {
  const started = performance.now();
  let findings = [];
  let failure = null;
  try {
    if (name === "pr-info") {
      findings = checkPrInfo(event.pull_request.body ?? "");
    } else if (name === "build-test") {
      const reportDirectory = join(root, "app/build/test-results/testDebugUnitTest");
      // Remove only generated test reports, not sources or stored evidence.
      await rm(reportDirectory, { recursive: true, force: true });
      const result = await runCommand(gradleCommand("BuildTest"), {
        cwd: root, timeoutMs: policy.checks[name].timeoutSeconds * 1000,
        logPath: join(out, "build-test.log"),
      });
      const reports = (await readdir(reportDirectory).catch((error) => {
        if (error.code === "ENOENT") return [];
        throw error;
      })).filter((file) => file.endsWith(".xml"));
      if (result.exitCode !== 0) {
        findings.push({ ruleId: "BUILD_DEBUG", file: "Gradle", line: 0, detail: "gradle-exit-nonzero" });
      }
      if (reports.length === 0) throw new Error("JUNIT_REPORT_MISSING");
      const totals = { tests: 0, failures: 0, errors: 0, skipped: 0 };
      for (const file of reports) {
        const xml = await readFile(join(reportDirectory, file), "utf8");
        const counts = parseJUnit(xml);
        for (const key of Object.keys(totals)) totals[key] += counts[key];
        await writeFile(join(out, file), xml, "utf8");
      }
      await save("unit-counts.json", totals);
      if (totals.tests === 0) throw new Error("NO_UNIT_TESTS_EXECUTED");
      if (totals.failures || totals.errors || totals.skipped) {
        findings.push({ ruleId: "UNIT_TESTS", file: "JUnit", line: 0, detail: "failed-errored-or-skipped-tests" });
      }
    } else if (name === "quality") {
      const reportPath = join(root, "app/build/reports/lint-results-debug.xml");
      await rm(reportPath, { force: true });
      const result = await runCommand(gradleCommand("Lint"), {
        cwd: root, timeoutMs: policy.checks[name].timeoutSeconds * 1000,
        logPath: join(out, "quality.log"),
      });
      const xml = await readFile(reportPath, "utf8");
      findings = parseLint(xml, [root, values["windows-root"] ?? root]);
      await writeFile(join(out, "lint-results-debug.xml"), xml, "utf8");
      const selected = applyPolicy(policy, name, findings);
      if (result.exitCode !== 0 && !selected.some((finding) => finding.decision === "block")) {
        throw new Error("LINT_EXECUTION_FAILED");
      }
    } else if (name === "secrets") {
      const executable = values.gitleaks ?? await installGitleaks();
      const result = await scanSecrets({
        root, executable,
        config: join(control, "policy/gitleaks.toml"),
        ignorePath: join(control, "policy/gitleaks.ignore"),
        timeoutMs: policy.checks[name].timeoutSeconds * 1000,
      });
      findings = result.findings.map((finding) => ({
        ...finding,
        ruleId: `gitleaks:${finding.ruleId}`,
      }));
    } else {
      throw new Error("UNKNOWN_CHECK");
    }
    findings = applyPolicy(policy, name, findings);
  } catch (error) {
    // Never put raw tool output or possible secret material in a receipt.
    failure = error.message?.match(/^[A-Z_]+$/) ? error.message : "CHECK_EXECUTION_ERROR";
    findings = [];
  }
  const receipt = makeReceipt(name, context, findings, Math.round(performance.now() - started), failure);
  await save(`${name}.json`, receipt);
  console.log(`${name}: ${receipt.status} (${receipt.elapsedMs} ms)`);
  return receipt;
}

async function latestContext() {
  if (values["latest-file"]) {
    const latest = JSON.parse(await readFile(values["latest-file"], "utf8"));
    return createContext(latest, context.policyDigest, context.runId, context.runAttempt);
  }
  if (!process.env.GITHUB_TOKEN) throw new Error("LIVE_PR_LOOKUP_REQUIRES_TOKEN");
  const response = await fetch(
    `https://api.github.com/repos/${context.repository}/pulls/${context.prNumber}`,
    {
      headers: {
        Accept: "application/vnd.github+json",
        Authorization: `Bearer ${process.env.GITHUB_TOKEN}`,
        "X-GitHub-Api-Version": "2022-11-28",
      },
      signal: AbortSignal.timeout(30_000),
    },
  );
  if (!response.ok) throw new Error("LIVE_PR_LOOKUP_FAILED");
  const pr = await response.json();
  if (pr.state !== "open") throw new Error("PR_NOT_OPEN");
  return createContext({ repository: event.repository, number: event.number, pull_request: pr },
    context.policyDigest, context.runId, context.runAttempt);
}

async function collect() {
  const directory = resolve(values.results ?? out);
  const files = await readdir(directory, { recursive: true });
  const receipts = [];
  for (const file of files) {
    if (!CHECKS.some((name) => file === `${name}.json` || file.endsWith(`/${name}.json`))) continue;
    receipts.push(JSON.parse(await readFile(join(directory, file), "utf8")));
  }
  return receipts;
}

async function finish(receipts, jobs) {
  let result;
  try {
    result = aggregate({ policy, context, latest: await latestContext(), receipts, jobs });
  } catch {
    result = { ...context, decision: "ERROR", readyForHumanReview: false,
      humanApprovalVerified: false, errors: ["RESULT_OR_LATEST_PR_LOOKUP_FAILED"], blocks: [], warnings: [] };
  }
  await save("quality-gate.json", result);
  const summary = [
    "# AI PR quality-gate",
    `판정: **${result.decision}**`,
    `커밋: ${context.headSha}`,
    `정책 차단: ${result.blocks.length}, 경고: ${result.warnings.length}, 실행 오류: ${result.errors.length}`,
    "PASS는 자동 검사 통과만 의미합니다. 작성자 외 사람의 승인과 브랜치 보호 조건은 별도입니다.",
    ...result.errors.map((error) => `- ${error}`),
    ...result.blocks.map((finding) => `- BLOCK ${finding.ruleId}: ${finding.file}:${finding.line}`),
    ...result.warnings.map((finding) => `- WARN ${finding.ruleId}: ${finding.file}:${finding.line}`),
  ].join("\n") + "\n";
  await writeFile(join(out, "summary.md"), summary, "utf8");
  if (process.env.GITHUB_STEP_SUMMARY) {
    const { appendFile } = await import("node:fs/promises");
    await appendFile(process.env.GITHUB_STEP_SUMMARY, summary, "utf8");
  }
  console.log(`quality-gate: ${result.decision}`);
  process.exitCode = result.decision === "PASS" ? 0 : 1;
}

if (command === "check") {
  if (!CHECKS.includes(check)) throw new Error("Unknown check");
  const receipt = await execute(check);
  process.exitCode = receipt.status === "PASS" ? 0 : 1;
} else if (command === "aggregate") {
  const jobs = JSON.parse(await readFile(values["needs-file"], "utf8"));
  // Parse/download errors are not allowed to turn the gate green.
  try {
    await finish(await collect(), jobs);
  } catch {
    await finish([], jobs);
  }
} else if (command === "local") {
  if (!values["latest-file"]) throw new Error("Local runs require an explicit simulated latest event");
  const independent = Promise.all([execute("pr-info"), execute("secrets")]);
  // CI jobs have isolated runners and run all four checks concurrently.
  // Local Gradle stages share app/build and must not race each other.
  const build = await execute("build-test");
  const quality = await execute("quality");
  const receipts = [...await independent, build, quality];
  await finish(receipts, Object.fromEntries(receipts.map((receipt) =>
    [receipt.check, receipt.status === "PASS" ? "success" : "failure"])));
} else {
  throw new Error("Expected check, aggregate, or local");
}
