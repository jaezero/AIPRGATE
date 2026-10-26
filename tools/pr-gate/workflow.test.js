import { test, expect } from "bun:test";
import { readFileSync } from "node:fs";
import { CHECKS } from "./core.mjs";
import { auditProtection, inspectRepository } from "./github-protection.mjs";

const source = (path) => readFileSync(new URL(`../../${path}`, import.meta.url), "utf8");
const workflow = Bun.YAML.parse(source(".github/workflows/pr-gate.yml"));
const action = Bun.YAML.parse(source(".github/actions/run-check/action.yml"));
const protection = JSON.parse(source("policy/main-protection.json"));
const soloProtection = JSON.parse(source("policy/main-protection-solo.json"));

test("four independent jobs precede an always-running stable required gate", () => {
  for (const name of CHECKS) {
    expect(workflow.jobs[name].needs).toBeUndefined();
    expect(workflow.jobs[name]["timeout-minutes"]).toBeGreaterThan(0);
    expect(workflow.jobs[name].steps[0].with.ref).toBe("${{ github.event.pull_request.base.sha }}");
    expect(workflow.jobs[name].steps[1].with.check).toBe(name);
  }
  const gate = workflow.jobs["quality-gate"];
  expect(gate.name).toBe("quality-gate");
  expect(gate.needs).toEqual(CHECKS);
  expect(gate.if).toBe("${{ always() }}");
});

test("push and body changes rerun while old runs are cancelled", () => {
  const event = workflow.on.pull_request;
  expect(event.branches).toEqual(["main"]);
  for (const type of ["opened", "synchronize", "reopened", "edited"]) expect(event.types).toContain(type);
  expect(workflow.concurrency["cancel-in-progress"]).toBe(true);
});

test("PR execution is read-only and is not pull_request_target", () => {
  expect(workflow.on.pull_request_target).toBeUndefined();
  expect(workflow.permissions).toEqual({ contents: "read", "pull-requests": "read" });
  expect(action.runs.steps.find((step) => step.name === "Checkout exact PR head").with)
    .toMatchObject({ ref: "${{ github.event.pull_request.head.sha }}", "persist-credentials": false });
});

test("external actions are commit-pinned and failed checks are not ignored", () => {
  const steps = [...Object.values(workflow.jobs).flatMap((job) => job.steps), ...action.runs.steps];
  for (const step of steps) {
    if (step.uses && !step.uses.startsWith("./")) expect(step.uses).toMatch(/@[a-f0-9]{40}$/);
    expect(step["continue-on-error"]).toBeUndefined();
  }
});

test("receipts are uploaded after failure and scoped to the run attempt", () => {
  const upload = action.runs.steps.find((step) => step.name === "Preserve machine receipt");
  expect(upload.if).toBe("always()");
  expect(upload.with["if-no-files-found"]).toBe("error");
  expect(upload.with.name).toContain("${{ github.run_attempt }}");
  const download = workflow.jobs["quality-gate"].steps.find((step) => step.name === "Download only this run and attempt");
  expect(download.with.pattern).toContain("${{ github.run_attempt }}");
});

test("desired branch policy enforces humans latest push and checks", () =>
  expect(auditProtection(protection)).toEqual({ configured: true, missing: [] }));

for (const mutate of [
  (p) => { p.required_status_checks.checks[0].app_id = -1; },
  (p) => { p.required_status_checks.strict = false; },
  (p) => { p.required_pull_request_reviews.required_approving_review_count = 0; },
  (p) => { p.required_pull_request_reviews.dismiss_stale_reviews = false; },
  (p) => { p.required_pull_request_reviews.require_last_push_approval = false; },
  (p) => { p.enforce_admins = false; },
  (p) => { p.required_pull_request_reviews.bypass_pull_request_allowances.users = ["example"]; },
  (p) => { p.allow_force_pushes = true; },
]) {
  test("weakened branch policy is not configured", () => {
    const changed = structuredClone(protection);
    mutate(changed);
    expect(auditProtection(changed).configured).toBe(false);
  });
}

test("solo policy passes only the solo audit and keeps every non-approval rule", () => {
  expect(auditProtection(soloProtection, { solo: true })).toEqual({ configured: true, missing: [] });
  expect(auditProtection(soloProtection).missing).toEqual(["non-author-review", "approve-latest-push"]);
  for (const mutate of [
    (p) => { p.required_status_checks.checks[0].app_id = -1; },
    (p) => { p.enforce_admins = false; },
    (p) => { p.required_pull_request_reviews.bypass_pull_request_allowances.users = ["example"]; },
    (p) => { delete p.required_pull_request_reviews; },
  ]) {
    const changed = structuredClone(soloProtection);
    mutate(changed);
    expect(auditProtection(changed, { solo: true }).configured).toBe(false);
  }
});

test("repository inspection performs reads only and distinguishes absent protection", async () => {
  const paths = [];
  const state = await inspectRepository("example/repo", async (path) => {
    paths.push(path);
    return path.endsWith("/branches/main") ? { protected: false } : { name: "repo" };
  });
  expect(paths).toEqual(["/repos/example/repo", "/repos/example/repo/branches/main"]);
  expect(state.protected).toBe(false);
  expect(state.configured).toBe(false);
});

test("inaccessible protected branch does not become an unprotected success", async () => {
  await expect(inspectRepository("example/repo", async (path) => {
    if (path.endsWith("/protection")) throw new Error("HTTP 403");
    return path.endsWith("/branches/main") ? { protected: true } : {};
  })).rejects.toThrow("HTTP 403");
});
