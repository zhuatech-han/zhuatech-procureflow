// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.procureflow;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

/** 询价草稿与审批状态，发布后冻结需求。 官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "rfq")
public class Rfq {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Version
  @Column(nullable = false)
  public long version;

  @Column(name = "number", nullable = false, length = 60)
  public String number;

  @Column(name = "title", nullable = false, length = 200)
  public String title;

  @Column(name = "description", nullable = false, length = 2000)
  public String description;

  @Column(name = "category", nullable = false, length = 60)
  public String category;

  @Column(name = "currency", nullable = false, length = 3)
  public String currency;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "status", nullable = false, length = 30)
  public String status;

  @Column(name = "submitted_by", nullable = false, length = 60)
  public String submittedBy;

  @Column(name = "created_by", nullable = false, length = 60)
  public String createdBy;

  @Column(name = "created_at", nullable = false)
  public java.time.Instant createdAt;

  @Column(name = "deadline", nullable = false)
  public java.time.Instant deadline;

  @Column(name = "minimum_quotes", nullable = false)
  public int minimumQuotes;

  @Column(name = "review_note", nullable = false, length = 1000)
  public String reviewNote;

  @Column(name = "selected_quote_id", nullable = true)
  public Long selectedQuoteId;

  @Column(name = "selection_reason", nullable = false, length = 2000)
  public String selectionReason;

  @Column(name = "exception_reason", nullable = false, length = 1000)
  public String exceptionReason;

  @Column(name = "selected_by", nullable = false, length = 60)
  public String selectedBy;

  @Column(name = "awarded_at", nullable = true)
  public java.time.Instant awardedAt;

  @Column(name = "approved_by", nullable = false, length = 60)
  public String approvedBy;

  @JsonIgnore
  @Column(name = "award_snapshot", columnDefinition = "text")
  public String awardSnapshot;
}
