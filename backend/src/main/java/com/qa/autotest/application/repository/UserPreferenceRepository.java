package com.qa.autotest.application.repository;

import java.util.Map;
import java.util.Optional;

/**
 * 用户偏好仓储（UX-01）。
 *
 * <p>纯 KV：不含白名单校验（那是应用层的事），也不解释值的含义。
 */
public interface UserPreferenceRepository {

    /** 该用户全部偏好；无记录返回空 Map（不是 null）。 */
    Map<String, String> findAll(long userId);

    Optional<String> find(long userId, String key);

    /** 有则更新、无则插入。并发改不同 key 不互相覆盖。 */
    void upsert(long userId, String key, String value);
}
