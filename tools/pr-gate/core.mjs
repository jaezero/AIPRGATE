import { createHash } from "node:crypto";

export const CHECKS = Object.freeze(["pr-info", "build-test", "quality", "secrets"]);
const decisions = new Set(["block", "warn", "report"]);
const shaPattern = /^[a-f0-9]{40}$/;
const digestPattern = /^[a-f0-9]{64}$/;

function requireValue(condition, message) {
  if (!condition) throw new Error(message);
}

export function digest(value) {
  return createHash("sha256").update(value).digest("hex");
}

export function validatePolicy(policy) {
  requireValue(policy?.schemaVersion === 1, "Unsupported policy schema");
  requireValue(JSON.stringify(Object.keys(policy.checks ?? {}).sort()) ===
    JSON.stringify([...CHECKS].sort()), "Exactly four checks are required");
  for (const name of CHECKS) {
    const check = policy.checks[name];
    requireValue(Number.isInteger(check.timeoutSeconds) && check.timeoutSeconds > 0 &&
      check.timeoutSeconds <= 3600, `Invalid timeout: ${name}`);
    requireValue(typeof check.rationale === "string" && check.rationale.trim(), `Missing rationale: ${name}`);
  }
  for (const name of ["quality", "secrets"]) {
    requireValue(["warn", "report"].includes(policy.defaults?.[name]), `Invalid default: ${name}`);
  }
  for (const [id, rule] of Object.entries(policy.rules ?? {})) {
    requireValue(CHECKS.includes(rule.check) && decisions.has(rule.decision), `Invalid rule: ${id}`);
    requireValue(typeof rule.rationale === "string" && rule.rationale.trim(), `Missing rationale: ${id}`);
    requireValue(typeof rule.pilot === "string" && rule.pilot.trim(), `Missing pilot: ${id}`);
  }
  for (const [id, check] of Object.entries({
    PR_REQUIRED: "pr-info", BUILD_DEBUG: "build-test", UNIT_TESTS: "build-test",
  })) {
    requireValue(policy.rules?.[id]?.check === check &&
      policy.rules[id].decision === "block", `Required blocking rule: ${id}`);
  }
  requireValue(Array.isArray(policy.exceptions), "Exceptions must be an array");
  const exceptionKeys = new Set();
  for (const exception of policy.exceptions) {
    const rule = policy.rules[exception.ruleId];
    requireValue(rule && ["quality", "secrets"].includes(rule.check), "Exception cannot bypass required execution");
    requireValue(typeof exception.file === "string" && exception.file.length > 0 &&
      !exception.file.includes("\\") && !exception.file.startsWith("/") &&
      !exception.file.includes(":") &&
      !exception.file.split("/").some((part) => ["", ".", ".."].includes(part)) &&
      !/[*?[\]]/.test(exception.file), "Exception requires an exact relative file");
    requireValue(typeof exception.reason === "string" && exception.reason.trim(), "Exception requires a reason");
    requireValue(typeof exception.reviewer === "string" &&
      /^[a-z\d][a-z\d-]*$/i.test(exception.reviewer), "Exception requires a reviewer login");
    requireValue(typeof exception.expiresOn === "string" &&
      /^\d{4}-\d{2}-\d{2}$/.test(exception.expiresOn) &&
      Number.isFinite(Date.parse(exception.expiresOn)) &&
      new Date(exception.expiresOn).toISOString().slice(0, 10) === exception.expiresOn,
    "Exception requires a valid expiry date");
    const key = `${exception.ruleId}:${exception.file}`;
    requireValue(!exceptionKeys.has(key), "Duplicate exception");
    exceptionKeys.add(key);
  }
  return policy;
}

export function createContext(event, policyDigest, runId, runAttempt) {
  const pr = event?.pull_request;
  requireValue(pr && shaPattern.test(pr.head?.sha) && shaPattern.test(pr.base?.sha), "Invalid PR commit context");
  requireValue(typeof event.repository?.full_name === "string" &&
    /^[\w.-]+\/[\w.-]+$/.test(event.repository.full_name), "Invalid repository");
  requireValue(Number.isSafeInteger(event.number) && event.number > 0, "Invalid PR number");
  requireValue(typeof pr.user?.login === "string" && pr.user.login.length > 0, "Missing PR author");
  requireValue(typeof pr.title === "string" && typeof (pr.body ?? "") === "string", "Invalid PR metadata");
  requireValue(digestPattern.test(policyDigest), "Invalid policy digest");
  requireValue(typeof runId === "string" && /^\d+$/.test(runId) &&
    typeof runAttempt === "string" && /^[1-9]\d*$/.test(runAttempt), "Invalid workflow run identity");
  return {
    repository: event.repository.full_name,
    prNumber: event.number,
    headSha: pr.head.sha,
    baseSha: pr.base.sha,
    metadataDigest: digest(JSON.stringify({
      title: pr.title, body: pr.body ?? "", author: pr.user.login,
      baseRef: pr.base.ref, headRef: pr.head.ref,
    })),
    policyDigest,
    runId,
    runAttempt,
  };
}

