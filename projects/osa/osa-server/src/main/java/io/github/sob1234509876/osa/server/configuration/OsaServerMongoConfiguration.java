package io.github.sob1234509876.osa.server.configuration;

import com.mongodb.client.MongoClient;
import io.github.sob1234509876.osa.server.dao.mongo.OsaServerMongoUserDao;
import io.github.sob1234509876.osa.server.dao.mongo.OsaServerVerificationCodeDao;
import io.github.sob1234509876.osa.server.entity.OsaServerVerificationCode;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
@Data
@NoArgsConstructor
@Configuration
public class OsaServerMongoConfiguration {

    public static final String DB_NAME = "user";

    @Bean
    @NonNull
    public MongoTemplate mongoTemplate(@NonNull MongoClient mongoClient) {
        return new MongoTemplate(mongoClient, DB_NAME);
    }

    @Bean
    @NonNull
    public OsaServerMongoUserDao userDao(@NonNull MongoTemplate mongoTemplate) {
        return new OsaServerMongoUserDao(mongoTemplate);
    }

    @Bean
    @NonNull
    public OsaServerVerificationCodeDao verificationCodeDao(@NonNull MongoTemplate mongoTemplate) {
        return new OsaServerVerificationCodeDao(mongoTemplate);
    }

}
