// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.procureflow;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** 会话认证；返回错误代码而不泄露用户名存在性。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
  final Store db;
  final AccessService access;
  final BCryptPasswordEncoder encoder;
  final Map<String, Attempt> attempts = new LinkedHashMap<>();

  /** 登录输入。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Login(
      @NotBlank @Size(max = 60) String username, @NotBlank @Size(max = 128) String password) {}

  /** 失败窗口。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  record Attempt(int count, Instant until) {}

  public AuthController(Store db, AccessService access, BCryptPasswordEncoder encoder) {
    this.db = db;
    this.access = access;
    this.encoder = encoder;
  }

  /** 获取当前会话 CSRF 请求头和令牌。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/csrf")
  public Map<String, String> csrf(CsrfToken token) {
    return Map.of("header", token.getHeaderName(), "token", token.getToken());
  }

  /** 登录并更新会话标识；连续失败限制，密码不写日志。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/login")
  @Transactional
  public synchronized Map<String, Object> login(
      @Valid @RequestBody Login input, HttpServletRequest request, HttpServletResponse response) {
    var key = request.getRemoteAddr() + ":" + input.username();
    var now = Instant.now();
    attempts.entrySet().removeIf(e -> e.getValue().until.isBefore(now));
    var prior = attempts.get(key);
    if (prior != null && prior.count >= 8) throw new Problem(429, "LOGIN_THROTTLED");
    var rows = db.query(Account.class, "from Account where username=?1", input.username());
    var user = rows.isEmpty() ? null : rows.getFirst();
    if (user == null || !encoder.matches(input.password(), user.passwordHash) || !user.enabled) {
      if (attempts.size() > 2000) attempts.remove(attempts.keySet().iterator().next());
      attempts.put(key, new Attempt(prior == null ? 1 : prior.count + 1, now.plusSeconds(300)));
      throw new Problem(401, "LOGIN_FAILED");
    }
    attempts.remove(key);
    request.getSession();
    request.changeSessionId();
    var context = SecurityContextHolder.createEmptyContext();
    var authentication =
        UsernamePasswordAuthenticationToken.authenticated(user.username, null, List.of());
    authentication.setDetails(user.passwordHash);
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
    new HttpSessionSecurityContextRepository().saveContext(context, request, response);
    access.audit("LOGIN", user.id, user.departmentId);
    return access.profile();
  }

  /** 返回当前菜单、权限和账号。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/me")
  @Transactional(readOnly = true)
  public Map<String, Object> me() {
    return access.profile();
  }

  /** 退出后使服务端会话失效。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/logout")
  public Map<String, Boolean> logout(HttpServletRequest req) {
    var session = req.getSession(false);
    if (session != null) session.invalidate();
    SecurityContextHolder.clearContext();
    return Map.of("ok", true);
  }

  /** 登录用户修改自己的密码，旧密码验证后撤销该会话。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/password")
  @Transactional
  public Map<String, Boolean> password(
      @RequestBody Map<String, String> body, HttpServletRequest req) {
    var a = access.current();
    if (!encoder.matches(body.getOrDefault("oldPassword", ""), a.passwordHash))
      throw new Problem(400, "OLD_PASSWORD_INVALID");
    var value = body.getOrDefault("newPassword", "");
    AdminService.validatePassword(value);
    var encoded = encoder.encode(value);
    access.audit("PASSWORD_CHANGE", a.id, a.departmentId);
    a.passwordHash = encoded;
    return logout(req);
  }
}
