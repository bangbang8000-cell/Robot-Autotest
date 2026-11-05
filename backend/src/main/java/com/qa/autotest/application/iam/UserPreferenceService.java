package com.qa.autotest.application.iam;

import com.qa.autotest.application.repository.UserPreferenceRepository;
import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 用户偏好（UX-01）。
 *
 * <p><b>这是一张白名单表，不是通用 KV 存储。</b>键与值都必须在 {@link #ALLOWED} 内：
 * 否则它会变成"任何登录用户都能往数据库写任意字符串"的口子，而且值会随
 * {@code GET /users/me/preferences} 原样回吐给前端。
 *
 * <p>主题偏好的作用是<b>换设备后一致</b>：本机选择先落 localStorage（首帧要用），
 * 登录后再以服务端为准覆盖。所以这里不做"合并"，服务端就是权威。
 */
@Service
public class UserPreferenceService {

    /**
     * 允许的键与取值。新增偏好项要同时改这里与前端常量。
     *
     * <p>坑：{@code accent}（UX-04 六预设强调色）漏加过一次——前端照常
     * {@code PUT {accent:"violet"}}，后端 400，前端按"偏好同步失败不回滚"的约定静默吞掉，
     * 于是表现出「本机有效、换设备丢」的假象，日志里什么都没有。偏好类缺陷都是这个形态：
     * 不报错、只是不同步。加键时必须同步补 {@code UserPreferenceIT} 的用例。
     *
     * <p>accent 取值 2026-11-05 换版（新年红/科技蓝/土豪金/活力橙/魅力紫/翡翠绿）。
     * 首版键名 cyan/indigo/teal/fuchsia 不再接受：前端 {@code useAccent.normalizeAccent}
     * 会把库里的旧值归一成新值并在下一次写入时覆盖，所以服务端不需要为兼容留旧值。
     * 保留旧值反而会让"六预设"的定义在两侧不一致，是更坏的长期债。
     *
     * <p>density（UX-06 表格密度）只有舒适/紧凑两档，与前端
     * {@code useDensity.DENSITIES} 一一对应；前端只发这两个值，多一个都不收。
     */
    private static final Map<String, Set<String>> ALLOWED = Map.of(
            "theme", Set.of("system", "light", "dark"),
            "locale", Set.of("zh-CN", "en-US"),
            "accent", Set.of("crimson", "blue", "gold", "orange", "violet", "jade"),
            "density", Set.of("comfortable", "compact"));

    /** 单次提交的键数上限：偏好项就几项，超过说明调用方写错了。 */
    private static final int MAX_KEYS_PER_REQUEST = 10;

    private final UserPreferenceRepository repository;

    public UserPreferenceService(UserPreferenceRepository repository) {
        this.repository = repository;
    }

    public Map<String, String> get(long userId) {
        return repository.findAll(userId);
    }

    /**
     * 全量校验通过后才写入：不留下"一半新一半旧"的状态。
     * 空 Map 视为无操作（幂等），不报错——前端在用户没改任何东西时可能发空请求。
     */
    public Map<String, String> put(long userId, Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return repository.findAll(userId);
        }
        if (values.size() > MAX_KEYS_PER_REQUEST) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "偏好项过多：" + values.size() + "，单次上限 " + MAX_KEYS_PER_REQUEST);
        }
        values.forEach(UserPreferenceService::validate);
        values.forEach((key, value) -> repository.upsert(userId, key, value));
        return repository.findAll(userId);
    }

    private static void validate(String key, String value) {
        Set<String> allowedValues = ALLOWED.get(key);
        if (allowedValues == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "不支持的偏好键：" + key);
        }
        if (value == null || !allowedValues.contains(value)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "偏好值非法：" + key + "=" + value);
        }
    }
}
