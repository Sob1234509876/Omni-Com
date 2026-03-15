package io.github.sob1234509876.oc.engine.misc;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
public class OcPair<@NonNull K, @NonNull V> {

    @NonNull
    K key;

    @NonNull
    V value;

    public static <K, V> OcPair<K, V> from(Map.@NonNull Entry<K, V> entry) {
        return new OcPair<>(entry.getKey(), entry.getValue());
    }

    public Map.Entry<K, V> toEntry() {
        return Map.entry(key, value);
    }
}
