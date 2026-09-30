// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.procureflow;

import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 真实业务和管理接口，权限与数据范围由事务服务核验。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class ApiController {
  final SourcingService service;
  final AdminService admin;

  public ApiController(SourcingService service, AdminService admin) {
    this.service = service;
    this.admin = admin;
  }

  /** 字典与显示参数。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/options")
  public Object options() {
    return service.options();
  }

  /** 查询已授权供应商。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/suppliers")
  public Object suppliers() {
    return service.suppliers();
  }

  /** 建立供应商档案。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/suppliers")
  public Object supplier(@RequestBody SourcingService.SupplierInput v) {
    return service.saveSupplier(null, v);
  }

  /** 修改档案重新审核。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/suppliers/{id}")
  public Object supplier(@PathVariable Long id, @RequestBody SourcingService.SupplierInput v) {
    return service.saveSupplier(id, v);
  }

  /** 供应商准入审核。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/suppliers/{id}/{action}")
  public Object review(
      @PathVariable Long id, @PathVariable String action, @RequestBody SourcingService.Command c) {
    if (!Set.of("approve", "reject").contains(action)) throw new Problem(404, "NOT_FOUND");
    return service.reviewSupplier(id, action.equals("approve"), c);
  }

  /** 真实分页、搜索与状态过滤。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/rfqs")
  public Object list(
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "newest") String sort) {
    return service.list(search, status, page, size, sort);
  }

  /** 新建询价草稿。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/rfqs")
  public Object create(@RequestBody SourcingService.Draft v) {
    return service.saveDraft(null, v);
  }

  /** 获取安全详情。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/rfqs/{id}")
  public Object detail(@PathVariable Long id) {
    return service.detail(id);
  }

  /** 更新尚未发布的草稿。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/rfqs/{id}")
  public Object draft(@PathVariable Long id, @RequestBody SourcingService.Draft v) {
    return service.saveDraft(id, v);
  }

  /** 删除草稿。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/rfqs/{id}")
  public Object delete(@PathVariable Long id, @RequestParam Long version) {
    service.deleteDraft(id, version);
    return Map.of("ok", true);
  }

  /** 执行审批、报价、封标与定标，带请求幂等键。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/rfqs/{id}/{action}")
  public Object act(
      @PathVariable Long id, @PathVariable String action, @RequestBody SourcingService.Command c) {
    return service.act(id, action, c);
  }

  /** 下载冻结采购建议，内容不附加广告。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/rfqs/{id}/advice.json")
  public ResponseEntity<String> export(@PathVariable Long id) {
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=procurement-advice-" + id + ".json")
        .contentType(MediaType.APPLICATION_JSON)
        .body(service.export(id));
  }

  /** 仪表盘。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/dashboard")
  public Object dashboard() {
    return service.dashboard();
  }

  /** 操作审计。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/audit")
  public Object audit() {
    return service.audit();
  }

  /** 管理资源目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @GetMapping("/admin/{type}")
  public Object adminList(@PathVariable String type) {
    return admin.list(type);
  }

  /** 创建管理资源。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PostMapping("/admin/{type}")
  public Object adminCreate(@PathVariable String type, @RequestBody AdminService.Input v) {
    return admin.save(type, null, v);
  }

  /** 更新管理资源。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @PutMapping("/admin/{type}/{id}")
  public Object adminSave(
      @PathVariable String type, @PathVariable Long id, @RequestBody AdminService.Input v) {
    return admin.save(type, id, v);
  }

  /** 删除未被业务引用资源。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @DeleteMapping("/admin/{type}/{id}")
  public Object adminDelete(@PathVariable String type, @PathVariable Long id) {
    admin.delete(type, id);
    return Map.of("ok", true);
  }
}
