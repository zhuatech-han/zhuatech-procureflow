// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import { test } from "node:test";
import assert from "node:assert/strict";
import { api, resetCsrf } from "./api.js";
import { money, date } from "./schema.js";
const response = (value, status = 200) => ({
  ok: status === 200,
  status,
  json: async () => value,
});
test("write requests carry CSRF without storage of session credentials", async () => {
  resetCsrf();
  const calls = [];
  globalThis.fetch = async (path, init) => {
    calls.push([path, init]);
    return response(
      path.endsWith("csrf")
        ? { header: "X-CSRF-TOKEN", token: "test-csrf" }
        : { ok: true },
    );
  };
  await api("/rfqs", "POST", { title: "测试" });
  assert.equal(calls[1][1].headers["X-CSRF-TOKEN"], "test-csrf");
  assert.equal(calls[1][1].headers.Authorization, undefined);
});
test("expired session resets CSRF bootstrap", async () => {
  resetCsrf();
  let bootstraps = 0;
  globalThis.fetch = async (path) => {
    if (path.endsWith("csrf")) {
      bootstraps++;
      return response({ header: "X-CSRF-TOKEN", token: "test-csrf" });
    }
    return response({ code: "UNAUTHENTICATED" }, 401);
  };
  await assert.rejects(api("/auth/me"), /UNAUTHENTICATED/);
  await assert.rejects(api("/auth/me"), /UNAUTHENTICATED/);
  assert.equal(bootstraps, 2);
});
test("absent price stays absent in sealed UI", () => {
  assert.equal(money(null), "—");
  assert.equal(money(0), "0.00");
});
test("date display obeys configured time zone", () => {
  const instant = "2026-09-30T00:00:00Z";
  assert.notEqual(date(instant, "Asia/Shanghai"), date(instant, "UTC"));
});
