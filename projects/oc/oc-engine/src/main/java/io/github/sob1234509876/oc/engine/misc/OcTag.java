package io.github.sob1234509876.oc.engine.misc;

import io.github.sob1234509876.oc.engine.api.Tag;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Data
@NoArgsConstructor
public class OcTag implements Tag {

    @Nullable
    Object value;

    @NonNull
    Map<String, Tag> children = new HashMap<>();

    @Override
    public @NonNull Optional<Object> as() {
        return Optional.ofNullable(value);
    }

    @Override
    public void set(@NonNull Object o) {
        value = o;
    }

    @Override
    public @NonNull Optional<Tag> get(@NonNull String attr) {
        return Optional.ofNullable(children.get(attr));
    }

    @Override
    public void set(@NonNull String attr, @NonNull Tag tag) {
        children.put(attr, tag);
    }

    @Override
    public @NonNull Map<@NonNull String, @NonNull Tag> asMap() {
        return children;
    }
}
