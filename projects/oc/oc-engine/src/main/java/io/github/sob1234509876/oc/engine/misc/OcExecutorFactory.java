package io.github.sob1234509876.oc.engine.misc;

import io.github.sob1234509876.oc.engine.AbstractOcParent;
import lombok.*;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class OcExecutorFactory extends AbstractOcParent implements ExecutorFactory {

    @Override
    public @NonNull Executor create() {
        return Executors.newFixedThreadPool(Runtime.getRuntime()
                .availableProcessors());
    }

}
