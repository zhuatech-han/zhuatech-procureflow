// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.procureflow;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** 验证采购金额舍入、百分税率、零报价与精度边界。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
class QuoteMathTest {
  @Test
  void lineRounding() {
    assertEquals(
        new BigDecimal("10.01"), QuoteMath.net(new BigDecimal("3"), new BigDecimal("3.335")));
  }

  @Test
  void percentageTax() {
    assertEquals(
        new BigDecimal("1.30"), QuoteMath.tax(new BigDecimal("10.01"), new BigDecimal("13")));
  }

  @Test
  void zeroPriceAllowed() {
    assertEquals(new BigDecimal("0.0000"), QuoteMath.decimal(BigDecimal.ZERO, 4, false));
  }

  @Test
  void quantityMustBePositive() {
    assertThrows(Problem.class, () -> QuoteMath.decimal(BigDecimal.ZERO, 3, true));
  }

  @Test
  void precisionNotSilentlyRounded() {
    assertThrows(Problem.class, () -> QuoteMath.decimal(new BigDecimal("1.0001"), 3, true));
  }

  @Test
  void noNegativeAmount() {
    assertThrows(Problem.class, () -> QuoteMath.decimal(new BigDecimal("-1"), 2, false));
  }

  @Test
  void taxBoundaries() {
    assertEquals(
        new BigDecimal("10.01"), QuoteMath.tax(new BigDecimal("10.01"), new BigDecimal("100")));
    assertThrows(Problem.class, () -> QuoteMath.tax(BigDecimal.ONE, new BigDecimal("100.01")));
  }

  @Test
  void amountLimit() {
    assertThrows(Problem.class, () -> QuoteMath.decimal(new BigDecimal("1000000000"), 2, false));
  }
}
