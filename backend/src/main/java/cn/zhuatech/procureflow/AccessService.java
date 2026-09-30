// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.procureflow;

import java.time.Clock;
import java.util.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** 每次操作重新检查账号、角色与部门范围，即时撤销权限。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
public class AccessService {
  final Store db;
  final Clock clock;

  public AccessService(Store db, Clock clock) {
    this.db = db;
    this.clock = clock;
  }

  /** 读取当前账号，禁用账号立即失效。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Account current() {
    var a = SecurityContextHolder.getContext().getAuthentication();
    if (a == null || !a.isAuthenticated()) throw new Problem(401, "UNAUTHENTICATED");
    var rows = db.query(Account.class, "from Account where username=?1", a.getName());
    if (rows.isEmpty()
        || !rows.getFirst().enabled
        || !Objects.equals(a.getDetails(), rows.getFirst().passwordHash))
      throw new Problem(401, "UNAUTHENTICATED");
    return rows.getFirst();
  }

  /** 验证业务权限，不因隐藏菜单而省略接口检查。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void require(String permission) {
    if (!role().permissions.contains(permission)) throw new Problem(403, "FORBIDDEN");
  }

  /** 取得数据库中的实时角色。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public AccessRole role() {
    return db.get(AccessRole.class, current().roleId);
  }

  /** 验证部门数据范围。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void department(Long id) {
    if (!visible(id)) throw new Problem(403, "OUT_OF_SCOPE");
  }

  /** 判定部门记录是否属于当前账号的数据范围。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public boolean visible(Long id) {
    return !"SUPPLIER".equals(role().scope)
        && ("ALL".equals(role().scope) || Objects.equals(current().departmentId, id));
  }

  /** 写入不含敏感载荷的审计记录，与业务事务一起提交。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void audit(String action, Object id, Long department) {
    var a = new AuditEvent();
    a.actor = current().username;
    a.action = action;
    a.objectId = String.valueOf(id);
    a.departmentId = department;
    a.createdAt = clock.instant();
    db.save(a);
  }

  /** 安全账号响应；密码和密码哈希不返回。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> profile() {
    var a = current();
    var r = role();
    var menus =
        db.all(NavMenu.class).stream()
            .filter(m -> m.enabled && r.permissions.contains(m.permissionCode))
            .sorted(Comparator.comparingInt(m -> m.position))
            .toList();
    var result =
        new LinkedHashMap<String, Object>(
            Map.of(
                "id",
                a.id,
                "username",
                a.username,
                "displayName",
                a.displayName,
                "departmentId",
                a.departmentId,
                "role",
                r.name,
                "permissions",
                r.permissions,
                "scope",
                r.scope,
                "menus",
                menus));
    result.put("supplierId", a.supplierId);
    return result;
  }
}
