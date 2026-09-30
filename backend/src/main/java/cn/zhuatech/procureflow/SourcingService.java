// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.procureflow;

import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** 准入、密封询价、不可变报价版本与独立定标审批。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class SourcingService {
  final Store db;
  final AccessService access;
  final Clock clock;
  final JsonMapper json = JsonMapper.builder().findAndAddModules().build();

  public SourcingService(Store db, AccessService access, Clock clock) {
    this.db = db;
    this.access = access;
    this.clock = clock;
  }

  /** 需求行输入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Item(
      String itemCode, String name, String specification, String unit, BigDecimal quantity) {}

  /** 草稿输入，版本号防止覆盖旧页面。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Draft(
      Long version,
      String title,
      String description,
      String category,
      String currency,
      Long departmentId,
      Instant deadline,
      List<Item> lines,
      List<Long> supplierIds) {}

  /** 报价行输入，价格未税，税率为百分数。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Price(Long rfqLineId, BigDecimal unitPrice, BigDecimal taxRate) {}

  /** 幂等状态命令及完整报价。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Command(
      Long version,
      String requestKey,
      String note,
      Long quoteId,
      String exceptionReason,
      Integer leadDays,
      Instant validUntil,
      BigDecimal freight,
      String terms,
      List<Price> lines) {}

  /** 供应商档案与有效期声明；并不自动核实真实性。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record SupplierInput(
      Long version,
      String code,
      String name,
      String category,
      String contact,
      String qualification,
      LocalDate validUntil,
      Boolean enabled) {}

  /** 返回范围内供应商或本人绑定的档案。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<Supplier> suppliers() {
    if (portal()) {
      access.require("portal");
      return List.of(ownSupplier());
    }
    access.require("supplier.read");
    return db.all(Supplier.class).stream().filter(s -> access.visible(s.departmentId)).toList();
  }

  /** 新建、修改或停用档案；修改资质重新提交准入审核，保留被引用历史。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Supplier saveSupplier(Long id, SupplierInput v) {
    boolean external = portal();
    if (external) {
      access.require("portal");
      if (!Objects.equals(id, ownSupplier().id)) throw new Problem(403, "OUT_OF_SCOPE");
    } else access.require("supplier.write");
    Long dept =
        id == null ? access.current().departmentId : db.get(Supplier.class, id).departmentId;
    if (!external) access.department(dept);
    db.lock(Department.class, dept);
    var s = id == null ? new Supplier() : db.lock(Supplier.class, id);
    if (id != null) version(s.version, v.version);
    if (id == null) {
      s.code = text(v.code, 60);
      s.name = text(v.name, 120);
      s.departmentId = dept;
      s.enabled = true;
    } else if (!external) {
      s.name = text(v.name, 120);
      s.enabled = Boolean.TRUE.equals(v.enabled);
    }
    s.category = category(v.category);
    s.contact = text(v.contact, 200);
    s.qualification = text(v.qualification, 2000);
    if (v.validUntil == null || !v.validUntil.isAfter(LocalDate.now(clock)))
      throw new Problem(400, "QUALIFICATION_EXPIRED");
    s.validUntil = v.validUntil;
    s.status = "PENDING";
    s.submittedBy = access.current().username;
    s.reviewNote = "";
    if (id == null) db.save(s);
    access.audit("SUPPLIER_SUBMIT", s.id, dept);
    return s;
  }

  /** 审核人不能审核自己提交的资质，检查到期与启用状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Supplier reviewSupplier(Long id, boolean approve, Command c) {
    access.require("supplier.review");
    var prior = db.get(Supplier.class, id);
    access.department(prior.departmentId);
    db.lock(Department.class, prior.departmentId);
    var s = db.lock(Supplier.class, id);
    version(s.version, c.version);
    if (!s.status.equals("PENDING")) throw new Problem(409, "INVALID_STATE");
    independent(s.submittedBy);
    s.reviewNote = text(c.note, 1000);
    if (approve && (!s.enabled || !s.validUntil.isAfter(LocalDate.now(clock))))
      throw new Problem(409, "SUPPLIER_INELIGIBLE");
    s.status = approve ? "APPROVED" : "REJECTED";
    access.audit("SUPPLIER_" + s.status, id, s.departmentId);
    return s;
  }

  /** 建立或更新草稿，发布后不可改变邀请与需求；删除只允许草稿。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> saveDraft(Long id, Draft v) {
    access.require("rfq.write");
    Long dept = id == null ? v.departmentId : db.get(Rfq.class, id).departmentId;
    access.department(dept);
    db.lock(Department.class, dept);
    var r = id == null ? new Rfq() : db.lock(Rfq.class, id);
    if (id != null) {
      version(r.version, v.version);
      state(r, "DRAFT", "RETURNED");
      db.query(RfqLine.class, "from RfqLine where rfqId=?1", id).forEach(db::delete);
      db.query(Invitation.class, "from Invitation where rfqId=?1", id).forEach(db::delete);
    }
    if (v.deadline == null
        || !v.deadline.isAfter(clock.instant())
        || v.deadline.isAfter(clock.instant().plus(Duration.ofDays(180))))
      throw new Problem(400, "INVALID_DEADLINE");
    if (v.lines == null
        || v.lines.isEmpty()
        || v.lines.size() > 100
        || v.supplierIds == null
        || v.supplierIds.isEmpty()
        || v.supplierIds.size() > 100
        || new HashSet<>(v.supplierIds).size() != v.supplierIds.size())
      throw new Problem(400, "INVALID_LINES");
    r.title = text(v.title, 200);
    r.description = text(v.description, 2000);
    r.category = category(v.category);
    r.currency = currency(v.currency);
    r.deadline = v.deadline;
    r.status = "DRAFT";
    r.reviewNote = "";
    if (id == null) {
      r.number = "RFQ-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT);
      r.departmentId = dept;
      r.createdBy = access.current().username;
      r.createdAt = clock.instant();
      r.selectionReason = "";
      r.exceptionReason = "";
      r.selectedBy = "";
      r.approvedBy = "";
      r.submittedBy = "";
      r.minimumQuotes = Integer.parseInt(setting("minimumQuotes"));
      db.save(r);
    }
    var seen = new HashSet<String>();
    for (var i : v.lines) {
      var l = new RfqLine();
      l.rfqId = r.id;
      l.itemCode = text(i.itemCode, 60);
      if (!seen.add(l.itemCode)) throw new Problem(400, "DUPLICATE_ITEM");
      l.name = text(i.name, 200);
      l.specification = text(i.specification, 1000);
      l.unit = text(i.unit, 30);
      l.quantity = QuoteMath.decimal(i.quantity, 3, true);
      db.save(l);
    }
    for (var sid : v.supplierIds) {
      var s = db.get(Supplier.class, sid);
      if (!s.departmentId.equals(dept) || !eligible(s))
        throw new Problem(400, "SUPPLIER_INELIGIBLE");
      var i = new Invitation();
      i.rfqId = r.id;
      i.supplierId = s.id;
      i.supplierName = s.name;
      db.save(i);
    }
    access.audit("RFQ_DRAFT", r.id, dept);
    db.flush();
    return detail(r.id);
  }

  /** 删除未提交且无报价的草稿，外键保留历史单据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void deleteDraft(Long id, Long version) {
    access.require("rfq.write");
    var r = internal(id);
    db.lock(Department.class, r.departmentId);
    r = db.lock(Rfq.class, id);
    version(r.version, version);
    state(r, "DRAFT", "RETURNED");
    db.query(MutationStamp.class, "from MutationStamp where rfqId=?1", id).forEach(db::delete);
    db.query(Invitation.class, "from Invitation where rfqId=?1", id).forEach(db::delete);
    db.query(RfqLine.class, "from RfqLine where rfqId=?1", id).forEach(db::delete);
    access.audit("RFQ_DELETE", id, r.departmentId);
    db.delete(r);
  }

  /** 统一状态命令；部门行锁与幂等键序列化状态及报价版本，重复命令不重复执行。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> act(Long id, String action, Command c) {
    Rfq before = portal() ? invited(id) : internal(id);
    access.require(
        Set.of("quote", "withdraw").contains(action)
            ? "portal"
            : Set.of("approve", "reject", "award", "reject-award").contains(action)
                ? "rfq.approve"
                : "rfq.write");
    db.lock(Department.class, before.departmentId);
    var r = db.lock(Rfq.class, id);
    String key = text(c.requestKey, 80);
    String actor = access.current().username;
    String fp = fingerprint(action, c);
    var stamps =
        db.query(
            MutationStamp.class,
            "from MutationStamp where rfqId=?1 and actor=?2 and requestKey=?3",
            id,
            actor,
            key);
    if (!stamps.isEmpty()) {
      if (!stamps.getFirst().fingerprint.equals(fp)) throw new Problem(409, "IDEMPOTENCY_CONFLICT");
      return detail(id);
    }
    if (!Set.of("quote", "withdraw").contains(action)) version(r.version, c.version);
    switch (action) {
      case "submit" -> {
        state(r, "DRAFT", "RETURNED");
        future(r);
        r.minimumQuotes = Integer.parseInt(setting("minimumQuotes"));
        if (invitations(id).size() < r.minimumQuotes)
          throw new Problem(409, "INSUFFICIENT_INVITATIONS");
        r.submittedBy = actor;
        r.status = "PENDING_APPROVAL";
      }
      case "approve" -> {
        state(r, "PENDING_APPROVAL");
        independent(r.submittedBy);
        future(r);
        for (var i : invitations(id))
          if (!eligible(db.get(Supplier.class, i.supplierId)))
            throw new Problem(409, "SUPPLIER_INELIGIBLE");
        r.reviewNote = text(c.note, 1000);
        r.status = "OPEN";
      }
      case "reject" -> {
        state(r, "PENDING_APPROVAL");
        independent(r.submittedBy);
        r.reviewNote = text(c.note, 1000);
        r.status = "RETURNED";
      }
      case "close" -> {
        state(r, "OPEN");
        if (clock.instant().isBefore(r.deadline)) throw new Problem(409, "BEFORE_DEADLINE");
        r.status = "CLOSED";
      }
      case "cancel" -> {
        state(r, "DRAFT", "RETURNED", "PENDING_APPROVAL", "OPEN", "CLOSED");
        r.reviewNote = text(c.note, 1000);
        r.status = "CANCELLED";
      }
      case "quote" -> quote(r, c);
      case "withdraw" -> {
        state(r, "OPEN");
        future(r);
        Long sid = ownSupplier().id;
        var q = latest(r.id, sid);
        if (q == null || !q.status.equals("SUBMITTED")) throw new Problem(409, "NO_QUOTE");
        q.status = "WITHDRAWN";
      }
      case "select" -> {
        state(r, "CLOSED");
        var q = db.get(Quote.class, c.quoteId);
        validQuote(r, q);
        long count = quotes(r.id).stream().filter(x -> valid(x)).count();
        r.exceptionReason = count < r.minimumQuotes ? text(c.exceptionReason, 1000) : "";
        r.selectedQuoteId = q.id;
        r.selectionReason = text(c.note, 2000);
        r.selectedBy = actor;
        r.status = "PENDING_AWARD";
      }
      case "reject-award" -> {
        state(r, "PENDING_AWARD");
        independent(r.selectedBy);
        r.reviewNote = text(c.note, 1000);
        r.status = "CLOSED";
      }
      case "award" -> {
        state(r, "PENDING_AWARD");
        independent(r.selectedBy);
        var q = db.get(Quote.class, r.selectedQuoteId);
        validQuote(r, q);
        long count = quotes(r.id).stream().filter(x -> valid(x)).count();
        if (count < r.minimumQuotes && r.exceptionReason.isBlank())
          throw new Problem(409, "INSUFFICIENT_QUOTES");
        r.reviewNote = text(c.note, 1000);
        r.approvedBy = actor;
        r.awardedAt = clock.instant();
        r.status = "AWARDED";
        r.awardSnapshot = serialize(snapshot(r, q));
      }
      default -> throw new Problem(404, "UNKNOWN_ACTION");
    }
    var stamp = new MutationStamp();
    stamp.rfqId = id;
    stamp.actor = actor;
    stamp.requestKey = key;
    stamp.fingerprint = fp;
    db.save(stamp);
    access.audit("RFQ_" + action.toUpperCase(Locale.ROOT), id, r.departmentId);
    db.flush();
    return detail(id);
  }

  /** 安全详情：供应商仅见自己的报价，采购方截止并封标后才能读取价格。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> detail(Long id) {
    boolean external = portal();
    var r = external ? invited(id) : internal(id);
    if (external) access.require("portal");
    else access.require("rfq.read");
    var result = new LinkedHashMap<String, Object>();
    result.put("rfq", r);
    result.put("lines", lines(id));
    if (external) {
      result.put(
          "quotes",
          quotes(id).stream()
              .filter(q -> q.supplierId.equals(ownSupplier().id))
              .map(q -> quoteView(q, true))
              .toList());
      result.put("supplier", ownSupplier());
    } else {
      result.put("invitations", invitations(id));
      boolean sealed = !Set.of("CLOSED", "PENDING_AWARD", "AWARDED").contains(r.status);
      result.put("sealed", sealed);
      result.put("quotes", quotes(id).stream().map(q -> quoteView(q, !sealed)).toList());
    }
    // Supplier response deliberately excludes internal evaluation, approver identities and reasons.
    if (external) {
      var safe = new LinkedHashMap<String, Object>();
      safe.put("id", r.id);
      safe.put("number", r.number);
      safe.put("title", r.title);
      safe.put("description", r.description);
      safe.put("currency", r.currency);
      safe.put("deadline", r.deadline);
      safe.put("status", r.status);
      safe.put("category", r.category);
      safe.put(
          "won",
          r.status.equals("AWARDED")
              && db.get(Quote.class, r.selectedQuoteId).supplierId.equals(ownSupplier().id));
      result.put("rfq", safe);
    }
    return result;
  }

  /** 搜索筛选、页码和白名单排序；数据库分页，供应商只见受邀已发布记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> list(String search, String status, int page, int size, String sort) {
    boolean external = portal();
    access.require(external ? "portal" : "rfq.read");
    if (page < 0 || page > 100000 || size < 1 || size > 100 || search.length() > 200)
      throw new Problem(400, "INVALID_PAGE");
    String where = " where lower(r.title) like :search";
    var params = new LinkedHashMap<String, Object>();
    params.put("search", "%" + search.toLowerCase(Locale.ROOT) + "%");
    if (!status.isBlank()) {
      where += " and r.status=:status";
      params.put("status", status);
    }
    if (external) {
      where +=
          " and r.status in ('OPEN','CLOSED','PENDING_AWARD','AWARDED','CANCELLED') and exists (select i.id from Invitation i where i.rfqId=r.id and i.supplierId=:supplier)";
      params.put("supplier", ownSupplier().id);
    } else if (!access.role().scope.equals("ALL")) {
      where += " and r.departmentId=:dept";
      params.put("dept", access.current().departmentId);
    }
    String order =
        switch (sort) {
          case "deadline" -> "r.deadline asc,r.id desc";
          case "oldest" -> "r.id asc";
          case "newest" -> "r.id desc";
          default -> throw new Problem(400, "INVALID_SORT");
        };
    var q = db.jpql(Long.class, "select r.id from Rfq r" + where + " order by " + order);
    var count = db.jpql(Long.class, "select count(r.id) from Rfq r" + where);
    params.forEach(
        (k, v) -> {
          q.setParameter(k, v);
          count.setParameter(k, v);
        });
    var items =
        q.setFirstResult(page * size).setMaxResults(size).getResultList().stream()
            .map(id -> detail(id).get("rfq"))
            .toList();
    return Map.of("items", items, "total", count.getSingleResult(), "page", page, "size", size);
  }

  /** 按币种分别汇总已批准的采购建议，金额不是付款或收入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> dashboard() {
    access.require("dashboard");
    String clause = access.role().scope.equals("ALL") ? "" : " where r.departmentId=?1";
    var q = db.jpql(Rfq.class, "from Rfq r" + clause);
    if (!clause.isEmpty()) q.setParameter(1, access.current().departmentId);
    var rows = q.getResultList();
    var counts = new TreeMap<String, Long>();
    var amounts = new TreeMap<String, BigDecimal>();
    for (var r : rows) {
      counts.merge(r.status, 1L, Long::sum);
      if (r.status.equals("AWARDED"))
        amounts.merge(
            r.currency, db.get(Quote.class, r.selectedQuoteId).grossTotal, BigDecimal::add);
    }
    return Map.of(
        "states",
        counts,
        "awardedAmounts",
        amounts,
        "overdueOpen",
        rows.stream()
            .filter(r -> r.status.equals("OPEN") && !r.deadline.isAfter(clock.instant()))
            .count(),
        "suppliers",
        db.all(Supplier.class).stream().filter(s -> access.visible(s.departmentId)).count());
  }

  /** 导出冻结的版本化采购建议，不创建订单、库存或财务记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public String export(Long id) {
    access.require("export");
    var r = internal(id);
    state(r, "AWARDED");
    access.audit("PROCUREMENT_ADVICE_EXPORT", id, r.departmentId);
    return r.awardSnapshot;
  }

  /** 返回当前范围内无敏感载荷的操作和审批记录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<AuditEvent> audit() {
    access.require("audit");
    return db.all(AuditEvent.class).stream()
        .filter(a -> access.visible(a.departmentId))
        .sorted(Comparator.comparing((AuditEvent a) -> a.id).reversed())
        .toList();
  }

  /** 读取有效字典和设置，供表单与显示使用。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> options() {
    access.current();
    return Map.of(
        "categories",
        db.all(DictionaryEntry.class).stream()
            .filter(d -> d.type.equals("sourcingCategory"))
            .toList(),
        "settings",
        db.all(SystemSetting.class));
  }

  /** 完整逐行校验并生成不可变报价版本，原版本仅变更状态。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  private void quote(Rfq r, Command c) {
    state(r, "OPEN");
    future(r);
    var supplier = ownSupplier();
    if (!eligible(supplier)) throw new Problem(409, "SUPPLIER_INELIGIBLE");
    if (c.validUntil == null
        || !c.validUntil.isAfter(r.deadline)
        || c.validUntil.isAfter(clock.instant().plus(Duration.ofDays(365))))
      throw new Problem(400, "INVALID_QUOTE_VALIDITY");
    if (c.leadDays == null || c.leadDays < 0 || c.leadDays > 3650)
      throw new Problem(400, "INVALID_LEAD_DAYS");
    var expected = lines(r.id);
    if (c.lines == null || c.lines.size() != expected.size())
      throw new Problem(400, "INCOMPLETE_QUOTE");
    var byId = new HashMap<Long, Price>();
    for (var p : c.lines)
      if (p.rfqLineId == null || byId.put(p.rfqLineId, p) != null)
        throw new Problem(400, "DUPLICATE_ITEM");
    var old = latest(r.id, supplier.id);
    if (old != null && old.status.equals("SUBMITTED")) old.status = "SUPERSEDED";
    var q = new Quote();
    q.rfqId = r.id;
    q.supplierId = supplier.id;
    q.supplierName = supplier.name;
    q.revision = old == null ? 1 : old.revision + 1;
    q.status = "SUBMITTED";
    q.validUntil = c.validUntil;
    q.leadDays = c.leadDays;
    q.terms = text(c.terms, 2000);
    q.freight = QuoteMath.decimal(c.freight, 2, false);
    q.netTotal = BigDecimal.ZERO.setScale(2);
    q.taxTotal = BigDecimal.ZERO.setScale(2);
    q.submittedAt = clock.instant();
    q.submittedBy = access.current().username;
    q.grossTotal = BigDecimal.ZERO.setScale(2);
    db.save(q);
    for (var i : expected) {
      var p = byId.get(i.id);
      if (p == null) throw new Problem(400, "INCOMPLETE_QUOTE");
      var l = new QuoteLine();
      l.quoteId = q.id;
      l.rfqLineId = i.id;
      l.unitPrice = QuoteMath.decimal(p.unitPrice, 4, false);
      l.taxRate = QuoteMath.decimal(p.taxRate, 2, false);
      if (l.taxRate.compareTo(BigDecimal.valueOf(100)) > 0) throw new Problem(400, "INVALID_TAX");
      l.netAmount = QuoteMath.net(i.quantity, l.unitPrice);
      l.taxAmount = QuoteMath.tax(l.netAmount, l.taxRate);
      q.netTotal = q.netTotal.add(l.netAmount);
      q.taxTotal = q.taxTotal.add(l.taxAmount);
      db.save(l);
    }
    q.grossTotal = q.netTotal.add(q.taxTotal).add(q.freight);
    if (q.grossTotal.compareTo(new BigDecimal("99999999999999.99")) > 0)
      throw new Problem(400, "AMOUNT_TOO_LARGE");
  }

  /** 按密封与供应商范围构造安全报价响应。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  private Map<String, Object> quoteView(Quote q, boolean prices) {
    var view = new LinkedHashMap<String, Object>();
    view.put("id", q.id);
    view.put("supplierId", q.supplierId);
    view.put("supplierName", q.supplierName);
    view.put("revision", q.revision);
    view.put("status", q.status);
    view.put("submittedAt", q.submittedAt);
    if (prices) {
      view.put("quote", q);
      view.put(
          "lines", db.query(QuoteLine.class, "from QuoteLine where quoteId=?1 order by id", q.id));
      view.put("eligible", valid(q));
    }
    return view;
  }

  /** 在定标事务内固化采购建议，不附加广告或重算旧报价。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  private Map<String, Object> snapshot(Rfq r, Quote q) {
    var result = new LinkedHashMap<String, Object>();
    result.put("schemaVersion", "1.0");
    result.put("kind", "PROCUREMENT_ADVICE");
    result.put("sourceSystem", "procureflow");
    result.put("adviceId", r.number);
    result.put("rfqId", r.id);
    result.put("quoteId", q.id);
    result.put("quoteRevision", q.revision);
    result.put(
        "supplier",
        Map.of("code", db.get(Supplier.class, q.supplierId).code, "name", q.supplierName));
    result.put("currency", r.currency);
    result.put("leadDays", q.leadDays);
    result.put("terms", q.terms);
    result.put("freight", q.freight);
    result.put("netTotal", q.netTotal);
    result.put("taxTotal", q.taxTotal);
    result.put("grossTotal", q.grossTotal);
    result.put("approvedAt", r.awardedAt.toString());
    result.put("approvedBy", r.approvedBy);
    result.put("selectionReason", r.selectionReason);
    result.put("exceptionReason", r.exceptionReason);
    var prices = db.query(QuoteLine.class, "from QuoteLine where quoteId=?1", q.id);
    var items = new ArrayList<Map<String, Object>>();
    for (var l : lines(r.id)) {
      var p = prices.stream().filter(x -> x.rfqLineId.equals(l.id)).findFirst().orElseThrow();
      items.add(
          Map.of(
              "itemCode",
              l.itemCode,
              "name",
              l.name,
              "specification",
              l.specification,
              "unit",
              l.unit,
              "quantity",
              l.quantity,
              "unitPrice",
              p.unitPrice,
              "taxRate",
              p.taxRate,
              "netAmount",
              p.netAmount,
              "taxAmount",
              p.taxAmount));
    }
    result.put("lines", items);
    return result;
  }

  private boolean portal() {
    return "SUPPLIER".equals(access.role().scope);
  }

  private Supplier ownSupplier() {
    var a = access.current();
    if (a.supplierId == null || !portal()) throw new Problem(403, "SUPPLIER_ACCOUNT_REQUIRED");
    return db.get(Supplier.class, a.supplierId);
  }

  private Rfq internal(Long id) {
    if (portal()) throw new Problem(403, "FORBIDDEN");
    var r = db.get(Rfq.class, id);
    access.department(r.departmentId);
    return r;
  }

  private Rfq invited(Long id) {
    var r = db.get(Rfq.class, id);
    if (Set.of("DRAFT", "RETURNED", "PENDING_APPROVAL").contains(r.status)
        || db.query(
                Invitation.class,
                "from Invitation where rfqId=?1 and supplierId=?2",
                id,
                ownSupplier().id)
            .isEmpty()) throw new Problem(403, "OUT_OF_SCOPE");
    return r;
  }

  private boolean eligible(Supplier s) {
    return s.enabled
        && s.status.equals("APPROVED")
        && s.validUntil != null
        && s.validUntil.isAfter(LocalDate.now(clock));
  }

  private boolean valid(Quote q) {
    return q.status.equals("SUBMITTED")
        && q.validUntil.isAfter(clock.instant())
        && eligible(db.get(Supplier.class, q.supplierId));
  }

  private void validQuote(Rfq r, Quote q) {
    if (!q.rfqId.equals(r.id) || !valid(q)) throw new Problem(409, "QUOTE_INELIGIBLE");
  }

  private Quote latest(Long id, Long sid) {
    return db
        .query(
            Quote.class,
            "from Quote where rfqId=?1 and supplierId=?2 order by revision desc",
            id,
            sid)
        .stream()
        .findFirst()
        .orElse(null);
  }

  private List<Quote> quotes(Long id) {
    return db.query(Quote.class, "from Quote where rfqId=?1 order by supplierId,revision desc", id);
  }

  private List<Invitation> invitations(Long id) {
    return db.query(Invitation.class, "from Invitation where rfqId=?1 order by id", id);
  }

  private List<RfqLine> lines(Long id) {
    return db.query(RfqLine.class, "from RfqLine where rfqId=?1 order by id", id);
  }

  private void future(Rfq r) {
    if (!r.deadline.isAfter(clock.instant())) throw new Problem(409, "DEADLINE_PASSED");
  }

  private void independent(String author) {
    if (author.equals(access.current().username)) throw new Problem(403, "SELF_APPROVAL");
  }

  private void state(Rfq r, String... allowed) {
    if (!Set.of(allowed).contains(r.status)) throw new Problem(409, "INVALID_STATE");
  }

  private void version(long current, Long given) {
    if (given == null || current != given) throw new Problem(409, "STALE_VERSION");
  }

  private String category(String code) {
    var result = text(code, 60);
    if (db.query(
            DictionaryEntry.class,
            "from DictionaryEntry where type='sourcingCategory' and code=?1",
            result)
        .isEmpty()) throw new Problem(400, "INVALID_CATEGORY");
    return result;
  }

  private String currency(String code) {
    Currency c = Currency.getInstance(code);
    if (c.getDefaultFractionDigits() != 2) throw new Problem(400, "UNSUPPORTED_CURRENCY");
    return code;
  }

  private String setting(String code) {
    return db.query(SystemSetting.class, "from SystemSetting where code=?1", code).getFirst().value;
  }

  private String text(String s, int max) {
    return AdminService.text(s, max);
  }

  private String serialize(Object v) {
    try {
      return json.writeValueAsString(v);
    } catch (Exception e) {
      throw new IllegalStateException("SERIALIZATION_FAILED");
    }
  }

  /** 对动作和完整命令生成摘要，识别同键不同载荷。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  private String fingerprint(String action, Command c) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest((action + serialize(c)).getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("HASH_FAILED");
    }
  }
}
