package io.github.sob1234509876.oc.engine;

import io.github.sob1234509876.oc.engine.api.Parent;
import io.github.sob1234509876.oc.engine.api.Tag;
import io.github.sob1234509876.oc.engine.misc.OcTag;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.i18n.LocaleContextHolder;

import java.net.URL;
import java.util.Optional;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@Data
@NoArgsConstructor
@RequiredArgsConstructor
public abstract class AbstractParent implements Parent, ApplicationContextAware {

    @NonNull
    ApplicationContext applicationContext;

    @NonNull
    String type;

    @NonNull
    String localizedNameCode;

    @NonNull
    Optional<URL> icon = Static.DefaultData
            .OC_ICON_URL;

    @NonNull
    Tag tag = new OcTag();

    @Override
    public @NonNull String getLocalizedName() {
        return applicationContext.getMessage(getLocalizedNameCode(),
                new Object[0],
                LocaleContextHolder.getLocale());
    }
}
