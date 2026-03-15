package io.github.sob1234509876.oc.engine.configuration;

import io.github.sob1234509876.oc.engine.dao.OcDaoImportSelector;
import io.github.sob1234509876.oc.engine.dao.collection.OcAreaMap;
import io.github.sob1234509876.oc.engine.dao.collection.OcPlayerMap;
import io.github.sob1234509876.oc.engine.dao.collection.OcPlayerToAreaMap;
import io.github.sob1234509876.oc.engine.dao.repository.OcAreaCrudRepository;
import io.github.sob1234509876.oc.engine.dao.repository.OcPlayerCrudRepository;
import io.github.sob1234509876.oc.engine.dao.repository.OcPlayerToAreaCrudRepository;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Data
@NoArgsConstructor
@Configuration
@Import(OcDaoImportSelector.class)
public class OcDaoCollectionConfiguration {

    @Bean
    @NonNull
    public OcAreaMap ocAreaMap(@NonNull OcAreaCrudRepository dao,
                               @NonNull ApplicationContext applicationContext) {
        var tmp = new OcAreaMap();
        tmp.setApplicationContext(applicationContext);
        tmp.setDao(dao);

        return tmp;
    }

    @Bean
    @NonNull
    public OcPlayerMap ocPlayerMap(@NonNull OcPlayerCrudRepository dao,
                                   @NonNull ApplicationContext applicationContext) {
        var tmp = new OcPlayerMap();
        tmp.setApplicationContext(applicationContext);
        tmp.setDao(dao);

        return tmp;
    }

    @Bean
    @NonNull
    public OcPlayerToAreaMap ocPlayerToAreaMap(@NonNull OcPlayerToAreaCrudRepository dao,
                                               @NonNull ApplicationContext applicationContext) {
        var tmp = new OcPlayerToAreaMap();
        tmp.setApplicationContext(applicationContext);
        tmp.setDao(dao);

        return tmp;
    }

}
