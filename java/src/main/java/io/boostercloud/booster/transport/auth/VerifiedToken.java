package io.boostercloud.booster.transport.auth;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Verified identity. Claims are immutable, including nested JSON objects and arrays. */
public record VerifiedToken(String subject, Set<String> roles, Map<String, Object> claims, Map<String, Object> header) {
    public VerifiedToken(String subject, Set<String> roles, Map<String, Object> claims) { this(subject, roles, claims, Map.of()); }
    public VerifiedToken {
        roles = Set.copyOf(roles);
        claims = immutableMap(claims);
        header = immutableMap(header);
    }

    private static Map<String, Object> immutableMap(Map<String, ?> source) {
        var copy = new LinkedHashMap<String, Object>();
        source.forEach((key, value) -> copy.put(key, immutableValue(value)));
        return Collections.unmodifiableMap(copy);
    }

    private static Object immutableValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            var copy = new LinkedHashMap<String, Object>();
            map.forEach((key, child) -> copy.put(Objects.toString(key), immutableValue(child)));
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) {
            return Collections.unmodifiableList(list.stream().map(VerifiedToken::immutableValue).toList());
        }
        return value;
    }
}
