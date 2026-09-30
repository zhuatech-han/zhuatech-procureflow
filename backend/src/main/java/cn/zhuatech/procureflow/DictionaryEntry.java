// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.procureflow;

import jakarta.persistence.*;

/**
 * 可编辑业务分类字典，用于供应商与询价分类。 Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech
 * / zhuatech2
 */
@Entity
@Table(name = "dictionary_entry")
public class DictionaryEntry {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "type", nullable = false, length = 60)
  public String type;

  @Column(name = "code", nullable = false, length = 60)
  public String code;

  @Column(name = "name", nullable = false, length = 120)
  public String name;

  @Column(name = "name_en", nullable = false, length = 120)
  public String nameEn;
}
