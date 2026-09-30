// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.procureflow;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 不可修改的业务操作审计；不记录密码。 Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech /
 * zhuatech2
 */
@Entity
@Table(name = "audit_event")
public class AuditEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "actor", nullable = false, length = 60)
  public String actor;

  @Column(name = "action", nullable = false, length = 120)
  public String action;

  @Column(name = "object_id", nullable = false, length = 80)
  public String objectId;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
