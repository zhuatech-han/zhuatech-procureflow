// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.procureflow;

/**
 * 可对外呈现的错误代码；不包含 SQL 或凭证。 Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信
 * zhuatech / zhuatech2
 */
public class Problem extends RuntimeException {
  public final int status;

  public Problem(int status, String code) {
    super(code);
    this.status = status;
  }
}
