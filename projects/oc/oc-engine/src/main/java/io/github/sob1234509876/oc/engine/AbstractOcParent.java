package io.github.sob1234509876.oc.engine;

import lombok.*;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public abstract class AbstractOcParent extends AbstractParent {

    @Override
    public @NonNull String getLocalizedNameCode() {
        return Static.LocalizeNameCode
                .get(getClass());
    }

    @Override
    public @NonNull String getType() {
        return Static.Type
                .get(getClass());
    }
}
