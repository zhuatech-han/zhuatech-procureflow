// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.procureflow;

import jakarta.persistence.*;

/** 询价写请求幂等凭据，防止重报产生重复版本。 官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "mutation_stamp")
public class MutationStamp {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "rfq_id", nullable = false)
  public Long rfqId;

  @Column(name = "actor", nullable = false, length = 60)
  public String actor;

  @Column(name = "request_key", nullable = false, length = 80)
  public String requestKey;

  @Column(name = "fingerprint", nullable = false, length = 64)
  public String fingerprint;
}
