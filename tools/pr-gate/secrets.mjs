import { execFile } from "node:child_process";
import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { isAbsolute, join, relative, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";

const defaultConfig = fileURLToPath(new URL("../../policy/gitleaks.toml", import.meta.url));
const defaultIgnore = fileURLToPath(new URL("../../policy/gitleaks.ignore", import.meta.url));

function execute(executable, args, cwd, timeoutMs) {
  // Do not forward diagnostics: even tool failures can echo source contents.
  const env = Object.fromEntries(
    Object.entries(process.env).filter(([key]) => !key.startsWith("GITLEAKS_")),
  );
  return new Promise((resolveRun, reject) => {
    execFile(executable, args, {
      cwd, env, timeout: timeoutMs, killSignal: "SIGKILL", maxBuffer: 1024 * 1024,
    }, (error, stdout) => {
      if (error?.killed || error?.signal) {
        reject(new Error("Gitleaks execution timed out or was terminated"));
      } else if (error && !Number.isInteger(error.code)) {
        reject(new Error("Gitleaks execution failed"));
      } else {
        resolveRun({ exitCode: error ? error.code : 0, stdout });
      }
    });
  });
}

// config and ignorePath must come from the trusted gate checkout, not PR contents.
export async function scanSecrets({
  root,
  executable,
  config = defaultConfig,
  ignorePath = defaultIgnore,
  timeoutMs = 60_000,
}) {
  if (!Number.isSafeInteger(timeoutMs) || timeoutMs <= 0) {
    throw new Error("Gitleaks timeoutMs must be a positive integer");
  }
  const started = performance.now();
  const scanRoot = resolve(root);
  const trustedConfig = resolve(config);
  const trustedIgnore = resolve(ignorePath);
  if ((await readFile(trustedIgnore, "utf8")).trim() !== "") {
    throw new Error("Gitleaks trusted ignore file must be empty");
  }
  // Copy trusted inputs into the private run directory; never discover PR config/ignores.
  const configBytes = await readFile(trustedConfig);
  const temp = await mkdtemp(join(tmpdir(), "aiprgate-secrets-"));
  try {
    await writeFile(join(temp, "config.toml"), configBytes, { mode: 0o600 });
    await writeFile(join(temp, "empty.ignore"), "", { mode: 0o600 });
    const version = await execute(executable, ["version"], temp, timeoutMs);
    const toolVersion = version.stdout.trim();
    if (version.exitCode !== 0 || !/^\d+\.\d+\.\d+$/.test(toolVersion)) {
      throw new Error("Gitleaks version query failed");
    }
    const reportPath = join(temp, "report.json");
    const run = await execute(executable, [
      "dir", scanRoot,
      "--config", join(temp, "config.toml"),
      "--gitleaks-ignore-path", join(temp, "empty.ignore"),
      "--ignore-gitleaks-allow",
      "--redact=100",
      "--report-format", "json",
      "--report-path", reportPath,
      "--exit-code", "1",
      "--no-banner", "--no-color", "--log-level", "error",
    ], temp, timeoutMs);
    if (run.exitCode !== 0 && run.exitCode !== 1) {
      throw new Error("Gitleaks scan execution failed");
    }
    let report;
    try {
      report = JSON.parse(await readFile(reportPath, "utf8"));
    } catch {
      throw new Error("Gitleaks report missing or malformed");
    }
    if (!Array.isArray(report)) throw new Error("Gitleaks report malformed");
    const findings = report.map((item) => {
      if (!item || typeof item.RuleID !== "string" || !/^[a-z0-9][a-z0-9-]*$/.test(item.RuleID)
        || typeof item.File !== "string" || !item.File || /[\x00-\x1f\x7f]/.test(item.File)
        || !Number.isSafeInteger(item.StartLine) || item.StartLine <= 0) {
        throw new Error("Gitleaks report malformed");
      }
      const file = relative(scanRoot, isAbsolute(item.File) ? item.File : resolve(scanRoot, item.File));
      if (!file || file === ".." || file.startsWith(`..${sep}`) || isAbsolute(file)) {
        throw new Error("Gitleaks report contains an out-of-scope path");
      }
      return { ruleId: item.RuleID, file: file.split(sep).join("/"), line: item.StartLine };
    });
    if ((run.exitCode === 1) !== (findings.length > 0)) {
      throw new Error("Gitleaks exit code and report disagree");
    }
    findings.sort((a, b) => a.file.localeCompare(b.file) || a.line - b.line || a.ruleId.localeCompare(b.ruleId));
    return { exitCode: run.exitCode, findings, elapsedMs: performance.now() - started, toolVersion };
  } finally {
    await rm(temp, { recursive: true, force: true });
  }
}
