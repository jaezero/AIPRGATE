import { afterEach, beforeAll, describe, expect, test } from "bun:test";
import { createHash } from "node:crypto";
import { access, chmod, mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { GITLEAKS_VERSION, installGitleaks } from "./bootstrap-gitleaks.mjs";
import { scanSecrets } from "./secrets.mjs";

const roots = [];
async function fixture() {
  const root = await mkdtemp(join(tmpdir(), "aiprgate-secret-test-"));
  roots.push(root);
  return root;
}

// Invalid synthetic data, assembled only at runtime; never a real credential.
function invalidSecret() {
  return ["7vK9", "mP2x", "Q6rT", "8bW3", "nF5z", "H4jY", "6cD1", "sL0a"].join("");
}

afterEach(async () => {
  await Promise.all(roots.splice(0).map((root) => rm(root, { recursive: true, force: true })));
});

describe("pinned Gitleaks pilot (actual executable)", () => {
  let executable;
  beforeAll(async () => {
    executable = await installGitleaks();
    console.log(`PILOT binary=${executable} version=${GITLEAKS_VERSION}`);
  }, 150_000);

  test("official archive hash and path-only bootstrap CLI", async () => {
    const archive = await readFile(join(dirname(executable), "release.tar.gz"));
    expect(createHash("sha256").update(archive).digest("hex"))
      .toBe("551f6fc83ea457d62a0d98237cbad105af8d557003051f41f3e7ca7b3f2470eb");
    const cli = Bun.spawn([process.execPath, fileURLToPath(new URL("./bootstrap-gitleaks.mjs", import.meta.url))], {
      stdout: "pipe", stderr: "pipe",
    });
    const [exit, stdout, stderr] = await Promise.all([
      cli.exited, new Response(cli.stdout).text(), new Response(cli.stderr).text(),
    ]);
    expect(exit).toBe(0);
    expect(stdout).toBe(`${executable}\n`);
    expect(stderr).toBe("");
  });

  test("invalid literal detected despite inline allow and untrusted config/ignore", async () => {
    const root = await fixture();
    const source = `val apiKey = "${invalidSecret()}" // gitleaks:allow\n`;
    await writeFile(join(root, "Credential.kt"), source);
    const first = await scanSecrets({ root, executable });
    expect(first.exitCode).toBe(1);
    expect(first.findings).toEqual([{ ruleId: "generic-api-key", file: "Credential.kt", line: 1 }]);
    expect(first.toolVersion).toBe(GITLEAKS_VERSION);
    expect(first.elapsedMs).toBeGreaterThanOrEqual(0);
    expect(JSON.stringify(first)).not.toContain(invalidSecret());
    expect(JSON.stringify(first)).not.toContain(source.trim());
    // A PR-local fingerprint and configuration must not suppress a trusted scan.
    await writeFile(join(root, ".gitleaksignore"), "Credential.kt:generic-api-key:1\n");
    await writeFile(join(root, ".gitleaks.toml"), '[allowlist]\npaths = [".*"]\n');
    const second = await scanSecrets({ root, executable });
    expect(second.findings).toEqual(first.findings);
    console.log(`PILOT risk exit=${second.exitCode} findings=${JSON.stringify(second.findings)}`);
  });

  test("similar environment-variable reference is safe", async () => {
    const root = await fixture();
    await writeFile(join(root, "Credential.kt"), 'val apiKey = System.getenv("SERVICE_API_KEY")\n');
    const result = await scanSecrets({ root, executable });
    expect(result.exitCode).toBe(0);
    expect(result.findings).toEqual([]);
    console.log(`PILOT safe exit=${result.exitCode} findings=${result.findings.length}`);
  });

  test("only named generated directories are excluded, not source", async () => {
    const root = await fixture();
    const source = `val apiKey = "${invalidSecret()}"\n`;
    for (const directory of [".git", ".gradle", ".idea", ".kotlin", "build", "app/build"]) {
      await mkdir(join(root, directory), { recursive: true });
      await writeFile(join(root, directory, "Generated.kt"), source);
    }
    const generated = await scanSecrets({ root, executable });
    expect(generated.findings).toEqual([]);
    await mkdir(join(root, "app/src"), { recursive: true });
    await writeFile(join(root, "app/src/Credential.kt"), source);
    const result = await scanSecrets({ root, executable });
    expect(result.findings).toEqual([{ ruleId: "generic-api-key", file: "app/src/Credential.kt", line: 1 }]);
  });

  test("invalid trusted configuration is an execution error", async () => {
    const root = await fixture();
    await writeFile(join(root, "invalid.toml"), "[not valid TOML");
    await expect(scanSecrets({ root, executable, config: join(root, "invalid.toml") })).rejects.toThrow();
  });
});

// A real subprocess preserves CLI exit/report behavior without mocking the scanner.
async function fakeTool(root, { report = "[]", exitCode = 0, mode = "report" } = {}) {
  const executable = join(root, "fake-gitleaks");
  await writeFile(executable, `#!${process.execPath}
const args = process.argv.slice(2);
if (args[0] === "version") {
  console.log("8.30.1");
  process.exit(0);
}
const mode = ${JSON.stringify(mode)};
if (mode === "timeout") { for (;;) {} }
if (mode === "unsupported") {
  console.error("unknown flag: --ignore-gitleaks-allow");
  process.exit(1);
}
await Bun.write(${JSON.stringify(join(root, "invocation.json"))}, JSON.stringify(args));
if (mode !== "missing") {
  await Bun.write(args[args.indexOf("--report-path") + 1], ${JSON.stringify(report)});
}
console.error(${JSON.stringify(invalidSecret())});
process.exit(${exitCode});
`);
  await chmod(executable, 0o700);
  return executable;
}

describe("execution and report boundaries", () => {
  test("projects metadata only, enforces critical flags, removes temporary report", async () => {
    const root = await fixture();
    const executable = await fakeTool(root, {
      exitCode: 1,
      report: JSON.stringify([{
        RuleID: "generic-api-key", File: join(root, "Credential.kt"), StartLine: 3,
        Secret: invalidSecret(), Match: `apiKey = "${invalidSecret()}"`,
      }]),
    });
    const result = await scanSecrets({ root, executable });
    expect(result.findings).toEqual([{ ruleId: "generic-api-key", file: "Credential.kt", line: 3 }]);
    expect(JSON.stringify(result)).not.toContain(invalidSecret());
    const args = JSON.parse(await readFile(join(root, "invocation.json"), "utf8"));
    expect(args.slice(0, 2)).toEqual(["dir", root]);
    for (const flag of ["--config", "--gitleaks-ignore-path", "--ignore-gitleaks-allow", "--redact=100"]) {
      expect(args).toContain(flag);
    }
    const reportPath = args[args.indexOf("--report-path") + 1];
    await expect(access(dirname(reportPath))).rejects.toThrow();
  });

  for (const [name, options, message] of [
    ["invalid JSON", { report: "{malformed" }, "report missing or malformed"],
    ["wrong report shape", { report: "{}" }, "report malformed"],
    ["missing report", { mode: "missing" }, "report missing or malformed"],
    ["exit one without findings", { exitCode: 1 }, "exit code and report disagree"],
    ["unexpected exit", { exitCode: 2 }, "scan execution failed"],
    ["unsupported critical flag", { mode: "unsupported" }, "report missing or malformed"],
    ["invalid finding fields", { report: '[{"RuleID":"generic-api-key","File":"a.kt","StartLine":0}]' }, "report malformed"],
    ["outside root", { report: '[{"RuleID":"generic-api-key","File":"../a.kt","StartLine":1}]' }, "out-of-scope path"],
    ["clean exit with findings", { report: '[{"RuleID":"generic-api-key","File":"a.kt","StartLine":1}]' }, "exit code and report disagree"],
  ]) {
    test(name, async () => {
      const root = await fixture();
      const executable = await fakeTool(root, options);
      await expect(scanSecrets({ root, executable })).rejects.toThrow(message);
    });
  }

  test("missing executable is an execution error", async () => {
    const root = await fixture();
    await expect(scanSecrets({ root, executable: join(root, "not-installed") }))
      .rejects.toThrow("execution failed");
  });

  test("never-returning process is killed by the timeout", async () => {
    const root = await fixture();
    const executable = await fakeTool(root, { mode: "timeout" });
    await expect(scanSecrets({ root, executable, timeoutMs: 1000 }))
      .rejects.toThrow("timed out or was terminated");
  });

  test("nonempty trusted ignore is rejected", async () => {
    const root = await fixture();
    const ignorePath = join(root, "nonempty.ignore");
    await writeFile(ignorePath, "Credential.kt:generic-api-key:1\n");
    await expect(scanSecrets({ root, executable: "unused", ignorePath })).rejects.toThrow("must be empty");
  });
});
