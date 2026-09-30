<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, onMounted } from "vue";
import { api, resetCsrf } from "./api";
import { states, errors, adminFields, date, money, localInput } from "./schema";
const me = ref(null),
  page = ref(""),
  rows = ref([]),
  suppliers = ref([]),
  options = ref({ categories: [], settings: [] }),
  refs = ref({}),
  stats = ref({ states: {}, awardedAmounts: {} }),
  detail = ref(null),
  busy = ref(false),
  error = ref(""),
  notice = ref(""),
  nav = ref(false),
  dialog = ref(null),
  form = ref({}),
  search = ref(""),
  filter = ref(""),
  sort = ref("newest"),
  index = ref(0),
  total = ref(0),
  login = ref({ username: "admin", password: "" });
const portal = computed(() => me.value?.scope === "SUPPLIER"),
  isAdmin = computed(() => adminFields[page.value]),
  zone = computed(
    () =>
      options.value.settings.find((s) => s.code === "timezone")?.value ||
      "Asia/Shanghai",
  ),
  title = computed(
    () =>
      me.value?.menus.find((m) => m.code === page.value)?.name || "账号与关于",
  ),
  visible = computed(() =>
    isAdmin.value || page.value === "suppliers" || page.value === "audit"
      ? rows.value.filter((r) =>
          Object.values(r).some((v) =>
            String(v).toLowerCase().includes(search.value.toLowerCase()),
          ),
        )
      : rows.value,
  ),
  paged = computed(() =>
    ["rfqs", "portal"].includes(page.value)
      ? visible.value
      : visible.value.slice(index.value * 20, index.value * 20 + 20),
  ),
  count = computed(() =>
    ["rfqs", "portal"].includes(page.value)
      ? total.value
      : visible.value.length,
  ),
  has = (p) => me.value?.permissions.includes(p),
  fmt = (v) => date(v, zone.value);
