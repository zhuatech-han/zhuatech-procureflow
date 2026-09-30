// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.procureflow;

import jakarta.persistence.*;

/** 报价行的未税价格及税率快照。 官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "quote_line")
public class QuoteLine {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "quote_id", nullable = false)
  public Long quoteId;

  @Column(name = "rfq_line_id", nullable = false)
  public Long rfqLineId;

  @Column(name = "unit_price", nullable = false, precision = 16, scale = 4)
  public java.math.BigDecimal unitPrice;

  @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2)
  public java.math.BigDecimal taxRate;

  @Column(name = "net_amount", nullable = false, precision = 16, scale = 2)
  public java.math.BigDecimal netAmount;

  @Column(name = "tax_amount", nullable = false, precision = 16, scale = 2)
  public java.math.BigDecimal taxAmount;
}
