import { mkdir, readFile, writeFile } from "node:fs/promises";
import { resolve, join } from "node:path";
import { fileURLToPath } from "node:url";
import { runCommand } from "./process.mjs";

const root = fileURLToPath(new URL("../..", import.meta.url));
const out = resolve(process.argv[2] ?? join(root, "docs/results/pr-gate-20261025/engine-tests"));
await mkdir(out, { recursive: true });
process.env.NO_COLOR = "1";
const startedAt = new Date().toISOString();
const result = await runCommand([process.execPath, "test", "./tools/pr-gate"], {
  cwd: root, timeoutMs: 300_000, logPath: join(out, "tests.log"),
});
const log = await readFile(join(out, "tests.log"), "utf8");
const passed = log.match(/(\d+)\s+pass\b/);
const failed = log.match(/(\d+)\s+fail\b/);
if (!passed || !failed) throw new Error("Test summary not found; inspect preserved log");
const record = {
  command: "bun test ./tools/pr-gate", startedAt, finishedAt: new Date().toISOString(),
  exitCode: result.exitCode, elapsedMs: result.elapsedMs,
  passed: Number(passed[1]), failed: Number(failed[1]),
};
await writeFile(join(out, "run.json"), JSON.stringify(record, null, 2) + "\n", "utf8");
console.log(JSON.stringify(record));
process.exitCode = result.exitCode || (record.failed > 0 ? 1 : 0);
