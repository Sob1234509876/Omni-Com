package io.github.sob1234509876.oc.engine.rm;

import io.github.sob1234509876.oc.engine.AbstractOcParent;
import io.github.sob1234509876.oc.engine.api.Plugin;
import io.github.sob1234509876.oc.engine.api.event.Event;
import io.github.sob1234509876.oc.engine.api.event.EventListener;
import io.github.sob1234509876.oc.engine.bean.OcResourceManager;
import io.github.sob1234509876.oc.engine.event.OcContentUpdateEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ApplicationContextAware;

import java.util.Optional;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class OcResourceManagerClassListener extends AbstractOcParent implements EventListener, ApplicationContextAware, InitializingBean {

    @SuppressWarnings("unchecked")
    @Override
    public @NonNull Optional<RunnableFuture<@NonNull Optional<Event>>> apply(@NonNull Event event) {

        if (!(event instanceof OcContentUpdateEvent update))
            return Optional.empty();

        var classes = update.getUpdatedObjects()
                .stream()
                .filter(o -> o instanceof Class)
                .map(o -> (Class<?>) o)
                .toList();

        var rm = getApplicationContext().getBean("ocResourceManager", OcResourceManager.class);
        var reg = (BeanDefinitionRegistry) getApplicationContext();

        return Optional.of(new FutureTask<>(() -> {
            var pcs = classes.stream()
                    .filter(Plugin.class::isAssignableFrom)
                    .map(o -> (Class<? extends Plugin>) o)
                    .toList();

            pcs.forEach(clazz -> reg.registerBeanDefinition(clazz.getName(),
                    new RootBeanDefinition(clazz)));

            // Batch operation
            rm.getPlugins().add(pcs.stream()
                    .map(clazz -> getApplicationContext().getBean(clazz.getName(),
                            Plugin.class))
                    .toList());

            return Optional.empty();
        }));
    }

    @Override
    public void afterPropertiesSet() {
        if (!(getApplicationContext() instanceof BeanDefinitionRegistry))
            throw new IllegalStateException("Field applicationContext is not a BeanDefinitionRegistry");
    }
}
