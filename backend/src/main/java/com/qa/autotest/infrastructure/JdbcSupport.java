package com.qa.autotest.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 基础设施层的小工具：时间转换与单值取值。 */
final class JdbcSupport {

    private JdbcSupport() {
    }

    static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    static <T> Optional<T> first(List<T> rows) {
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    static String text(Object value) {
        return value == null ? null : value.toString();
    }
}
