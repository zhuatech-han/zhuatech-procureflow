// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.procureflow;

import java.math.*;

/** 报价数量、金额精度与逐行税额，避免浏览器金额成为账据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
public final class QuoteMath {
  private QuoteMath() {}

  /** 拒绝负数、超限及隐式舍入；数量必须为正。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static BigDecimal decimal(BigDecimal n, int scale, boolean positive) {
    if (n == null
        || n.signum() < 0
        || positive && n.signum() == 0
        || n.compareTo(new BigDecimal("999999999.999")) > 0)
      throw new Problem(400, "INVALID_AMOUNT");
    try {
      return n.setScale(scale, RoundingMode.UNNECESSARY);
    } catch (ArithmeticException e) {
      throw new Problem(400, "INVALID_PRECISION");
    }
  }

  /** 每行未税金额先四舍五入至两位，再计算该行百分数税额。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static BigDecimal net(BigDecimal quantity, BigDecimal unitPrice) {
    return quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
  }

  /** 按已经舍入的未税金额计算税额，税率范围0–100。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static BigDecimal tax(BigDecimal net, BigDecimal rate) {
    if (rate.signum() < 0 || rate.compareTo(BigDecimal.valueOf(100)) > 0)
      throw new Problem(400, "INVALID_TAX");
    return net.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
  }
}
