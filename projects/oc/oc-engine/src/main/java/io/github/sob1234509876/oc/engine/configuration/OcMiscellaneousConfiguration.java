package io.github.sob1234509876.oc.engine.configuration;

import io.github.sob1234509876.oc.engine.misc.OcExecutorFactory;
import io.github.sob1234509876.oc.engine.rm.util.OcInMemoryClassLoader;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;

@Data
@NoArgsConstructor
@Configuration
public class OcMiscellaneousConfiguration {

    @Bean
    @NonNull
    public OcExecutorFactory ocExecutorFactory(@NonNull ApplicationContext applicationContext) {
        var tmp = new OcExecutorFactory();
        tmp.setApplicationContext(applicationContext);

        return tmp;
    }

    @Bean
    @NonNull
    public Executor ocSharedExecutor(@NonNull OcExecutorFactory ocExecutorFactory) {
        return ocExecutorFactory.create();
    }

    @Bean
    @NonNull
    public OcInMemoryClassLoader ocInMemoryClassLoader() {
        return new OcInMemoryClassLoader();
    }

}
