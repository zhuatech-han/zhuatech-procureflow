// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.procureflow;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 参数、权限、并发与业务错误的统一安全响应。 Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech
 * / zhuatech2
 */
@RestControllerAdvice
public class ErrorHandler {
  /**
   * 返回明确业务错误。 Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech /
   * zhuatech2
   */
  @ExceptionHandler(Problem.class)
  ResponseEntity<?> business(Problem e) {
    return ResponseEntity.status(e.status).body(Map.of("code", e.getMessage()));
  }

  /**
   * 隐藏约束内部细节。 Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech /
   * zhuatech2
   */
  @ExceptionHandler({
    DataIntegrityViolationException.class,
    jakarta.persistence.PersistenceException.class
  })
  ResponseEntity<?> conflict(Exception e) {
    return ResponseEntity.status(409).body(Map.of("code", "CONFLICT"));
  }

  /**
   * 校验和类型转换错误返回 400。 Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech /
   * zhuatech2
   */
  @ExceptionHandler({
    IllegalArgumentException.class,
    org.springframework.web.bind.MethodArgumentNotValidException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class
  })
  ResponseEntity<?> invalid(Exception e) {
    return ResponseEntity.badRequest().body(Map.of("code", "INVALID_INPUT"));
  }
}
