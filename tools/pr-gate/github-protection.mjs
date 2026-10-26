import { readFile } from "node:fs/promises";
import { parseArgs } from "node:util";

const enabled = (value) => value === true || value?.enabled === true;

// solo: a one-person repository cannot obtain a non-author approval, so the
// approval count and latest-push approval are not required. Every other rule
// (quality-gate, strict checks, admins, no bypass) still applies.
export function auditProtection(protection, { solo = false } = {}) {
  const missing = [];
  const checks = protection?.required_status_checks;
  if (!checks?.strict) missing.push("strict-required-checks");
  if (!checks?.checks?.some((check) => check.context === "quality-gate" && check.app_id === 15368)) {
    missing.push("quality-gate-from-github-actions");
  }
  const reviews = protection?.required_pull_request_reviews;
  if (!reviews) missing.push("pull-request-required");
  if (!solo && !(reviews?.required_approving_review_count >= 1)) missing.push("non-author-review");
  if (!reviews?.dismiss_stale_reviews) missing.push("dismiss-stale-reviews");
  if (!solo && !reviews?.require_last_push_approval) missing.push("approve-latest-push");
  if (!enabled(protection?.enforce_admins)) missing.push("enforce-admins");
  const bypass = reviews?.bypass_pull_request_allowances;
  if (bypass && ["users", "teams", "apps"].some((key) => (bypass[key]?.length ?? 0) > 0)) {
    missing.push("remove-review-bypass");
  }
  if (enabled(protection?.allow_force_pushes)) missing.push("disable-force-push");
  if (enabled(protection?.allow_deletions)) missing.push("disable-main-deletion");
  if (!enabled(protection?.required_conversation_resolution)) missing.push("resolve-review-conversations");
  return { configured: missing.length === 0, missing };
}

// GitHub accepts review bypass allowances only for organization repositories.
// An empty allowance grants nothing, so dropping it for a user-owned repository
// sends the same rules; a non-empty allowance is never silently discarded.
export function protectionPayload(policy, ownerType) {
  const payload = structuredClone(policy);
  const reviews = payload.required_pull_request_reviews;
  const bypass = reviews?.bypass_pull_request_allowances;
  if (ownerType !== "Organization" && bypass) {
    if (["users", "teams", "apps"].some((key) => (bypass[key]?.length ?? 0) > 0)) {
      throw new Error("Review bypass allowances require an organization repository");
    }
    delete reviews.bypass_pull_request_allowances;
  }
  return payload;
}

export async function inspectRepository(repository, request, options = {}) {
  if (!/^[\w.-]+\/[\w.-]+$/.test(repository)) throw new Error("Expected owner/repository");
  // Check the repository and branch first: a 404 for protection is not by
  // itself proof that protection is absent or that the account has access.
  const repo = await request(`/repos/${repository}`);
  const branch = await request(`/repos/${repository}/branches/main`);
  const ownerType = repo?.owner?.type;
  if (branch.protected !== true) {
    return { repository, ownerType, protected: false, ...auditProtection(null, options) };
  }
  const protection = await request(`/repos/${repository}/branches/main/protection`);
  return { repository, ownerType, protected: true, ...auditProtection(protection, options), protection };
}

if (import.meta.main) {
  const { values } = parseArgs({
    args: process.argv.slice(2),
    options: {
      repo: { type: "string" },
      apply: { type: "boolean", default: false },
      file: { type: "string" },
      solo: { type: "boolean", default: false },
    },
    strict: true,
  });
  try {
    if (values.file) {
      if (values.apply || values.repo) throw new Error("Offline file audit cannot apply repository changes");
      const audit = auditProtection(JSON.parse(await readFile(values.file, "utf8")), { solo: values.solo });
      console.log(JSON.stringify({ mode: "offline-policy-audit", ...audit }, null, 2));
      process.exitCode = audit.configured ? 0 : 1;
    } else {
      if (!values.repo) throw new Error("--repo owner/repository is required");
      if (!process.env.GITHUB_TOKEN) throw new Error("GITHUB_TOKEN with repository administration access is required");
      const request = async (path, method = "GET", body) => {
        const response = await fetch(`https://api.github.com${path}`, {
          method,
          headers: {
            Accept: "application/vnd.github+json",
            Authorization: `Bearer ${process.env.GITHUB_TOKEN}`,
            "X-GitHub-Api-Version": "2022-11-28",
            ...(body ? { "Content-Type": "application/json" } : {}),
          },
          ...(body ? { body: JSON.stringify(body) } : {}),
          signal: AbortSignal.timeout(30_000),
        });
        if (!response.ok) {
          const detail = await response.json().then((body) => body?.message, () => undefined);
          throw new Error(`GitHub ${method} failed: HTTP ${response.status}${detail ? ` (${detail})` : ""}`);
        }
        return response.json();
      };
      const options = { solo: values.solo };
      const state = await inspectRepository(values.repo, request, options);
      if (values.apply && !state.configured) {
        // Never replace or weaken a user's existing protection configuration.
        if (state.protected) throw new Error("Existing protection differs; reconcile it manually without losing stronger rules");
        const policy = values.solo ? "main-protection-solo.json" : "main-protection.json";
        const expected = JSON.parse(await readFile(new URL(`../../policy/${policy}`, import.meta.url), "utf8"));
        await request(`/repos/${values.repo}/branches/main/protection`, "PUT",
          protectionPayload(expected, state.ownerType));
        const applied = await inspectRepository(values.repo, request, options);
        console.log(JSON.stringify({ mode: "applied-and-read-back", ...applied }, null, 2));
        process.exitCode = applied.configured ? 0 : 1;
      } else {
        console.log(JSON.stringify({ mode: "read-only-inspection", ...state }, null, 2));
        process.exitCode = state.configured ? 0 : 1;
      }
    }
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
