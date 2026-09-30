// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.procureflow;

import jakarta.persistence.*;

/** 已准入供应商的询价邀请。 官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "invitation")
public class Invitation {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "rfq_id", nullable = false)
  public Long rfqId;

  @Column(name = "supplier_id", nullable = false)
  public Long supplierId;

  @Column(name = "supplier_name", nullable = false, length = 120)
  public String supplierName;
}