const companyName = computed(
  () =>
    options.value.settings.find((s) => s.code === "companyName")?.value ||
    "供应商寻源与报价协同",
);
const quoteVersions = computed(() => detail.value?.quotes || []);
const compared = computed(() =>
  [...quoteVersions.value]
    .filter((q) => q.status === "SUBMITTED")
    .sort(
      (a, b) =>
        Number(a.quote?.grossTotal ?? 0) - Number(b.quote?.grossTotal ?? 0),
    ),
);
const columns = computed(() =>
  page.value === "suppliers"
    ? [
        ["code", "编码"],
        ["name", "供应商"],
        ["category", "分类"],
        ["status", "准入"],
        ["validUntil", "资质有效期"],
        ["enabled", "启用"],
      ]
    : page.value === "audit"
      ? [
          ["actor", "操作人"],
          ["action", "动作"],
          ["objectId", "记录"],
          ["departmentId", "部门"],
          ["createdAt", "时间"],
        ]
      : isAdmin.value
        ? adminFields[page.value].filter((f) => f[0] !== "password")
        : [],
);
function display(row, key) {
  const v = row[key];
  if (key === "status") return states[v] || v;
  if (key.endsWith("At")) return fmt(v);
  if (typeof v === "boolean") return v ? "是" : "否";
  if (key === "permissions") return (v || []).join(" · ");
  if (["roleId", "departmentId", "supplierId"].includes(key)) {
    const type =
      key === "roleId"
        ? "roles"
        : key === "departmentId"
          ? "departments"
          : "suppliers";
    return refs.value[type]?.find((x) => x.id === v)?.name || v || "—";
  }
  return v ?? "—";
}
/** 统一忙碌、反馈与会话失效处理。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function run(work) {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    await work();
  } catch (e) {
    error.value = errors[e.message] || `操作失败：${e.message}`;
    if (e.message === "UNAUTHENTICATED") {
      me.value = null;
      dialog.value = null;
    }
  } finally {
    busy.value = false;
  }
}
async function init() {
  options.value = await api("/options");
  if (has("supplier.read") || portal.value)
    suppliers.value = await api("/suppliers");
  if (has("admin")) {
    for (const type of ["roles", "departments", "permissions"])
      refs.value[type] = await api("/admin/" + type);
    refs.value.suppliers = suppliers.value;
  }
  page.value = me.value.menus[0]?.code || "about";
  await load();
}
async function signIn() {
  await run(async () => {
    me.value = await api("/auth/login", "POST", login.value);
    login.value.password = "";
    await init();
  });
}
async function load() {
  detail.value = null;
  if (page.value === "dashboard") stats.value = await api("/dashboard");
  else if (["rfqs", "portal"].includes(page.value)) {
    const query = new URLSearchParams({
      search: search.value,
      status: filter.value,
      sort: sort.value,
      page: index.value,
      size: 20,
    });
    const r = await api("/rfqs?" + query);
    rows.value = r.items;
    total.value = r.total;
    if (portal.value) suppliers.value = await api("/suppliers");
  } else if (page.value === "suppliers") {
    suppliers.value = await api("/suppliers");
    rows.value = suppliers.value;
  } else if (page.value === "audit") rows.value = await api("/audit");
  else if (isAdmin.value) rows.value = await api("/admin/" + page.value);
}
async function navigate(code) {
  await run(async () => {
    page.value = code;
    search.value = "";
    filter.value = "";
    index.value = 0;
    nav.value = false;
    await load();
  });
}
async function openRfq(id) {
  await run(async () => {
    detail.value = await api("/rfqs/" + id);
  });
}
async function query() {
  await run(async () => {
    index.value = 0;
    await load();
  });
}
async function turn(step) {
  await run(async () => {
    index.value += step;
    if (["rfqs", "portal"].includes(page.value)) await load();
  });
}
function newSupplier(s) {
  form.value = s
    ? { ...s }
    : {
        code: "",
        name: "",
        category: options.value.categories[0]?.code,
        contact: "",
        qualification: "",
        validUntil: "",
        enabled: true,
      };
  dialog.value = { type: "supplier", id: s?.id };
}
async function draft(r) {
  await run(async () => {
    if (r) {
      const d = await api("/rfqs/" + r.id);
      form.value = {
        ...d.rfq,
        deadline: localInput(d.rfq.deadline),
        supplierIds: d.invitations.map((i) => i.supplierId),
        lines: d.lines.map((l) => ({ ...l })),
      };
    } else
      form.value = {
        title: "",
        description: "",
        category: options.value.categories[0]?.code,
        currency:
          options.value.settings.find((x) => x.code === "currency")?.value ||
          "CNY",
        departmentId: me.value.departmentId,
        deadline: localInput(Date.now() + 86400000),
        supplierIds: [],
        lines: [
          {
            itemCode: "",
            name: "",
            specification: "",
            unit: "件",
            quantity: 1,
          },
        ],
      };
    suppliers.value = await api("/suppliers");
    dialog.value = { type: "draft", id: r?.id };
  });
}
function adminEdit(row) {
  form.value = row
    ? JSON.parse(JSON.stringify(row))
    : {
        enabled: true,
        permissions: [],
        scope: "DEPARTMENT",
        departmentId: me.value.departmentId,
        position: 0,
      };
  dialog.value = { type: "admin", resource: page.value, id: row?.id };
}
function command(action, quoteId) {
  form.value = {
    version: detail.value.rfq.version,
    requestKey: crypto.randomUUID(),
    note: "",
    quoteId,
    exceptionReason: "",
  };
  dialog.value = { type: "command", action, id: detail.value.rfq.id };
}
function review(s, action) {
  form.value = { version: s.version, note: "" };
  dialog.value = { type: "review", id: s.id, action };
}
function quote() {
  const q = quoteVersions.value[0];
  form.value = {
    requestKey: crypto.randomUUID(),
    leadDays: q?.quote?.leadDays || 7,
    freight: q?.quote?.freight || 0,
    terms: q?.quote?.terms || "",
    validUntil: localInput(Date.now() + 86400000 * 30),
    lines: detail.value.lines.map((l) => ({
      rfqLineId: l.id,
      unitPrice: q?.lines?.find((p) => p.rfqLineId === l.id)?.unitPrice || 0,
      taxRate: q?.lines?.find((p) => p.rfqLineId === l.id)?.taxRate || 0,
    })),
  };
  dialog.value = { type: "quote", id: detail.value.rfq.id };
}
function remove(resource, row) {
  form.value = {};
  dialog.value = { type: "delete", resource, id: row.id, version: row.version };
}
/** 表单提交保留失败输入与幂等键，成功后重读持久化记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
async function submit() {
  await run(async () => {
    const d = dialog.value;
    const body = JSON.parse(JSON.stringify(form.value));
    if (d.type === "supplier")
      await api(
        "/suppliers" + (d.id ? "/" + d.id : ""),
        d.id ? "PUT" : "POST",
        body,
      );
    else if (d.type === "draft") {
      body.deadline = new Date(body.deadline).toISOString();
      await api(
        "/rfqs" + (d.id ? "/" + d.id : ""),
        d.id ? "PUT" : "POST",
        body,
      );
    } else if (d.type === "admin") {
      for (const key of ["roleId", "departmentId", "supplierId"])
        if (body[key] === "" || body[key] == null) body[key] = null;
      await api(
        "/admin/" + d.resource + (d.id ? "/" + d.id : ""),
        d.id ? "PUT" : "POST",
        body,
      );
    } else if (d.type === "review")
      await api(`/suppliers/${d.id}/${d.action}`, "POST", body);
    else if (d.type === "delete")
      await api(
        (d.resource === "rfqs" ? "/rfqs/" : "/admin/" + d.resource + "/") +
          d.id +
          (d.resource === "rfqs" ? "?version=" + d.version : ""),
        "DELETE",
      );
    else if (d.type === "password") {
      await api("/auth/password", "POST", body);
      resetCsrf();
      me.value = null;
    } else {
      if (d.type === "quote")
        body.validUntil = new Date(body.validUntil).toISOString();
      detail.value = await api(
        `/rfqs/${d.id}/${d.type === "quote" ? "quote" : d.action}`,
        "POST",
        body,
      );
    }
    dialog.value = null;
    notice.value = "操作已保存";
    if (me.value) {
      if (detail.value && ["command", "quote"].includes(d.type)) {
      } else await load();
      if (has("admin")) {
        refs.value.roles = await api("/admin/roles");
        refs.value.departments = await api("/admin/departments");
        if (has("supplier.read"))
          refs.value.suppliers = await api("/suppliers");
      }
    }
  });
}
async function exportAdvice() {
  await run(async () => {
    const r = await fetch(`/api/rfqs/${detail.value.rfq.id}/advice.json`);
    if (!r.ok) {
      const e = await r.json();
      throw new Error(e.code);
    }
    const url = URL.createObjectURL(await r.blob());
    const a = document.createElement("a");
    a.href = url;
    a.download = detail.value.rfq.number + "-advice.json";
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    notice.value = "采购建议已下载";
  });
}
async function logout() {
  await run(async () => {
    await api("/auth/logout", "POST");
    resetCsrf();
    me.value = null;
    detail.value = null;
  });
}
onMounted(() =>
  run(async () => {
    try {
      me.value = await api("/auth/me");
      await init();
    } catch (e) {
      if (e.message !== "UNAUTHENTICATED") throw e;
    }
  }),
);
</script>

<template>
  <div v-if="!me" class="login-shell">
    <section class="login-brand">
      <img src="/brand/logo.jpg" alt="知华科技" />
      <h1>供应商寻源与报价协同</h1>
      <p>ProcureFlow · 公开源码学习版</p>
      <div class="steps">
        <span>供应商准入</span><span>询价与报价</span><span>比价与定标</span>
      </div>
      <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
        >知华科技官网 ↗</a
      >
    </section>
    <form class="login-form" @submit.prevent="signIn">
      <h2>登录工作台</h2>
      <p>采购、审批与供应商账号</p>
      <label
        >登录账号<input
          v-model="login.username"
          required
          autocomplete="username"
          maxlength="60" /></label
      ><label
        >密码<input
          v-model="login.password"
          type="password"
          required
          autocomplete="current-password"
      /></label>
      <div v-if="error" class="alert error" role="alert">{{ error }}</div>
      <button class="primary" :disabled="busy">
        {{ busy ? "正在登录…" : "登录" }}</button
      ><small
        >0.1.0 · 仅限学习、研究与非商业交流<br />上海如静知华信息科技有限公司<br />商业咨询微信：zhuatech
        / zhuatech2</small
      >
    </form>
  </div>
  <div v-else class="shell">
    <aside :class="{ expanded: nav }">
      <div class="brand">
        <img src="/brand/logo.jpg" alt="知华科技" /><strong>ProcureFlow</strong
        ><small>供应商协同</small>
      </div>
      <nav>
        <button
          v-for="menu in me.menus"
          :key="menu.id"
          :class="{ active: page === menu.code }"
          :disabled="busy"
          @click="navigate(menu.code)"
        >
          {{ menu.name }}
        </button>
      </nav>
      <div class="sidebar-foot">
        <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
          >知华科技官网 ↗</a
        ><span>公开源码学习版 0.1.0</span>
      </div>
    </aside>
    <div class="workspace">
      <header>
        <button class="nav-toggle" aria-label="展开导航" @click="nav = !nav">
          ☰
        </button>
        <div>
          <span class="eyebrow">{{ companyName }}</span>
          <h1>{{ title }}</h1>
        </div>
        <div class="identity">
          <button :disabled="busy" @click="navigate('about')">
            {{ me.displayName }} · {{ me.role }}</button
          ><button :disabled="busy" @click="logout">退出</button>
        </div>
      </header>
      <main>
        <div v-if="error" class="alert error" role="alert">{{ error }}</div>
        <div v-if="notice" class="alert success" role="status">
          {{ notice }}
        </div>
        <template v-if="page === 'dashboard'"
          ><div class="metric-grid">
            <div class="metric">
              <span>报价中</span><strong>{{ stats.states.OPEN || 0 }}</strong>
            </div>
            <div class="metric">
              <span>待定标审批</span
              ><strong>{{ stats.states.PENDING_AWARD || 0 }}</strong>
            </div>
            <div class="metric">
              <span>已定标</span
              ><strong>{{ stats.states.AWARDED || 0 }}</strong>
            </div>
            <div class="metric">
              <span>已截止待封标</span
              ><strong>{{ stats.overdueOpen || 0 }}</strong>
            </div>
          </div>
          <section class="panel">
            <div class="panel-head">
              <h2>寻源进度</h2>
              <button :disabled="busy" @click="navigate('rfqs')">
                查看询价 →
              </button>
            </div>
            <div v-for="(n, s) in stats.states" :key="s" class="state-row">
              <span>{{ states[s] || s }}</span>
              <div class="bar">
                <i
                  :style="{
                    width:
                      (n / Math.max(...Object.values(stats.states), 1)) * 100 +
                      '%',
                  }"
                ></i>
              </div>
              <strong>{{ n }}</strong>
            </div>
            <p v-if="!Object.keys(stats.states).length" class="empty">
              暂无询价记录
            </p>
          </section>
          <section class="panel">
            <h2>已批准采购建议金额</h2>
            <p class="muted">按币种分别统计，金额不代表采购订单或实际付款。</p>
            <div
              v-for="(n, c) in stats.awardedAmounts"
              :key="c"
              class="amount-row"
            >
              <strong>{{ c }}</strong
              ><span>{{ money(n) }}</span>
            </div>
            <p v-if="!Object.keys(stats.awardedAmounts).length" class="empty">
              暂无已定标建议
            </p>
          </section></template
        >
        <template v-else-if="page === 'rfqs' || page === 'portal'">
          <section v-if="portal && !detail" class="panel supplier-summary">
            <div>
              <h2>{{ suppliers[0]?.name }}</h2>
              <span class="badge" :data-state="suppliers[0]?.status">{{
                states[suppliers[0]?.status]
              }}</span>
              <p class="muted">
                资质有效期：{{ suppliers[0]?.validUntil }} ·
                {{ suppliers[0]?.enabled ? "已启用" : "已停用" }}
              </p>
            </div>
            <button :disabled="busy" @click="newSupplier(suppliers[0])">
              更新资质
            </button>
          </section>
          <section v-if="!detail" class="panel">
            <div class="toolbar">
              <form class="search" @submit.prevent="query">
                <input
                  v-model="search"
                  aria-label="搜索询价"
                  placeholder="搜索询价标题"
                /><select v-model="filter" aria-label="询价状态">
                  <option value="">全部状态</option>
                  <option
                    v-for="s in [
                      'DRAFT',
                      'PENDING_APPROVAL',
                      'RETURNED',
                      'OPEN',
                      'CLOSED',
                      'PENDING_AWARD',
                      'AWARDED',
                      'CANCELLED',
                    ]"
                    :key="s"
                    :value="s"
                  >
                    {{ states[s] }}
                  </option></select
                ><select v-model="sort" aria-label="询价排序">
                  <option value="newest">最新创建</option>
                  <option value="oldest">最早创建</option>
                  <option value="deadline">截止时间</option></select
                ><button :disabled="busy">查询</button>
              </form>
              <button
                v-if="has('rfq.write')"
                class="primary"
                :disabled="busy"
                @click="draft()"
              >
                新建询价
              </button>
            </div>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>询价编号 / 标题</th>
                    <th>分类</th>
                    <th>状态</th>
                    <th>报价截止</th>
                    <th>币种</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="r in rows" :key="r.id">
                    <td>
                      <button
                        class="link"
                        :disabled="busy"
                        @click="openRfq(r.id)"
                      >
                        {{ r.title }}</button
                      ><small>{{ r.number }}</small>
                    </td>
                    <td>{{ r.category }}</td>
                    <td>
                      <span class="badge" :data-state="r.status">{{
                        states[r.status]
                      }}</span>
                    </td>
                    <td>{{ fmt(r.deadline) }}</td>
                    <td>{{ r.currency }}</td>
                    <td>
                      <button :disabled="busy" @click="openRfq(r.id)">
                        查看
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
              <p v-if="!rows.length" class="empty">没有符合条件的询价</p>
            </div>
          </section>
          <template v-else
            ><div class="detail-head">
              <button :disabled="busy" @click="run(load)">← 返回列表</button
              ><span class="badge" :data-state="detail.rfq.status">{{
                states[detail.rfq.status]
              }}</span>
            </div>
            <section class="panel">
              <div class="panel-head">
                <div>
                  <span class="eyebrow">{{ detail.rfq.number }}</span>
                  <h2>{{ detail.rfq.title }}</h2>
                </div>
                <div class="actions">
                  <template v-if="has('rfq.write')"
                    ><button
                      v-if="['DRAFT', 'RETURNED'].includes(detail.rfq.status)"
                      :disabled="busy"
                      @click="draft(detail.rfq)"
                    >
                      编辑</button
                    ><button
                      v-if="['DRAFT', 'RETURNED'].includes(detail.rfq.status)"
                      class="primary"
                      :disabled="busy"
                      @click="command('submit')"
                    >
                      提交发布审批</button
                    ><button
                      v-if="detail.rfq.status === 'OPEN'"
                      :disabled="busy"
                      @click="command('close')"
                    >
                      截止封标</button
                    ><button
                      v-if="
                        [
                          'DRAFT',
                          'RETURNED',
                          'PENDING_APPROVAL',
                          'OPEN',
                          'CLOSED',
                        ].includes(detail.rfq.status)
                      "
                      :disabled="busy"
                      @click="command('cancel')"
                    >
                      取消</button
                    ><button
                      v-if="['DRAFT', 'RETURNED'].includes(detail.rfq.status)"
                      class="danger"
                      :disabled="busy"
                      @click="remove('rfqs', detail.rfq)"
                    >
                      删除草稿
                    </button></template
                  >
                  <template v-if="has('rfq.approve')"
                    ><button
                      v-if="detail.rfq.status === 'PENDING_APPROVAL'"
                      class="primary"
                      :disabled="busy"
                      @click="command('approve')"
                    >
                      批准发布</button
                    ><button
                      v-if="detail.rfq.status === 'PENDING_APPROVAL'"
                      :disabled="busy"
                      @click="command('reject')"
                    >
                      退回修改</button
                    ><button
                      v-if="detail.rfq.status === 'PENDING_AWARD'"
                      class="primary"
                      :disabled="busy"
                      @click="command('award')"
                    >
                      批准定标</button
                    ><button
                      v-if="detail.rfq.status === 'PENDING_AWARD'"
                      :disabled="busy"
                      @click="command('reject-award')"
                    >
                      退回比价
                    </button></template
                  >
                  <button
                    v-if="portal && detail.rfq.status === 'OPEN'"
                    class="primary"
                    :disabled="busy"
                    @click="quote"
                  >
                    提交新版报价</button
                  ><button
                    v-if="
                      portal &&
                      detail.rfq.status === 'OPEN' &&
                      quoteVersions.some((q) => q.status === 'SUBMITTED')
                    "
                    :disabled="busy"
                    @click="command('withdraw')"
                  >
                    撤回报价</button
                  ><button
                    v-if="has('export') && detail.rfq.status === 'AWARDED'"
                    class="primary"
                    :disabled="busy"
                    @click="exportAdvice"
                  >
                    导出采购建议
                  </button>
                </div>
              </div>
              <p class="description">{{ detail.rfq.description }}</p>
              <div class="facts">
                <span>分类：{{ detail.rfq.category }}</span
                ><span>币种：{{ detail.rfq.currency }}</span
                ><span>报价截止：{{ fmt(detail.rfq.deadline) }}</span
                ><span v-if="!portal"
                  >最低有效报价：{{ detail.rfq.minimumQuotes }}</span
                >
              </div>
              <p v-if="detail.rfq.reviewNote" class="review">
                审批意见：{{ detail.rfq.reviewNote }}
              </p>
              <div
                v-if="portal && detail.rfq.status === 'AWARDED'"
                class="alert success"
              >
                {{
                  detail.rfq.won ? "此询价已选择您的报价" : "此询价已完成定标"
                }}
              </div>
              <div class="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>物料编码</th>
                      <th>名称</th>
                      <th>规格与要求</th>
                      <th>数量</th>
                      <th>单位</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="l in detail.lines" :key="l.id">
                      <td>{{ l.itemCode }}</td>
                      <td>{{ l.name }}</td>
                      <td>{{ l.specification }}</td>
                      <td>{{ l.quantity }}</td>
                      <td>{{ l.unit }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </section>
            <section class="panel">
              <div class="panel-head">
                <h2>{{ portal ? "我的报价版本" : "供应商报价与比价" }}</h2>
                <span class="muted">{{ quoteVersions.length }} 个版本</span>
              </div>
              <div v-if="detail.sealed" class="alert neutral">
                密封报价：封标前只显示提交状态与版本，不显示价格和条款。
              </div>
              <div class="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>供应商 / 版本</th>
                      <th>状态</th>
                      <th>提交时间</th>
                      <template v-if="!detail.sealed"
                        ><th>未税金额</th>
                        <th>税额</th>
                        <th>运费</th>
                        <th>含税总额</th>
                        <th>交期 / 有效期</th>
                        <th>付款及其他条款</th>
                        <th>操作</th></template
                      >
                    </tr>
                  </thead>
                  <tbody>
                    <tr
                      v-for="q in !detail.sealed && !portal
                        ? compared
                        : quoteVersions"
                      :key="q.id"
                    >
                      <td>
                        {{ q.supplierName
                        }}<small>版本 {{ q.revision }} · #{{ q.id }}</small>
                      </td>
                      <td>
                        <span class="badge">{{ states[q.status] }}</span
                        ><small v-if="q.quote">{{
                          q.eligible ? "当前可定标" : "已失效或非当前版本"
                        }}</small>
                      </td>
                      <td>{{ fmt(q.submittedAt) }}</td>
                      <template v-if="q.quote"
                        ><td>{{ money(q.quote.netTotal) }}</td>
                        <td>{{ money(q.quote.taxTotal) }}</td>
                        <td>{{ money(q.quote.freight) }}</td>
                        <td class="amount">{{ money(q.quote.grossTotal) }}</td>
                        <td>
                          {{ q.quote.leadDays }} 天<small>{{
                            fmt(q.quote.validUntil)
                          }}</small>
                        </td>
                        <td class="wrap">{{ q.quote.terms }}</td>
                        <td>
                          <button
                            v-if="
                              has('rfq.write') &&
                              detail.rfq.status === 'CLOSED' &&
                              q.eligible
                            "
                            :disabled="busy"
                            @click="command('select', q.id)"
                          >
                            选择报价</button
                          ><span v-if="detail.rfq.selectedQuoteId === q.id"
                            >拟选 / 已选</span
                          >
                        </td></template
                      >
                    </tr>
                  </tbody>
                </table>
                <p v-if="!quoteVersions.length" class="empty">暂无报价</p>
              </div>
              <details v-if="!detail.sealed && quoteVersions.length">
                <summary>全部报价版本与行价格</summary>
                <div
                  v-for="q in quoteVersions"
                  :key="q.id"
                  class="quote-detail"
                >
                  <h3>
                    {{ q.supplierName }} · 版本 {{ q.revision }} ·
                    {{ states[q.status] }}
                  </h3>
                  <div class="table-wrap">
                    <table>
                      <thead>
                        <tr>
                          <th>物料</th>
                          <th>未税单价</th>
                          <th>税率%</th>
                          <th>未税金额</th>
                          <th>税额</th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr v-for="p in q.lines" :key="p.id">
                          <td>
                            {{
                              detail.lines.find((l) => l.id === p.rfqLineId)
                                ?.name
                            }}
                          </td>
                          <td>{{ p.unitPrice }}</td>
                          <td>{{ p.taxRate }}</td>
                          <td>{{ money(p.netAmount) }}</td>
                          <td>{{ money(p.taxAmount) }}</td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                </div>
              </details>
            </section>
            <section v-if="!portal && detail.rfq.selectedQuoteId" class="panel">
              <h2>定标建议</h2>
              <div class="facts">
                <span>报价 #{{ detail.rfq.selectedQuoteId }}</span
                ><span>提交人：{{ detail.rfq.selectedBy }}</span
                ><span v-if="detail.rfq.approvedBy"
                  >审批人：{{ detail.rfq.approvedBy }}</span
                >
              </div>
              <p>{{ detail.rfq.selectionReason }}</p>
              <p v-if="detail.rfq.exceptionReason" class="review">
                有效报价不足例外：{{ detail.rfq.exceptionReason }}
              </p>
            </section></template
          >
        </template>
        <template
          v-else-if="page === 'suppliers' || isAdmin || page === 'audit'"
          ><section class="panel">
            <div class="toolbar">
              <input
                v-model="search"
                placeholder="搜索当前目录"
                aria-label="搜索当前目录"
                @input="index = 0"
              /><button
                v-if="page === 'suppliers' && has('supplier.write')"
                class="primary"
                :disabled="busy"
                @click="newSupplier()"
              >
                新建供应商</button
              ><button
                v-if="
                  isAdmin &&
                  !['menus', 'settings', 'permissions'].includes(page)
                "
                class="primary"
                :disabled="busy"
                @click="adminEdit()"
              >
                新建
              </button>
            </div>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th v-for="c in columns" :key="c[0]">{{ c[1] }}</th>
                    <th v-if="page !== 'audit'">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="r in paged" :key="r.id">
                    <td v-for="c in columns" :key="c[0]">
                      <span
                        v-if="c[0] === 'status'"
                        class="badge"
                        :data-state="r.status"
                        >{{ display(r, c[0]) }}</span
                      ><span v-else>{{ display(r, c[0]) }}</span>
                    </td>
                    <td v-if="page !== 'audit'">
                      <div class="actions">
                        <template v-if="page === 'suppliers'"
                          ><button
                            v-if="has('supplier.write')"
                            :disabled="busy"
                            @click="newSupplier(r)"
                          >
                            编辑</button
                          ><button
                            v-if="
                              has('supplier.review') && r.status === 'PENDING'
                            "
                            :disabled="busy"
                            @click="review(r, 'approve')"
                          >
                            准入</button
                          ><button
                            v-if="
                              has('supplier.review') && r.status === 'PENDING'
                            "
                            :disabled="busy"
                            @click="review(r, 'reject')"
                          >
                            驳回
                          </button>
                          <details>
                            <summary>资质</summary>
                            <p>{{ r.qualification }}</p>
                            <p>{{ r.contact }}</p>
                            <p>{{ r.reviewNote }}</p>
                          </details></template
                        ><template v-else
                          ><button :disabled="busy" @click="adminEdit(r)">
                            编辑</button
                          ><button
                            v-if="
                              !['menus', 'settings', 'permissions'].includes(
                                page,
                              )
                            "
                            class="danger"
                            :disabled="busy"
                            @click="remove(page, r)"
                          >
                            删除
                          </button></template
                        >
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
              <p v-if="!paged.length" class="empty">暂无记录</p>
            </div>
          </section></template
        >
        <section v-else-if="page === 'about'" class="panel about">
          <img src="/brand/logo.jpg" alt="知华科技" />
          <h2>供应商寻源与报价协同 · 0.1.0</h2>
          <p>
            公开源码学习版，仅限个人学习、技术研究与非商业交流，未经书面授权不得商用。
          </p>
          <p>上海如静知华信息科技有限公司</p>
          <p>
            <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
              >https://www.zhuatech.cn/</a
            >
            · 商业咨询微信 zhuatech / zhuatech2
          </p>
          <button
            :disabled="busy"
            @click="
              form = { oldPassword: '', newPassword: '' };
              dialog = { type: 'password' };
            "
          >
            修改我的密码
          </button>
        </section>
        <div
          v-if="!detail && page !== 'dashboard' && page !== 'about'"
          class="pagination"
        >
          <span>共 {{ count }} 条 · 第 {{ index + 1 }} 页</span
          ><button :disabled="busy || index === 0" @click="turn(-1)">
            上一页</button
          ><button
            :disabled="busy || (index + 1) * 20 >= count"
            @click="turn(1)"
          >
            下一页
          </button>
        </div>
      </main>
      <footer>
        知华科技 ·
        <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
          >商业授权与定制咨询</a
        >
      </footer>
    </div>
  </div>
  <div v-if="dialog" class="overlay">
    <section
      class="modal"
      role="dialog"
      aria-modal="true"
      aria-labelledby="modal-title"
    >
      <form @submit.prevent="submit">
        <div class="panel-head">
          <h2 id="modal-title">
            {{
              {
                supplier: "供应商档案与资质",
                draft: "询价需求",
                admin: "管理资料",
                review: "准入审核",
                command: "业务确认",
                quote: "提交新版报价",
                delete: "确认删除",
                password: "修改密码",
              }[dialog.type]
            }}
          </h2>
          <button
            type="button"
            :disabled="busy"
            aria-label="关闭表单"
            @click="dialog = null"
          >
            ✕
          </button>
        </div>
        <template v-if="dialog.type === 'supplier'"
          ><div class="form-grid">
            <label v-if="!portal"
              >供应商编码<input
                v-model="form.code"
                required
                maxlength="60"
                :disabled="!!dialog.id" /></label
            ><label
              >供应商名称<input
                v-model="form.name"
                required
                maxlength="120"
                :disabled="portal" /></label
            ><label
              >业务分类<select v-model="form.category">
                <option
                  v-for="c in options.categories"
                  :key="c.id"
                  :value="c.code"
                >
                  {{ c.name }}
                </option>
              </select></label
            ><label
              >资质有效期<input
                v-model="form.validUntil"
                type="date"
                required /></label
            ><label class="wide"
              >业务联系方式<input
                v-model="form.contact"
                required
                maxlength="200" /></label
            ><label class="wide"
              >资质名称、编号与核验说明<textarea
                v-model="form.qualification"
                required
                maxlength="2000"
              ></textarea></label
            ><label v-if="!portal" class="check"
              ><input v-model="form.enabled" type="checkbox" />启用</label
            >
          </div>
          <p class="muted">
            保存后重新进入准入审核；有效期按UTC日期核验。
          </p></template
        >
        <template v-if="dialog.type === 'draft'"
          ><div class="form-grid">
            <label class="wide"
              >询价标题<input
                v-model="form.title"
                required
                maxlength="200" /></label
            ><label
              >分类<select v-model="form.category">
                <option
                  v-for="c in options.categories"
                  :key="c.id"
                  :value="c.code"
                >
                  {{ c.name }}
                </option>
              </select></label
            ><label
              >币种<input
                v-model="form.currency"
                required
                minlength="3"
                maxlength="3" /></label
            ><label
              >报价截止（本地时区）<input
                v-model="form.deadline"
                type="datetime-local"
                step="1"
                required /></label
            ><label v-if="me.scope === 'ALL'"
              >所属部门<select
                v-model="form.departmentId"
                :disabled="!!dialog.id"
              >
                <option v-for="d in refs.departments" :key="d.id" :value="d.id">
                  {{ d.name }}
                </option>
              </select></label
            ><label class="wide"
              >需求与评审要求<textarea
                v-model="form.description"
                required
                maxlength="2000"
              ></textarea>
            </label>
          </div>
          <h3>需求明细</h3>
          <div class="table-wrap">
            <table class="edit-table">
              <thead>
                <tr>
                  <th>编码</th>
                  <th>名称</th>
                  <th>规格</th>
                  <th>数量</th>
                  <th>单位</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(l, i) in form.lines" :key="i">
                  <td>
                    <input
                      v-model="l.itemCode"
                      aria-label="物料编码"
                      required
                      maxlength="60"
                    />
                  </td>
                  <td>
                    <input
                      v-model="l.name"
                      aria-label="物料名称"
                      required
                      maxlength="200"
                    />
                  </td>
                  <td>
                    <input
                      v-model="l.specification"
                      aria-label="规格"
                      required
                      maxlength="1000"
                    />
                  </td>
                  <td>
                    <input
                      v-model.number="l.quantity"
                      aria-label="数量"
                      type="number"
                      min="0.001"
                      step="0.001"
                      required
                    />
                  </td>
                  <td>
                    <input
                      v-model="l.unit"
                      aria-label="单位"
                      required
                      maxlength="30"
                    />
                  </td>
                  <td>
                    <button
                      type="button"
                      :disabled="form.lines.length === 1"
                      @click="form.lines.splice(i, 1)"
                    >
                      移除
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <button
            type="button"
            :disabled="form.lines.length >= 100"
            @click="
              form.lines.push({
                itemCode: '',
                name: '',
                specification: '',
                quantity: 1,
                unit: '件',
              })
            "
          >
            添加需求行
          </button>
          <h3>邀请供应商</h3>
          <div class="checks">
            <label
              v-for="s in suppliers.filter(
                (s) =>
                  s.departmentId === form.departmentId &&
                  s.status === 'APPROVED' &&
                  s.enabled,
              )"
              :key="s.id"
              class="check"
              ><input
                v-model="form.supplierIds"
                type="checkbox"
                :value="s.id"
              />{{ s.name }} · {{ s.validUntil }}</label
            >
          </div>
          <p class="muted">
            仅可邀请本部门已准入且资质未到期的供应商。
          </p></template
        >
        <template v-if="dialog.type === 'quote'"
          ><div class="form-grid">
            <label
              >交货周期（天）<input
                v-model.number="form.leadDays"
                type="number"
                min="0"
                max="3650"
                required /></label
            ><label
              >运费（{{ detail.rfq.currency }}，含税）<input
                v-model.number="form.freight"
                type="number"
                min="0"
                step="0.01"
                required /></label
            ><label
              >报价有效期（本地时区）<input
                v-model="form.validUntil"
                type="datetime-local"
                step="1"
                required /></label
            ><label class="wide"
              >付款、交货及其他条款<textarea
                v-model="form.terms"
                required
                maxlength="2000"
              ></textarea>
            </label>
          </div>
          <div class="table-wrap">
            <table class="edit-table">
              <thead>
                <tr>
                  <th>物料 / 数量</th>
                  <th>未税单价</th>
                  <th>税率 %</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(l, i) in detail.lines" :key="l.id">
                  <td>
                    {{ l.name }} · {{ l.quantity }} {{ l.unit
                    }}<small>{{ l.specification }}</small>
                  </td>
                  <td>
                    <input
                      v-model.number="form.lines[i].unitPrice"
                      aria-label="未税单价"
                      type="number"
                      min="0"
                      step="0.0001"
                      required
                    />
                  </td>
                  <td>
                    <input
                      v-model.number="form.lines[i].taxRate"
                      aria-label="税率百分数"
                      type="number"
                      min="0"
                      max="100"
                      step="0.01"
                      required
                    />
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <p class="muted">
            新版报价替代此前有效版本，历史版本保留；截止后无法重报或撤回。
          </p></template
        >
        <template v-if="dialog.type === 'admin'"
          ><div class="form-grid">
            <label
              v-for="[key, label, type] in adminFields[dialog.resource]"
              :key="key"
              :class="{
                wide: type === 'permissions',
                check: type === 'checkbox',
              }"
              >{{ label
              }}<input
                v-if="type === 'checkbox'"
                v-model="form[key]"
                type="checkbox" /><select
                v-else-if="type === 'scope'"
                v-model="form[key]"
              >
                <option value="ALL">全部部门</option>
                <option value="DEPARTMENT">本部门</option>
                <option value="SUPPLIER">绑定供应商</option>
              </select>
              <div v-else-if="type === 'permissions'" class="checks">
                <label v-for="p in refs.permissions" :key="p.id" class="check"
                  ><input
                    v-model="form.permissions"
                    type="checkbox"
                    :value="p.code"
                  />{{ p.name }}</label
                >
              </div>
              <select
                v-else-if="
                  [
                    'roles',
                    'departments',
                    'suppliers',
                    'permissionCode',
                  ].includes(type)
                "
                v-model="form[key]"
              >
                <option v-if="type === 'suppliers'" :value="null">
                  不绑定（内部角色）
                </option>
                <option
                  v-for="r in refs[
                    type === 'permissionCode' ? 'permissions' : type
                  ]"
                  :key="r.id"
                  :value="type === 'permissionCode' ? r.code : r.id"
                >
                  {{ r.name }}
                </option></select
              ><input
                v-else
                v-model="form[key]"
                :type="
                  type === 'password'
                    ? 'password'
                    : type === 'number'
                      ? 'number'
                      : 'text'
                "
                :disabled="type === 'readonly'"
                :required="type !== 'password' || !dialog.id"
                :autocomplete="type === 'password' ? 'new-password' : 'off'"
                maxlength="200"
            /></label>
          </div>
          <p v-if="dialog.resource === 'users'" class="muted">
            修改账号时留空密码可保留原密码；供应商角色必须绑定同部门供应商。
          </p>
          <p v-if="dialog.resource === 'roles'" class="muted">
            供应商范围仅允许“供应商门户”权限；内部角色不分配门户权限。
          </p></template
        >
        <template v-if="dialog.type === 'command' || dialog.type === 'review'"
          ><p>
            {{
              dialog.type === "review"
                ? "核验资质后填写审核意见"
                : {
                    submit: "提交询价发布审批，审批通过后冻结需求与邀请。",
                    approve: "批准发布询价，提交人与审批人必须不同。",
                    reject: "退回询价修改。",
                    close: "截止时间后封标，封标后才能查看报价。",
                    cancel: "取消此询价，已有报价和审计记录保留。",
                    withdraw: "撤回当前报价版本，截止前可重新提交。",
                    select: "选择报价，提交定标审批。",
                    award: "批准定标并冻结采购建议，提交人与审批人必须不同。",
                    "reject-award": "退回采购专员重新比价。",
                  }[dialog.action]
            }}
          </p>
          <label v-if="!['submit', 'close', 'withdraw'].includes(dialog.action)"
            >{{
              dialog.action === "select"
                ? "选择理由（价格、交期、质量等依据）"
                : "审核 / 取消意见"
            }}<textarea
              v-model="form.note"
              required
              :maxlength="dialog.action === 'select' ? 2000 : 1000"
            ></textarea></label
          ><label v-if="dialog.action === 'select'"
            >有效报价不足时的例外说明<textarea
              v-model="form.exceptionReason"
              maxlength="1000"
            ></textarea></label
        ></template>
        <p v-if="dialog.type === 'delete'">
          确认删除此记录？已被业务引用的管理资源不能删除，询价只能删除草稿。
        </p>
        <template v-if="dialog.type === 'password'"
          ><label
            >原密码<input
              v-model="form.oldPassword"
              type="password"
              required
              autocomplete="current-password" /></label
          ><label
            >新密码<input
              v-model="form.newPassword"
              type="password"
              required
              minlength="12"
              maxlength="72"
              autocomplete="new-password"
          /></label>
          <p class="muted">
            至少12位，含大小写字母与数字。修改后需要重新登录。
          </p></template
        >
        <div v-if="error" class="alert error" role="alert">{{ error }}</div>
        <div class="modal-actions">
          <button type="button" :disabled="busy" @click="dialog = null">
            取消</button
          ><button class="primary" :disabled="busy">
            {{ busy ? "正在提交…" : "确认提交" }}
          </button>
        </div>
      </form>
    </section>
  </div>
</template>
