package com.qa.autotest.application.ci;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * CI 触发与回写参数（PLN-03），配置前缀 {@code ci}。
 *
 * <p><b>回写默认关闭</b>：{@code ci.writeback.enabled=false}。开启后仍要求环境变量
 * {@code GITHUB_STATUS_TOKEN} 存在；令牌只从环境读取，<b>不进配置文件、不落库、不落日志</b>。
 */
@Component
@ConfigurationProperties(prefix = "ci")
public class CiProperties {

    /** 同一令牌每分钟允许的触发次数。 */
    private int triggerPerMinute = 10;

    /** 限流窗口秒数。 */
    private int triggerWindowSeconds = 60;

    private Writeback writeback = new Writeback();

    public int getTriggerPerMinute() {
        return triggerPerMinute;
    }

    public void setTriggerPerMinute(int triggerPerMinute) {
        this.triggerPerMinute = triggerPerMinute;
    }

    public int getTriggerWindowSeconds() {
        return triggerWindowSeconds;
    }

    public void setTriggerWindowSeconds(int triggerWindowSeconds) {
        this.triggerWindowSeconds = triggerWindowSeconds;
    }

    public Writeback getWriteback() {
        return writeback;
    }

    public void setWriteback(Writeback writeback) {
        this.writeback = writeback;
    }

    /** GitHub commit status 回写参数。 */
    public static class Writeback {

        private boolean enabled = false;
        private String apiBaseUrl = "https://api.github.com";
        private String targetBaseUrl = "http://127.0.0.1:5173";
        private String context = "qa-platform/ci";
        private int timeoutSeconds = 10;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getApiBaseUrl() {
            return apiBaseUrl;
        }

        public void setApiBaseUrl(String apiBaseUrl) {
            this.apiBaseUrl = apiBaseUrl;
        }

        public String getTargetBaseUrl() {
            return targetBaseUrl;
        }

        public void setTargetBaseUrl(String targetBaseUrl) {
            this.targetBaseUrl = targetBaseUrl;
        }

        public String getContext() {
            return context;
        }

        public void setContext(String context) {
            this.context = context;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }
}
