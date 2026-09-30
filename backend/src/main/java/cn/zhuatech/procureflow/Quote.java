// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.procureflow;

import jakarta.persistence.*;

/** 不可变报价版本，重报保留此前版本。 官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "quote")
public class Quote {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "rfq_id", nullable = false)
  public Long rfqId;

  @Column(name = "supplier_id", nullable = false)
  public Long supplierId;

  @Column(name = "supplier_name", nullable = false, length = 120)
  public String supplierName;

  @Column(name = "revision", nullable = false)
  public int revision;

  @Column(name = "status", nullable = false, length = 30)
  public String status;

  @Column(name = "terms", nullable = false, length = 2000)
  public String terms;

  @Column(name = "lead_days", nullable = false)
  public int leadDays;

  @Column(name = "valid_until", nullable = true)
  public java.time.Instant validUntil;

  @Column(name = "freight", nullable = false, precision = 16, scale = 2)
  public java.math.BigDecimal freight;

  @Column(name = "net_total", nullable = false, precision = 16, scale = 2)
  public java.math.BigDecimal netTotal;

  @Column(name = "tax_total", nullable = false, precision = 16, scale = 2)
  public java.math.BigDecimal taxTotal;

  @Column(name = "gross_total", nullable = false, precision = 16, scale = 2)
  public java.math.BigDecimal grossTotal;

  @Column(name = "submitted_at", nullable = false)
  public java.time.Instant submittedAt;

  @Column(name = "submitted_by", nullable = false, length = 60)
  public String submittedBy;
}
