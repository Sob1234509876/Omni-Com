package io.github.sob1234509876.oc.engine.dao;

import io.github.sob1234509876.oc.engine.Static;
import io.github.sob1234509876.oc.engine.configuration.OcMongoConfiguration;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;

@Data
@NoArgsConstructor
@ConfigurationProperties("oc.data")
public class OcDaoImportSelector implements ImportSelector {

    @NonNull
    String repository = Static.DefaultData
            .DEFAULT_REPOSITORY_TYPE;

    boolean useImplemented = true;

    @Override
    public String @NonNull [] selectImports(@NonNull AnnotationMetadata importingClassMetadata) {
        if (!useImplemented)
            return new String[0];

        if (repository.equals(Static.RepositoryType.MONGO))
            return new String[]{OcMongoConfiguration.class
                    .getName()};

        throw new IllegalArgumentException("Repository in application configuration doesn't exist");
    }
}
