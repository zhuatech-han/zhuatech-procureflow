// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.procureflow;

import jakarta.persistence.*;

/** 需求行，保留采购建议所需规格及数量。 官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "rfq_line")
public class RfqLine {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "rfq_id", nullable = false)
  public Long rfqId;

  @Column(name = "item_code", nullable = false, length = 60)
  public String itemCode;

  @Column(name = "name", nullable = false, length = 200)
  public String name;

  @Column(name = "specification", nullable = false, length = 1000)
  public String specification;

  @Column(name = "unit", nullable = false, length = 30)
  public String unit;

  @Column(name = "quantity", nullable = false, precision = 16, scale = 3)
  public java.math.BigDecimal quantity;
}
