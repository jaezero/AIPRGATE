#!/usr/bin/env bun
import { createHash } from "node:crypto";
import { chmod, mkdir, mkdtemp, readFile, rename, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";

export const GITLEAKS_VERSION = "8.30.1";
const ARCHIVE_SHA256 = "551f6fc83ea457d62a0d98237cbad105af8d557003051f41f3e7ca7b3f2470eb";
const RELEASE_URL = "https://github.com/gitleaks/gitleaks/releases/download/v8.30.1/gitleaks_8.30.1_linux_x64.tar.gz";
const sha256 = (bytes) => createHash("sha256").update(bytes).digest("hex");

export async function installGitleaks() {
  if (process.platform !== "linux" || process.arch !== "x64") {
    throw new Error("Gitleaks bootstrap supports Linux x64 only");
  }
  const cache = join(tmpdir(), `aiprgate-tools-${process.getuid()}`, `gitleaks-${GITLEAKS_VERSION}`);
  await mkdir(cache, { recursive: true, mode: 0o700 });
  const archivePath = join(cache, "release.tar.gz");
  let bytes;
  try {
    bytes = await readFile(archivePath);
  } catch (error) {
    if (error.code !== "ENOENT") throw error;
    const response = await fetch(RELEASE_URL, { signal: AbortSignal.timeout(120_000) });
    if (!response.ok) throw new Error(`Gitleaks download failed: HTTP ${response.status}`);
    bytes = Buffer.from(await response.arrayBuffer());
  }
  // Verify the exact bytes used by the archive reader, including on cache hits.
  if (sha256(bytes) !== ARCHIVE_SHA256) {
    throw new Error("Gitleaks archive SHA-256 mismatch");
  }
  const entry = (await new Bun.Archive(bytes).files()).get("gitleaks");
  if (!entry) throw new Error("Gitleaks archive has no binary");
  const binaryBytes = Buffer.from(await entry.arrayBuffer());
  const executable = join(cache, "gitleaks");
  const staging = await mkdtemp(join(cache, "install-"));
  try {
    await writeFile(join(staging, "release.tar.gz"), bytes, { mode: 0o600 });
    await rename(join(staging, "release.tar.gz"), archivePath);
    // Reconstitute from the verified archive rather than trusting a cached executable.
    await writeFile(join(staging, "gitleaks"), binaryBytes, { mode: 0o700 });
    await chmod(join(staging, "gitleaks"), 0o700);
    await rename(join(staging, "gitleaks"), executable);
  } finally {
    await rm(staging, { recursive: true, force: true });
  }
  return executable;
}

if (import.meta.main) {
  try {
    console.log(await installGitleaks());
  } catch {
    console.error("Gitleaks bootstrap failed");
    process.exitCode = 1;
  }
}
