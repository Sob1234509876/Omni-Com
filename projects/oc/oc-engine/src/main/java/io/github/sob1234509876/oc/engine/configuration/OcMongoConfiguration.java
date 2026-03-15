package io.github.sob1234509876.oc.engine.configuration;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import io.github.sob1234509876.oc.engine.Static;
import io.github.sob1234509876.oc.engine.dao.mongo.OcAreaMongoDao;
import io.github.sob1234509876.oc.engine.dao.mongo.OcPlayerMongoDao;
import io.github.sob1234509876.oc.engine.dao.mongo.OcPlayerToAreaDao;
import io.github.sob1234509876.oc.engine.dao.repository.OcPlayerAreaPair;
import io.github.sob1234509876.oc.engine.dao.repository.OcUUIDAreaPair;
import io.github.sob1234509876.oc.engine.dao.repository.OcUUIDPlayerPair;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

@Data
@NoArgsConstructor
@Configuration
@ConfigurationProperties("oc.mongo")
public class OcMongoConfiguration {

    String database = Static.DefaultMongoData
            .DATABASE;

    String address = Static.DefaultMongoData
            .ADDRESS;

    String areaCollection = Static.DefaultMongoData
            .AREA_COLLECTION;

    String playerCollection = Static.DefaultMongoData
            .PLAYER_COLLECTION;

    String playerToAreaCollection = Static.DefaultMongoData
            .PLAYER_TO_AREA_COLLECTION;

    @Bean
    @NonNull
    public MongoClient mongoClient() {
        return MongoClients.create(address);
    }

    @Bean
    @NonNull
    public MongoTemplate mongoTemplate(@NonNull MongoClient mongoClient) {
        return new MongoTemplate(mongoClient, database);
    }

    @Bean
    @NonNull
    public OcAreaMongoDao ocAreaMongoDao(@NonNull ApplicationContext applicationContext,
                                         @NonNull MongoTemplate mongo) {
        var tmp = new OcAreaMongoDao();
        tmp.setApplicationContext(applicationContext);
        tmp.setMongo(mongo);
        tmp.setCollectionName(areaCollection);
        tmp.setKeyName(Static.DefaultData
                .ENTRY_KEY_FIELD_NAME);
        tmp.setValueClass(OcUUIDAreaPair.class);

        return tmp;
    }

    @Bean
    @NonNull
    public OcPlayerMongoDao ocPlayerMongoDao(@NonNull ApplicationContext applicationContext,
                                             @NonNull MongoTemplate mongo) {
        var tmp = new OcPlayerMongoDao();
        tmp.setApplicationContext(applicationContext);
        tmp.setMongo(mongo);
        tmp.setCollectionName(playerCollection);
        tmp.setKeyName(Static.DefaultData
                .ENTRY_KEY_FIELD_NAME);
        tmp.setValueClass(OcUUIDPlayerPair.class);

        return tmp;
    }

    @Bean
    @NonNull
    public OcPlayerToAreaDao ocPlayerToAreaDao(@NonNull ApplicationContext applicationContext,
                                               @NonNull MongoTemplate mongo) {
        var tmp = new OcPlayerToAreaDao();
        tmp.setApplicationContext(applicationContext);
        tmp.setCollectionName(playerToAreaCollection);
        tmp.setMongo(mongo);
        tmp.setKeyName(Static.DefaultData
                .ENTRY_KEY_FIELD_NAME);
        tmp.setValueClass(OcPlayerAreaPair.class);

        return tmp;
    }

}
