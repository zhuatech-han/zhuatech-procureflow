// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.procureflow;

import jakarta.persistence.*;

/**
 * 登录账号；密码不进入响应。 Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech /
 * zhuatech2
 */
@Entity
@Table(name = "account")
public class Account {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "username", nullable = false, length = 60)
  public String username;

  @Column(name = "display_name", nullable = false, length = 120)
  public String displayName;

  @Column(name = "password_hash", nullable = false, length = 100)
  @com.fasterxml.jackson.annotation.JsonIgnore
  public String passwordHash;

  @Column(name = "role_id", nullable = false)
  public Long roleId;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "supplier_id")
  public Long supplierId;

  @Column(name = "enabled", nullable = false)
  public boolean enabled;
}
