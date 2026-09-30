// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
let csrf;
/** 同源请求；CSRF 令牌保存在内存，会话由 HttpOnly Cookie 管理。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export async function api(path, method = "GET", body) {
  if (!csrf || path === "/auth/csrf") {
    const response = await fetch("/api/auth/csrf");
    if (!response.ok) throw new Error("NETWORK_ERROR");
    csrf = await response.json();
    if (path === "/auth/csrf") return csrf;
  }
  const response = await fetch("/api" + path, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(method === "GET" ? {} : { [csrf.header]: csrf.token }),
    },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
  const value = await response.json().catch(() => ({ code: "NETWORK_ERROR" }));
  if (!response.ok) {
    if (response.status === 401) csrf = null;
    throw new Error(value.code || "NETWORK_ERROR");
  }
  return value;
}
/** 会话结束后重新生成 CSRF 引导。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function resetCsrf() {
  csrf = null;
}
