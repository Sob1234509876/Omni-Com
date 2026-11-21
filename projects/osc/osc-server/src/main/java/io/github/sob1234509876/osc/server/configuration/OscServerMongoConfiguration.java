package io.github.sob1234509876.osc.server.configuration;

import com.mongodb.client.MongoClient;
import io.github.sob1234509876.osc.api.OscApiChatDao;
import io.github.sob1234509876.osc.api.OscApiChatUserDao;
import io.github.sob1234509876.osc.api.OscApiFileResourceDao;
import io.github.sob1234509876.osc.api.OscApiFriendLinkDao;
import io.github.sob1234509876.osc.api.OscApiMessageDao;
import io.github.sob1234509876.osc.api.OscApiRequestDao;
import io.github.sob1234509876.osc.api.OscApiRoomDao;
import io.github.sob1234509876.osc.api.OscApiRoomUserDao;
import io.github.sob1234509876.osc.server.dao.mongo.OscServerMongoChatDao;
import io.github.sob1234509876.osc.server.dao.mongo.OscServerMongoChatUserDao;
import io.github.sob1234509876.osc.server.dao.mongo.OscServerMongoFileResourceDao;
import io.github.sob1234509876.osc.server.dao.mongo.OscServerMongoFriendLinkDao;
import io.github.sob1234509876.osc.server.dao.mongo.OscServerMongoMessageDao;
import io.github.sob1234509876.osc.server.dao.mongo.OscServerMongoRequestDao;
import io.github.sob1234509876.osc.server.dao.mongo.OscServerMongoRoomDao;
import io.github.sob1234509876.osc.server.dao.mongo.OscServerMongoRoomUserDao;
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
public class OscServerMongoConfiguration {

    public static final String DB_NAME = "chat";

    @Bean
    @NonNull
    public MongoTemplate mongoTemplate(@NonNull MongoClient mongoClient) {
        return new MongoTemplate(mongoClient, DB_NAME);
    }

    @Bean
    @NonNull
    public OscApiChatUserDao chatUserDao(@NonNull MongoTemplate mongoTemplate) {
        return new OscServerMongoChatUserDao(mongoTemplate);
    }

    @Bean
    @NonNull
    public OscApiChatDao chatDao(@NonNull MongoTemplate mongoTemplate) {
        return new OscServerMongoChatDao(mongoTemplate);
    }

    @Bean
    @NonNull
    public OscApiRoomDao roomDao(@NonNull MongoTemplate mongoTemplate) {
        return new OscServerMongoRoomDao(mongoTemplate);
    }

    @Bean
    @NonNull
    public OscApiRoomUserDao roomUserDao(@NonNull MongoTemplate mongoTemplate) {
        return new OscServerMongoRoomUserDao(mongoTemplate);
    }

    @Bean
    @NonNull
    public OscApiMessageDao messageDao(@NonNull MongoTemplate mongoTemplate) {
        return new OscServerMongoMessageDao(mongoTemplate);
    }

    @Bean
    @NonNull
    public OscApiFileResourceDao fileResourceDao(@NonNull MongoTemplate mongoTemplate) {
        return new OscServerMongoFileResourceDao(mongoTemplate);
    }

    @Bean
    @NonNull
    public OscApiRequestDao requestDao(@NonNull MongoTemplate mongoTemplate) {
        return new OscServerMongoRequestDao(mongoTemplate);
    }

    @Bean
    @NonNull
    public OscApiFriendLinkDao friendLinkDao(@NonNull MongoTemplate mongoTemplate) {
        return new OscServerMongoFriendLinkDao(mongoTemplate);
    }

}