export function checkPrInfo(body) {
  const markers = [...body.matchAll(/^<!-- aiprgate:([a-z-]+) -->\s*$/gm)];
  const fields = {};
  for (let index = 0; index < markers.length; index++) {
    const marker = markers[index];
    requireValue(!Object.hasOwn(fields, marker[1]), "Duplicate PR metadata marker");
    const end = markers[index + 1]?.index ?? body.length;
    // Only the first paragraph after a marker is machine-consumed.
    fields[marker[1]] = body.slice(marker.index + marker[0].length, end)
      .trim().split(/\r?\n\s*\r?\n/)[0].trim();
  }
  const filled = (value) => typeof value === "string" && value.length > 0 &&
    !/\bTODO\b/i.test(value) && !/^(?:#{1,6}\s|<!--)/.test(value);
  const missing = ["purpose", "self-review", "test-evidence", "ai-used"].filter(
    (key) => !filled(fields[key]),
  );
  if (!["yes", "no"].includes(fields["ai-used"])) missing.push("ai-used");
  if (fields["ai-used"] === "yes" &&
      !filled(fields["ai-evidence"])) {
    missing.push("ai-evidence");
  }
  return [...new Set(missing)].map((field) => ({
    ruleId: "PR_REQUIRED", file: "PR_BODY", line: 1, detail: `missing-or-invalid:${field}`,
  }));
}

export function applyPolicy(policy, check, findings, today = new Date().toISOString().slice(0, 10)) {
  return findings.map((finding) => {
    requireValue(typeof finding.ruleId === "string" && typeof finding.file === "string" &&
      Number.isInteger(finding.line) && finding.line >= 0, "Malformed finding");
    const rule = policy.rules[finding.ruleId];
    requireValue(!rule || rule.check === check, "Finding belongs to another check");
    const decision = rule?.decision ?? policy.defaults[check];
    requireValue(decisions.has(decision), "No policy for finding");
    const exception = policy.exceptions.find((entry) => entry.ruleId === finding.ruleId &&
      entry.file === finding.file && entry.expiresOn >= today);
    return { ...finding, decision: exception ? "warn" : decision, ...(exception ? { exception } : {}) };
  });
}

export function makeReceipt(check, context, findings, elapsedMs, error = null) {
  return {
    schemaVersion: 1, check, ...context,
    status: error ? "ERROR" : findings.some((finding) => finding.decision === "block") ? "BLOCK" : "PASS",
    findings, elapsedMs, error,
  };
}

export function aggregate({ policy, context, latest, receipts, jobs, today }) {
  const errors = [];
  const blocks = [];
  const warnings = [];
  for (const field of ["repository", "prNumber", "headSha", "baseSha", "metadataDigest"]) {
    if (latest?.[field] !== context[field]) errors.push(`latest-context-mismatch:${field}`);
  }
  if (!Array.isArray(receipts) || receipts.length !== CHECKS.length) errors.push("exactly-four-receipts-required");
  const seen = new Set();
  for (const receipt of Array.isArray(receipts) ? receipts : []) {
    if (!CHECKS.includes(receipt?.check) || seen.has(receipt.check)) {
      errors.push("unknown-or-duplicate-receipt");
      continue;
    }
    seen.add(receipt.check);
    try {
      requireValue(receipt.schemaVersion === 1, "unsupported-receipt-schema");
      for (const [field, value] of Object.entries(context)) {
        requireValue(receipt[field] === value, `receipt-context-mismatch:${field}`);
      }
      requireValue(["PASS", "BLOCK", "ERROR"].includes(receipt.status), "invalid-check-status");
      requireValue(Number.isFinite(receipt.elapsedMs) && receipt.elapsedMs >= 0, "invalid-duration");
      requireValue(Array.isArray(receipt.findings), "missing-findings");
      const job = jobs?.[receipt.check];
      requireValue(["success", "failure"].includes(job), "job-missing-skipped-cancelled-or-timed-out");
      if (receipt.status === "ERROR") {
        errors.push(`${receipt.check}:execution-error`);
        continue;
      }
      requireValue(receipt.error === null, "unexpected-error-field");
      const verified = applyPolicy(policy, receipt.check, receipt.findings, today);
      const expected = verified.some((finding) => finding.decision === "block") ? "BLOCK" : "PASS";
      requireValue(receipt.status === expected, "receipt-decision-mismatch");
      requireValue(job === (expected === "PASS" ? "success" : "failure"), "job-result-mismatch");
      blocks.push(...verified.filter((finding) => finding.decision === "block"));
      warnings.push(...verified.filter((finding) => finding.decision === "warn"));
    } catch (error) {
      errors.push(`${receipt.check}:${error.message}`);
    }
  }
  for (const check of CHECKS) if (!seen.has(check)) errors.push(`${check}:missing-result`);
  return {
    schemaVersion: 1, ...context,
    decision: errors.length ? "ERROR" : blocks.length ? "BLOCK" : "PASS",
    readyForHumanReview: errors.length === 0 && blocks.length === 0,
    errors, blocks, warnings,
    // Human approval and branch protection are deliberately separate from CI.
    humanApprovalVerified: false,
  };
}
