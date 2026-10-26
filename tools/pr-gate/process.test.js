import { test, expect } from "bun:test";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { runCommand } from "./process.mjs";

test("CI rejects simulated identity overrides before reading files", async () => {
  const cli = fileURLToPath(new URL("./cli.mjs", import.meta.url));
  for (const args of [
    ["local"],
    ["aggregate", "--latest-file", "/nonexistent"],
    ["check", "pr-info", "--event", "/nonexistent"],
    ["check", "pr-info", "--run-id", "123"],
    ["check", "pr-info", "--attempt", "2"],
  ]) {
    const child = Bun.spawn([process.execPath, cli, ...args], {
      env: { ...process.env, GITHUB_ACTIONS: "true" },
      stdout: "pipe", stderr: "pipe",
    });
    const [code, stderr] = await Promise.all([child.exited, new Response(child.stderr).text()]);
    expect(code).not.toBe(0);
    expect(stderr).not.toContain("ENOENT");
    expect(stderr).toContain("CI_IDENTITY_OVERRIDE_FORBIDDEN");
  }
});

test("command success and failure retain actual exit and output", async () => {
  const directory = await mkdtemp(join(tmpdir(), "aiprgate-process-test-"));
  try {
    for (const code of [0, 3]) {
      const logPath = join(directory, `${code}.log`);
      const result = await runCommand([
        process.execPath, "-e", `console.log("test-output"); process.exitCode = ${code};`,
      ], { cwd: directory, timeoutMs: 5000, logPath });
      expect(result.exitCode).toBe(code);
      expect(await readFile(logPath, "utf8")).toContain("test-output");
    }
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});

test("execution deadline kills a never-ending child instead of passing", async () => {
  const directory = await mkdtemp(join(tmpdir(), "aiprgate-timeout-test-"));
  try {
    await expect(runCommand([
      process.execPath, "-e", 'Bun.serve({port: 0, fetch() { return new Response("test"); }});',
    ], { cwd: directory, timeoutMs: 100, logPath: join(directory, "timeout.log") }))
      .rejects.toThrow("COMMAND_TIMEOUT");
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});
