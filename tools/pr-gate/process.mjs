import { mkdir, writeFile } from "node:fs/promises";
import { dirname } from "node:path";

export async function runCommand(command, { cwd, timeoutMs, logPath }) {
  const started = performance.now();
  const child = Bun.spawn(command, { cwd, stdout: "pipe", stderr: "pipe" });
  let timedOut = false;
  const deadline = setTimeout(() => {
    timedOut = true;
    child.kill("SIGKILL");
  }, timeoutMs);
  try {
    const [stdout, stderr, exitCode] = await Promise.all([
      new Response(child.stdout).text(),
      new Response(child.stderr).text(),
      child.exited,
    ]);
    await mkdir(dirname(logPath), { recursive: true });
    await writeFile(logPath, `${stdout}\n${stderr}`, "utf8");
    if (timedOut) throw new Error("COMMAND_TIMEOUT");
    return { exitCode, elapsedMs: Math.round(performance.now() - started) };
  } finally {
    clearTimeout(deadline);
  }
}
