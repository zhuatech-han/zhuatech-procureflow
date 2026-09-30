// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.procureflow;

import jakarta.persistence.*;

/** 供应商及待审资质，资质修改撤回准入。 官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "supplier")
public class Supplier {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Version
  @Column(nullable = false)
  public long version;

  @Column(name = "code", nullable = false, length = 60)
  public String code;

  @Column(name = "name", nullable = false, length = 120)
  public String name;

  @Column(name = "category", nullable = false, length = 60)
  public String category;

  @Column(name = "contact", nullable = false, length = 200)
  public String contact;

  @Column(name = "qualification", nullable = false, length = 2000)
  public String qualification;

  @Column(name = "valid_until", nullable = true)
  public java.time.LocalDate validUntil;

  @Column(name = "status", nullable = false, length = 30)
  public String status;

  @Column(name = "review_note", nullable = false, length = 1000)
  public String reviewNote;

  @Column(name = "submitted_by", nullable = false, length = 60)
  public String submittedBy;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "enabled", nullable = false)
  public boolean enabled;
}
