// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
export const states = {
  DRAFT: "草稿",
  RETURNED: "退回修改",
  PENDING_APPROVAL: "待发布审批",
  OPEN: "报价中",
  CLOSED: "已封标",
  PENDING_AWARD: "待定标审批",
  AWARDED: "已定标",
  CANCELLED: "已取消",
  PENDING: "待准入审核",
  APPROVED: "已准入",
  REJECTED: "未准入",
  SUBMITTED: "有效版本",
  SUPERSEDED: "已被重报替代",
  WITHDRAWN: "已撤回",
};
export const errors = {
  SELF_APPROVAL: "提交人与审批人必须不同",
  OUT_OF_SCOPE: "无权访问该记录",
  FORBIDDEN: "没有此操作权限",
  UNAUTHENTICATED: "登录已失效，请重新登录",
  LOGIN_FAILED: "账号或密码不正确",
  LOGIN_THROTTLED: "登录失败过多，请稍后再试",
  INVALID_STATE: "当前状态不允许此操作",
  STALE_VERSION: "记录已更新，请刷新后重试",
  IDEMPOTENCY_CONFLICT: "请求标识已用于其他内容",
  BEFORE_DEADLINE: "报价截止后才能封标",
  DEADLINE_PASSED: "报价已截止",
  SUPPLIER_INELIGIBLE: "供应商未准入、已停用或资质到期",
  QUALIFICATION_EXPIRED: "资质有效期必须晚于今天",
  INSUFFICIENT_INVITATIONS: "受邀供应商数量未达到最低要求",
  INSUFFICIENT_QUOTES: "有效报价不足，需要填写例外说明",
  QUOTE_INELIGIBLE: "所选报价已失效或不属于此询价",
  INVALID_DEADLINE: "截止时间须在未来180天内",
  INVALID_QUOTE_VALIDITY: "报价有效期须晚于询价截止时间，且不超过365天",
  INCOMPLETE_QUOTE: "请对全部需求行报价",
  INVALID_AMOUNT: "数量或金额超出允许范围",
  INVALID_PRECISION: "数量最多3位小数、单价4位小数、税率2位小数",
  INVALID_TAX: "税率须为0–100%",
  WEAK_PASSWORD: "密码需12–72位，含大小写字母和数字",
  OLD_PASSWORD_INVALID: "原密码不正确",
  LAST_ADMIN: "必须保留至少一位全范围管理员",
  CONFLICT: "记录重名或被业务引用，无法保存或删除",
  INVALID_INPUT: "请检查必填字段、长度和输入格式",
  INVALID_SUPPLIER_ACCOUNT: "供应商账号须绑定同部门供应商",
  INVALID_SUPPLIER_ROLE: "供应商角色只允许门户权限",
  ROLE_IN_USE: "已有账号与此角色的新范围不匹配",
  REGISTERED_SETTINGS_ONLY: "只能修改现有参数",
  NO_QUOTE: "没有可撤回的报价",
  INVALID_CATEGORY: "请使用已登记的业务分类",
  UNSUPPORTED_CURRENCY: "仅支持保留两位小数的ISO币种",
  BUILTIN_RESOURCE: "系统内建资源不可删除",
  NETWORK_ERROR: "请求失败，请检查网络",
};
export const adminFields = {
  users: [
    ["username", "登录账号"],
    ["displayName", "显示名称"],
    ["password", "设置密码", "password"],
    ["roleId", "角色", "roles"],
    ["departmentId", "部门", "departments"],
    ["supplierId", "绑定供应商（供应商角色必填）", "suppliers"],
    ["enabled", "启用", "checkbox"],
  ],
  roles: [
    ["name", "角色名称"],
    ["scope", "数据范围", "scope"],
    ["permissions", "权限", "permissions"],
  ],
  departments: [["name", "部门名称"]],
  permissions: [
    ["code", "权限代码", "readonly"],
    ["name", "显示名称"],
  ],
  menus: [
    ["code", "页面代码", "readonly"],
    ["name", "中文名称"],
    ["nameEn", "英文名称"],
    ["permissionCode", "所需权限", "permissionCode"],
    ["position", "顺序", "number"],
    ["enabled", "启用", "checkbox"],
  ],
  dictionaries: [
    ["type", "字典类型"],
    ["code", "代码"],
    ["name", "中文名称"],
    ["nameEn", "英文名称"],
  ],
  settings: [
    ["code", "参数名称", "readonly"],
    ["value", "参数值"],
  ],
};
/** 操作日期按配置时区显示，输入使用浏览器本地时区。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function date(value, zone = "Asia/Shanghai") {
  return value
    ? new Intl.DateTimeFormat("zh-CN", {
        dateStyle: "short",
        timeStyle: "short",
        timeZone: zone,
      }).format(new Date(value))
    : "—";
}
/** 仅显示已有金额，不计算或猜测财务结果。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function money(value) {
  return value == null
    ? "—"
    : Number(value).toLocaleString("zh-CN", {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      });
}
/** 将服务器UTC时间转换成本地日期输入值。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function localInput(value) {
  const d = new Date(value);
  return new Date(d - d.getTimezoneOffset() * 60000).toISOString().slice(0, 19);
}
