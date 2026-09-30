// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.procureflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 真实HTTP、Flyway、权限、密封报价、审批和并发幂等验收。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import(SourcingIntegrationTest.TimeConfig.class)
class SourcingIntegrationTest {
  static final String password = "Aa9" + UUID.randomUUID();

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("procureflow.admin-password", () -> password);
  }

  /** 每测试独立推进时钟，生产不改UTC时钟。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @org.springframework.boot.test.context.TestConfiguration
  static class TimeConfig {
    @org.springframework.context.annotation.Bean
    @org.springframework.context.annotation.Primary
    TestClock testClock() {
      return new TestClock();
    }
  }

  /** 可推进的时钟。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  static class TestClock extends Clock {
    volatile Instant now = Instant.now();

    @Override
    public Instant instant() {
      return now;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId z) {
      return this;
    }
  }

  @Autowired MockMvc mvc;
  @Autowired TestClock clock;
  final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
  MockHttpSession admin, buyer, manager, s1, s2;
  long dept, sid1, sid2, rfq;
  String buyerName;
  JsonNode detail;

  @BeforeEach
  void setup() throws Exception {
    clock.now = Instant.now();
    admin = login("admin", password);
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    dept =
        ok(admin, "POST", "/admin/departments", Map.of("name", "验收部门-" + suffix))
            .path("id")
            .asLong();
    var roles = ok(admin, "GET", "/admin/roles", null);
    long br = 0, mr = 0, sr = 0;
    for (var r : roles) {
      switch (r.path("name").asString()) {
        case "采购专员" -> br = r.path("id").asLong();
        case "审批负责人" -> mr = r.path("id").asLong();
        case "供应商" -> sr = r.path("id").asLong();
      }
    }
    buyerName = "buyer-" + suffix;
    buyer = user(buyerName, br, null);
    manager = user("review-" + suffix, mr, null);
    sid1 = supplier("A-" + suffix);
    sid2 = supplier("B-" + suffix);
    s1 = user("s1-" + suffix, sr, sid1);
    s2 = user("s2-" + suffix, sr, sid2);
    detail = ok(buyer, "POST", "/rfqs", draft());
    rfq = detail.path("rfq").path("id").asLong();
  }

  private MockHttpSession user(String name, long role, Long supplier) throws Exception {
    var input = new LinkedHashMap<String, Object>();
    input.put("username", name);
    input.put("displayName", name);
    input.put("password", password);
    input.put("departmentId", dept);
    input.put("roleId", role);
    input.put("supplierId", supplier);
    input.put("enabled", true);
    ok(admin, "POST", "/admin/users", input);
    return login(name, password);
  }

  private long supplier(String code) throws Exception {
    var v =
        ok(
            buyer,
            "POST",
            "/suppliers",
            Map.of(
                "code",
                code,
                "name",
                "验收供应商-" + code,
                "category",
                "设备配件",
                "contact",
                "qa@example.invalid",
                "qualification",
                "虚构验收资质",
                "validUntil",
                LocalDate.now(clock).plusYears(1).toString(),
                "enabled",
                true));
    long id = v.path("id").asLong();
    ok(
        manager,
        "POST",
        "/suppliers/" + id + "/approve",
        Map.of("version", v.path("version").asLong(), "note", "验收审核"));
    return id;
  }

  private Map<String, Object> draft() {
    return Map.of(
        "title",
        "验收询价",
        "description",
        "虚构数据，非商业交易",
        "category",
        "设备配件",
        "currency",
        "CNY",
        "departmentId",
        dept,
        "deadline",
        clock.now.plusSeconds(3600).toString(),
        "supplierIds",
        List.of(sid1, sid2),
        "lines",
        List.of(
            Map.of(
                "itemCode",
                "QA-01",
                "name",
                "验收配件",
                "specification",
                "虚构规格",
                "unit",
                "件",
                "quantity",
                3)));
  }

  private Map<String, Object> command() {
    return new LinkedHashMap<>(
        Map.of(
            "version",
            detail.path("rfq").path("version").asLong(),
            "requestKey",
            UUID.randomUUID().toString(),
            "note",
            "验收意见"));
  }

  private void action(MockHttpSession session, String action) throws Exception {
    detail = ok(session, "POST", "/rfqs/" + rfq + "/" + action, command());
  }

  private void open() throws Exception {
    action(buyer, "submit");
    action(manager, "approve");
  }

  private Map<String, Object> quote(String price) {
    return Map.of(
        "requestKey",
        UUID.randomUUID().toString(),
        "leadDays",
        7,
        "freight",
        5,
        "terms",
        "验收：交货后登记付款",
        "validUntil",
        clock.now.plusSeconds(86400).toString(),
        "lines",
        List.of(
            Map.of(
                "rfqLineId",
                detail.path("lines").get(0).path("id").asLong(),
                "unitPrice",
                price,
                "taxRate",
                13)));
  }

  private void closed() throws Exception {
    open();
    ok(s1, "POST", "/rfqs/" + rfq + "/quote", quote("3.335"));
    ok(s2, "POST", "/rfqs/" + rfq + "/quote", quote("5"));
    clock.now = clock.now.plusSeconds(3601);
    action(buyer, "close");
  }

  private Map<String, Object> selection() {
    var c = command();
    c.put("quoteId", detail.path("quotes").get(0).path("id").asLong());
    return c;
  }

  private MockHttpSession login(String u, String p) throws Exception {
    var r =
        mvc.perform(
                post("/api/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsString(Map.of("username", u, "password", p))))
            .andReturn();
    assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    return (MockHttpSession) r.getRequest().getSession();
  }

  private MvcResult request(
      MockHttpSession session, String method, String path, Object body, boolean token)
      throws Exception {
    var b =
        switch (method) {
          case "POST" -> post("/api" + path);
          case "PUT" -> put("/api" + path);
          case "DELETE" -> delete("/api" + path);
          default -> get("/api" + path);
        };
    if (session != null) b.session(session);
    if (token) b.with(csrf());
    if (body != null) b.contentType("application/json").content(json.writeValueAsString(body));
    return mvc.perform(b).andReturn();
  }

  private JsonNode ok(MockHttpSession s, String method, String path, Object body) throws Exception {
    var r = request(s, method, path, body, true);
    assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    return json.readTree(r.getResponse().getContentAsString());
  }

  private void denied(MockHttpSession s, String method, String path, Object body, int status)
      throws Exception {
    assertEquals(status, request(s, method, path, body, true).getResponse().getStatus());
  }

  @Test
  void completeBusinessAndFrozenExport() throws Exception {
    closed();
    detail = ok(buyer, "POST", "/rfqs/" + rfq + "/select", selection());
    action(manager, "award");
    var v = ok(buyer, "GET", "/rfqs/" + rfq + "/advice.json", null);
    assertEquals("PROCUREMENT_ADVICE", v.path("kind").asString());
    assertEquals(16.31, v.path("grossTotal").asDouble(), .001);
    assertEquals("1.0", v.path("schemaVersion").asString());
    assertFalse(v.has("wechat"));
    assertEquals("AWARDED", detail.path("rfq").path("status").asString());
    denied(buyer, "POST", "/rfqs/" + rfq + "/cancel", command(), 409);
  }

  @Test
  void csrfAndAnonymousProtection() throws Exception {
    denied(null, "GET", "/suppliers", null, 401);
    assertEquals(
        403,
        request(admin, "POST", "/admin/departments", Map.of("name", "bad"), false)
            .getResponse()
            .getStatus());
  }

  @Test
  void differentAuthorizedApproverAccepted() throws Exception {
    action(buyer, "submit");
    var c = command();
    ok(admin, "POST", "/rfqs/" + rfq + "/approve", c);
  }

  @Test
  void selfApprovalIsRejected() throws Exception {
    var r = ok(admin, "POST", "/rfqs", draft());
    long id = r.path("rfq").path("id").asLong();
    var c =
        new LinkedHashMap<String, Object>(
            Map.of(
                "version",
                r.path("rfq").path("version").asLong(),
                "requestKey",
                UUID.randomUUID().toString(),
                "note",
                "验收"));
    r = ok(admin, "POST", "/rfqs/" + id + "/submit", c);
    c.put("version", r.path("rfq").path("version").asLong());
    c.put("requestKey", UUID.randomUUID().toString());
    denied(admin, "POST", "/rfqs/" + id + "/approve", c, 403);
  }

  @Test
  void sealedPricesAndOwnSupplierScope() throws Exception {
    open();
    ok(s1, "POST", "/rfqs/" + rfq + "/quote", quote("10"));
    ok(s2, "POST", "/rfqs/" + rfq + "/quote", quote("20"));
    var internal = ok(buyer, "GET", "/rfqs/" + rfq, null);
    assertTrue(internal.path("sealed").asBoolean());
    assertFalse(internal.path("quotes").get(0).has("quote"));
    var external = ok(s1, "GET", "/rfqs/" + rfq, null);
    assertEquals(1, external.path("quotes").size());
    assertFalse(external.path("rfq").has("selectionReason"));
    denied(s1, "GET", "/admin/users", null, 403);
    denied(s1, "GET", "/dashboard", null, 403);
  }

  @Test
  void revisionImmutableAndRetryIdempotent() throws Exception {
    open();
    var first = quote("10");
    ok(s1, "POST", "/rfqs/" + rfq + "/quote", first);
    ok(s1, "POST", "/rfqs/" + rfq + "/quote", first);
    var r = ok(s1, "POST", "/rfqs/" + rfq + "/quote", quote("11"));
    assertEquals(2, r.path("quotes").size());
    assertEquals("SUPERSEDED", r.path("quotes").get(1).path("status").asString());
    assertEquals(10, r.path("quotes").get(1).path("quote").path("netTotal").asDouble() / 3, .001);
    var conflict = new LinkedHashMap<>(first);
    conflict.put("terms", "changed");
    denied(s1, "POST", "/rfqs/" + rfq + "/quote", conflict, 409);
  }

  @Test
  void deadlineAndCloseRules() throws Exception {
    open();
    denied(buyer, "POST", "/rfqs/" + rfq + "/close", command(), 409);
    clock.now = clock.now.plusSeconds(3600);
    denied(s1, "POST", "/rfqs/" + rfq + "/quote", quote("10"), 409);
    action(buyer, "close");
  }

  @Test
  void quoteCoverageAndPrecision() throws Exception {
    open();
    var input = new LinkedHashMap<>(quote("10"));
    input.put("lines", List.of());
    denied(s1, "POST", "/rfqs/" + rfq + "/quote", input, 400);
    denied(s1, "POST", "/rfqs/" + rfq + "/quote", quote("1.00001"), 400);
    assertEquals(0, ok(s1, "GET", "/rfqs/" + rfq, null).path("quotes").size());
  }

  @Test
  void draftVersionAndPublishedFreeze() throws Exception {
    var update = new LinkedHashMap<>(draft());
    update.put("version", -1);
    denied(buyer, "PUT", "/rfqs/" + rfq, update, 409);
    open();
    update.put("version", detail.path("rfq").path("version").asLong());
    denied(buyer, "PUT", "/rfqs/" + rfq, update, 409);
    denied(buyer, "DELETE", "/rfqs/" + rfq + "?version=" + update.get("version"), null, 409);
  }

  @Test
  void changedQualificationInvalidatesQuote() throws Exception {
    closed();
    var s = ok(s1, "GET", "/suppliers", null).get(0);
    var v = new LinkedHashMap<String, Object>();
    for (var key : List.of("code", "name", "category", "contact", "qualification", "validUntil"))
      v.put(key, s.path(key).asString());
    v.put("version", s.path("version").asLong());
    v.put("enabled", true);
    ok(s1, "PUT", "/suppliers/" + sid1, v);
    denied(buyer, "POST", "/rfqs/" + rfq + "/select", selection(), 409);
  }

  @Test
  void insufficientQuotesRequireException() throws Exception {
    open();
    ok(s1, "POST", "/rfqs/" + rfq + "/quote", quote("10"));
    clock.now = clock.now.plusSeconds(3601);
    action(buyer, "close");
    var c = selection();
    denied(buyer, "POST", "/rfqs/" + rfq + "/select", c, 400);
    c.put("exceptionReason", "验收：仅一家响应，另行评审批准");
    detail = ok(buyer, "POST", "/rfqs/" + rfq + "/select", c);
    action(manager, "award");
  }

  @Test
  void departmentAndInvitationIsolation() throws Exception {
    var r = ok(admin, "POST", "/rfqs", draft());
    long other = r.path("rfq").path("id").asLong();
    denied(s1, "GET", "/rfqs/" + other, null, 403);
    var dept2 =
        ok(admin, "POST", "/admin/departments", Map.of("name", "其他部门" + UUID.randomUUID()))
            .path("id")
            .asLong();
    var roles = ok(admin, "GET", "/admin/roles", null);
    long role = 0;
    for (var x : roles) if (x.path("name").asString().equals("采购专员")) role = x.path("id").asLong();
    long old = dept;
    dept = dept2;
    var outsider = user("other-" + UUID.randomUUID().toString().substring(0, 8), role, null);
    dept = old;
    denied(outsider, "GET", "/rfqs/" + rfq, null, 403);
    assertEquals(0, ok(outsider, "GET", "/rfqs", null).path("total").asLong());
  }

  @Test
  void concurrentRetriesCreateOneVersion() throws Exception {
    open();
    var c = quote("10");
    try (var executor = Executors.newFixedThreadPool(2)) {
      var tasks =
          List.<Callable<Integer>>of(
              () ->
                  request(s1, "POST", "/rfqs/" + rfq + "/quote", c, true).getResponse().getStatus(),
              () ->
                  request(s1, "POST", "/rfqs/" + rfq + "/quote", c, true)
                      .getResponse()
                      .getStatus());
      for (var f : executor.invokeAll(tasks)) assertEquals(200, f.get());
    }
    assertEquals(1, ok(s1, "GET", "/rfqs/" + rfq, null).path("quotes").size());
  }

  @Test
  void withdrawAndRequote() throws Exception {
    open();
    ok(s1, "POST", "/rfqs/" + rfq + "/quote", quote("10"));
    ok(s1, "POST", "/rfqs/" + rfq + "/withdraw", command());
    var r = ok(s1, "POST", "/rfqs/" + rfq + "/quote", quote("11"));
    assertEquals("WITHDRAWN", r.path("quotes").get(1).path("status").asString());
    assertEquals(2, r.path("quotes").get(0).path("revision").asInt());
  }

  @Test
  void managerReturnAndReapproval() throws Exception {
    action(buyer, "submit");
    action(manager, "reject");
    assertEquals("RETURNED", detail.path("rfq").path("status").asString());
    action(buyer, "submit");
    action(manager, "approve");
    clock.now = clock.now.plusSeconds(3601);
    action(buyer, "close");
  }

  @Test
  void cannotEscalateSupplierRole() throws Exception {
    var roles = ok(admin, "GET", "/admin/roles", null);
    for (var r : roles)
      if (r.path("scope").asString().equals("SUPPLIER")) {
        denied(
            admin,
            "PUT",
            "/admin/roles/" + r.path("id").asLong(),
            Map.of("name", "供应商", "scope", "SUPPLIER", "permissions", List.of("portal", "admin")),
            400);
      }
    denied(s1, "POST", "/rfqs", draft(), 403);
  }

  @Test
  void passwordChangeInvalidatesSession() throws Exception {
    String next = "Bb8" + UUID.randomUUID();
    ok(buyer, "POST", "/auth/password", Map.of("oldPassword", password, "newPassword", next));
    assertTrue(buyer.isInvalid());
    var s = login(buyerName, next);
    assertEquals(buyerName, ok(s, "GET", "/auth/me", null).path("username").asString());
  }

  @Test
  void safePaginationAndSorting() throws Exception {
    var r = ok(buyer, "GET", "/rfqs?search=验收&size=1&sort=deadline", null);
    assertEquals(1, r.path("items").size());
    denied(buyer, "GET", "/rfqs?sort=title%20desc", null, 400);
    denied(buyer, "GET", "/rfqs?size=1000", null, 400);
  }
}
