// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.procureflow;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 空库创建权限和随机外部密码管理员，不加入虚构业务。 官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Component
public class Bootstrap implements ApplicationRunner {
  final Store db;
  final BCryptPasswordEncoder encoder;
  final String password;

  public Bootstrap(
      Store db,
      BCryptPasswordEncoder encoder,
      @Value("${procureflow.admin-password}") String password) {
    this.db = db;
    this.encoder = encoder;
    this.password = password;
  }

  /** 仅空库初始化，重启不覆盖密码与业务资料。 官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!db.all(Account.class).isEmpty()) return;
    AdminService.validatePassword(password);
    var dept = new Department();
    dept.name = "总部";
    db.save(dept);
    var names =
        Map.ofEntries(
            Map.entry("dashboard", "寻源概况"),
            Map.entry("supplier.read", "查看供应商"),
            Map.entry("supplier.write", "维护供应商"),
            Map.entry("supplier.review", "准入审核"),
            Map.entry("rfq.read", "查看询价"),
            Map.entry("rfq.write", "编制询价与定标建议"),
            Map.entry("rfq.approve", "询价与定标审批"),
            Map.entry("export", "导出采购建议"),
            Map.entry("audit", "操作审计"),
            Map.entry("admin", "系统管理"),
            Map.entry("portal", "供应商门户"));
    for (var e : new TreeMap<>(names).entrySet()) {
      var p = new Permission();
      p.code = e.getKey();
      p.name = e.getValue();
      db.save(p);
    }
    var all = new HashSet<>(names.keySet());
    all.remove("portal");
    role("管理员", "ALL", all);
    role(
        "采购专员",
        "DEPARTMENT",
        Set.of("dashboard", "supplier.read", "supplier.write", "rfq.read", "rfq.write", "export"));
    role(
        "审批负责人",
        "DEPARTMENT",
        Set.of(
            "dashboard",
            "supplier.read",
            "supplier.review",
            "rfq.read",
            "rfq.approve",
            "export",
            "audit"));
    role("供应商", "SUPPLIER", Set.of("portal"));
    var a = new Account();
    a.username = "admin";
    a.displayName = "管理员";
    a.passwordHash = encoder.encode(password);
    a.departmentId = dept.id;
    a.roleId = db.all(AccessRole.class).getFirst().id;
    a.enabled = true;
    db.save(a);
    String[][] menus = {
      {"dashboard", "寻源概况", "Overview", "dashboard"},
      {"rfqs", "询价与定标", "RFQs", "rfq.read"},
      {"suppliers", "供应商准入", "Suppliers", "supplier.read"},
      {"portal", "报价工作台", "Supplier portal", "portal"},
      {"audit", "操作审计", "Audit", "audit"},
      {"users", "账号管理", "Accounts", "admin"},
      {"roles", "角色与权限", "Roles", "admin"},
      {"departments", "部门", "Departments", "admin"},
      {"menus", "导航管理", "Menus", "admin"},
      {"permissions", "权限目录", "Permissions", "admin"},
      {"dictionaries", "业务字典", "Dictionaries", "admin"},
      {"settings", "系统参数", "Settings", "admin"}
    };
    for (int i = 0; i < menus.length; i++) {
      var m = new NavMenu();
      m.code = menus[i][0];
      m.name = menus[i][1];
      m.nameEn = menus[i][2];
      m.permissionCode = menus[i][3];
      m.position = i;
      m.enabled = true;
      db.save(m);
    }
    for (var e :
        Map.of(
                "currency",
                "CNY",
                "timezone",
                "Asia/Shanghai",
                "minimumQuotes",
                "2",
                "companyName",
                "知华供应商协同")
            .entrySet()) {
      var s = new SystemSetting();
      s.code = e.getKey();
      s.value = e.getValue();
      db.save(s);
    }
    for (var code : List.of("设备配件", "服务外协", "生产耗材")) {
      var d = new DictionaryEntry();
      d.type = "sourcingCategory";
      d.code = code;
      d.name = code;
      d.nameEn = code;
      db.save(d);
    }
  }

  private void role(String name, String scope, Set<String> p) {
    var r = new AccessRole();
    r.name = name;
    r.scope = scope;
    r.permissions = new HashSet<>(p);
    db.save(r);
  }
}
