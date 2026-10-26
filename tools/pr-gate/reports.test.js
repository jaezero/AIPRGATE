import { test, expect } from "bun:test";
import { parseJUnit, parseLint, normalizePath } from "./reports.mjs";

const suite = (values = {}) => {
  const counts = { tests: 12, failures: 0, errors: 0, skipped: 0, ...values };
  return `<testsuite ${Object.entries(counts).map(([key, value]) => `${key}="${value}"`).join(" ")}></testsuite>`;
};

test("JUnit counts prove execution, failure and skips separately", () => {
  expect(parseJUnit(suite())).toEqual({ tests: 12, failures: 0, errors: 0, skipped: 0 });
  expect(parseJUnit(suite({ failures: 1, skipped: 1 }))).toMatchObject({ failures: 1, skipped: 1 });
  expect(parseJUnit(`<testsuites>${suite({ tests: 2 })}${suite({ tests: 3 })}</testsuites>`).tests).toBe(5);
});

for (const xml of [
  "", "<not-tests/>", '<testsuite tests="x"/>',
  '<testsuite tests="1" failures="0" errors="0" skipped="0">',
  suite({ tests: 1, failures: 2 }),
]) {
  test("malformed JUnit cannot stand in for executed tests", () => expect(() => parseJUnit(xml)).toThrow());
}

test("Lint parser keeps machine rule and location without source text", () => {
  const findings = parseLint('<issues format="6"><issue id="TrustAllX509TrustManager" severity="Warning" message="source detail"><location file="C:\\Project\\app\\Trust.kt" line="10"/></issue></issues>', ["C:\\Project"]);
  expect(findings).toEqual([{
    ruleId: "lint:TrustAllX509TrustManager", file: "app/Trust.kt", line: 10, detail: "lint-severity:Warning",
  }]);
});

test("legitimate empty Lint report is accepted", () =>
  expect(parseLint('<issues format="6"/>')).toEqual([]));

for (const xml of [
  "", "<issues/>", '<issues format="6">', '<issues format="99"></issues>',
  '<issues format="6"><issue id="LintError" severity="Error"/></issues>',
  '<issues format="6"><issue id="Known" severity="Warning"/></issues>',
  '<!DOCTYPE issues SYSTEM "file:///etc/passwd"><issues format="6"/>',
]) {
  test("missing malformed or operational Lint report is error", () => expect(() => parseLint(xml)).toThrow());
}

test("report roots are normalized to repository-relative paths", () => {
  expect(normalizePath("/workspace/candidate/app/src/A.kt", ["/workspace/candidate"])).toBe("app/src/A.kt");
});
