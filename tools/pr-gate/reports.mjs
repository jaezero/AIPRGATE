function array(value) {
  return value === undefined ? [] : Array.isArray(value) ? value : [value];
}

function document(xml) {
  if (typeof xml !== "string" || /<!DOCTYPE|<!ENTITY/i.test(xml)) {
    throw new Error("Unsupported XML document");
  }
  return Bun.XML.parse(xml);
}

export function parseJUnit(xml) {
  const parsed = document(xml);
  if (!/(?:<\/testsuite>|<\/testsuites>|<testsuite\b[^>]*\/>)\s*$/.test(xml)) {
    throw new Error("Incomplete JUnit document");
  }
  const suites = array(parsed.testsuite ?? parsed.testsuites?.testsuite);
  if (suites.length === 0) throw new Error("Missing JUnit test suites");
  const total = { tests: 0, failures: 0, errors: 0, skipped: 0 };
  for (const suite of suites) {
    for (const key of Object.keys(total)) {
      const value = suite[`@${key}`];
      if (typeof value !== "string" || !/^\d+$/.test(value)) {
        throw new Error(`Invalid JUnit count: ${key}`);
      }
      total[key] += Number(value);
    }
  }
  if (total.failures + total.errors + total.skipped > total.tests) {
    throw new Error("Inconsistent JUnit counts");
  }
  return total;
}

export function normalizePath(file, roots) {
  let path = file.replaceAll("\\", "/");
  for (const root of roots) {
    const prefix = root.replaceAll("\\", "/").replace(/\/$/, "") + "/";
    if (path.toLowerCase().startsWith(prefix.toLowerCase())) {
      path = path.slice(prefix.length);
      break;
    }
  }
  return path;
}

export function parseLint(xml, roots = []) {
  const parsed = document(xml);
  if (!parsed.issues || parsed.issues["@format"] !== "6" ||
      !/(?:<\/issues>|<issues\b[^>]*\/>)\s*$/.test(xml)) {
    throw new Error("Missing or unsupported Android Lint report");
  }
  return array(parsed.issues.issue).map((issue) => {
    if (typeof issue["@id"] !== "string" || typeof issue["@severity"] !== "string") {
      throw new Error("Malformed Lint issue");
    }
    if (issue["@id"] === "LintError") throw new Error("Lint reported an execution error");
    const location = array(issue.location)[0];
    if (!location || typeof location["@file"] !== "string") throw new Error("Missing Lint location");
    const line = location["@line"] ?? "0";
    if (!/^\d+$/.test(line)) throw new Error("Malformed Lint line");
    return {
      ruleId: `lint:${issue["@id"]}`,
      file: normalizePath(location["@file"], roots),
      line: Number(line),
      detail: `lint-severity:${issue["@severity"]}`,
    };
  });
}
