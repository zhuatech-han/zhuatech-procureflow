// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.procureflow;

import jakarta.persistence.*;

/**
 * 角色和数据范围。 Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
 */
@Entity
@Table(name = "access_role")
public class AccessRole {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "name", nullable = false, length = 120)
  public String name;

  @Column(name = "scope", nullable = false, length = 20)
  public String scope;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "role_permission", joinColumns = @JoinColumn(name = "role_id"))
  @Column(name = "permission_code", length = 60)
  public java.util.Set<String> permissions = new java.util.HashSet<>();
}
