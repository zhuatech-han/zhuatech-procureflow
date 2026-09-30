// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
package cn.zhuatech.procureflow;

import jakarta.persistence.*;
import java.util.*;
import org.springframework.stereotype.Repository;

/**
 * 持久化查询；实体类型由代码确定，条件值全部绑定。 Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信
 * zhuatech / zhuatech2
 */
@Repository
public class Store {
  @PersistenceContext EntityManager em;

  /**
   * 查询指定实体；不存在返回 404。 Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech /
   * zhuatech2
   */
  public <T> T get(Class<T> type, Long id) {
    var v = em.find(type, id);
    if (v == null) throw new Problem(404, "NOT_FOUND");
    return v;
  }

  /**
   * 加行锁，序列化同一询价或供应商的变更。 Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech
   * / zhuatech2
   */
  public <T> T lock(Class<T> type, Long id) {
    var v = em.find(type, id, LockModeType.PESSIMISTIC_WRITE);
    if (v == null) throw new Problem(404, "NOT_FOUND");
    return v;
  }

  /**
   * 保存自有实体。 Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech / zhuatech2
   */
  public <T> T save(T entity) {
    em.persist(entity);
    return entity;
  }

  /**
   * 删除未被业务引用的实体，外键保护历史。 Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech
   * / zhuatech2
   */
  public void delete(Object entity) {
    em.remove(entity);
    em.flush();
  }

  /**
   * 查询有界列表，禁止客户端拼接 JPQL。 Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech
   * / zhuatech2
   */
  public <T> List<T> all(Class<T> type) {
    return em.createQuery("from " + type.getSimpleName() + " e order by e.id", type)
        .setMaxResults(10000)
        .getResultList();
  }

  /**
   * 执行固定查询，值参数由调用方绑定。 Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业咨询微信 zhuatech /
   * zhuatech2
   */
  public <T> List<T> query(Class<T> type, String jpql, Object... params) {
    var q = em.createQuery(jpql, type);
    for (int i = 0; i < params.length; i++) q.setParameter(i + 1, params[i]);
    return q.getResultList();
  }

  /** 通过实际注入的EntityManager刷新版本及约束，避免访问代理字段。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void flush() {
    em.flush();
  }

  /** 构造仅由业务代码提供的JPQL，调用者必须绑定过滤参数。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public <T> TypedQuery<T> jpql(Class<T> type, String statement) {
    return em.createQuery(statement, type);
  }
}
