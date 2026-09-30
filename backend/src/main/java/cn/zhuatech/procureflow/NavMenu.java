// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.procureflow;

import jakarta.persistence.*;

/**
 * 权限控制的页面导航。 Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
 */
@Entity
@Table(name = "nav_menu")
public class NavMenu {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "code", nullable = false, length = 60)
  public String code;

  @Column(name = "name", nullable = false, length = 120)
  public String name;

  @Column(name = "name_en", nullable = false, length = 120)
  public String nameEn;

  @Column(name = "permission_code", nullable = false, length = 60)
  public String permissionCode;

  @Column(name = "position", nullable = false)
  public int position;

  @Column(name = "enabled", nullable = false)
  public boolean enabled;
}
