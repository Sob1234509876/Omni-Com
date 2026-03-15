package io.github.sob1234509876.oc.engine;

import io.github.sob1234509876.oc.engine.api.Parent;
import io.github.sob1234509876.oc.engine.configuration.OcConfiguration;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.net.URL;
import java.util.Optional;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Static {

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class DefaultData {
        public static final String PLUGIN_PATH = "plugins";
        public static final int DEFAULT_MSU = 50;
        public static final String DEFAULT_REPOSITORY_TYPE = RepositoryType.MONGO;
        public static final String OC_ICON = "assets/icon/oc.png";
        public static final Optional<URL> OC_ICON_URL = Optional.ofNullable(OcConfiguration.class
                .getResource(OC_ICON));
        public static final String ENTRY_KEY_FIELD_NAME = "key";
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class DefaultMongoData {
        public static final String DATABASE = "omni-com";
        public static final String ADDRESS = "mongodb://localhost";
        public static final String AREA_COLLECTION = "area";
        public static final String PLAYER_COLLECTION = "player";
        public static final String PLAYER_TO_AREA_COLLECTION = "playerToArea";
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class RepositoryType {
        public static final String MONGO = "mongo";
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Type {
        @NonNull
        public static String get(Class<? extends Parent> clazz) {
            return "oc.engine." + clazz.getSimpleName();
        }
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class LocalizeNameCode {
        @NonNull
        public static String get(Class<? extends Parent> clazz) {
            return "oc.engine." + clazz.getSimpleName();
        }
    }

}
