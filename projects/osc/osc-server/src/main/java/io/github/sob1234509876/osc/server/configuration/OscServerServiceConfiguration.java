package io.github.sob1234509876.osc.server.configuration;

import io.github.sob1234509876.osc.api.OscApiChatDao;
import io.github.sob1234509876.osc.api.OscApiChatUserDao;
import io.github.sob1234509876.osc.api.OscApiFileResourceDao;
import io.github.sob1234509876.osc.api.OscApiFriendLinkDao;
import io.github.sob1234509876.osc.api.OscApiMessageDao;
import io.github.sob1234509876.osc.api.OscApiRequestDao;
import io.github.sob1234509876.osc.api.OscApiRoomDao;
import io.github.sob1234509876.osc.api.OscApiRoomUserDao;
import io.github.sob1234509876.osc.server.component.OscServerPropertyComponent;
import io.github.sob1234509876.osc.server.service.OscServerCreateService;
import io.github.sob1234509876.osc.server.service.OscServerDeleteService;
import io.github.sob1234509876.osc.server.service.OscServerEventBusService;
import io.github.sob1234509876.osc.server.service.OscServerOsaJwksService;
import io.github.sob1234509876.osc.server.service.OscServerReadService;
import io.github.sob1234509876.osc.server.service.OscServerUpdateService;
import io.github.sob1234509876.osftp.dao.ftp.FtpTemplate;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Slf4j
@Data
@NoArgsConstructor
@Configuration
@Import({OscServerFtpConfiguration.class,
        OscServerMongoConfiguration.class})
public class OscServerServiceConfiguration {

    @Bean
    @NonNull
    public OscServerOsaJwksService oscServerOsaJwksService(@NonNull OscServerPropertyComponent oscServerPropertyComponent) {
        return new OscServerOsaJwksService(oscServerPropertyComponent);
    }

    @Bean
    @NonNull
    public OscServerEventBusService oscServerEventBusService() {
        return new OscServerEventBusService();
    }

    @Bean
    @NonNull
    public OscServerReadService oscServerReadService(@NonNull FtpTemplate ftpTemplate,
                                                     @NonNull OscApiChatUserDao chatUserDao,
                                                     @NonNull OscApiChatDao chatDao,
                                                     @NonNull OscApiRoomDao roomDao,
                                                     @NonNull OscApiRoomUserDao roomUserDao,
                                                     @NonNull OscApiMessageDao messageDao,
                                                     @NonNull OscApiFileResourceDao fileResourceDao,
                                                     @NonNull OscApiRequestDao requestDao,
                                                     @NonNull OscApiFriendLinkDao friendLinkDao,
                                                     @NonNull OscServerPropertyComponent oscServerPropertyComponent) {
        return new OscServerReadService(ftpTemplate, chatUserDao, chatDao, roomDao, roomUserDao, messageDao, fileResourceDao, requestDao, friendLinkDao, oscServerPropertyComponent);
    }

    @Bean
    @NonNull
    public OscServerCreateService oscServerCreateService(@NonNull FtpTemplate ftpTemplate,
                                                         @NonNull OscApiChatDao chatDao,
                                                         @NonNull OscApiRoomDao roomDao,
                                                         @NonNull OscApiRoomUserDao roomUserDao,
                                                         @NonNull OscApiMessageDao messageDao,
                                                         @NonNull OscApiFileResourceDao fileResourceDao,
                                                         @NonNull OscApiRequestDao requestDao,
                                                         @NonNull OscApiFriendLinkDao friendLinkDao,
                                                         @NonNull OscServerEventBusService oscServerEventBusService,
                                                         @NonNull OscServerPropertyComponent oscServerPropertyComponent) {
        return new OscServerCreateService(ftpTemplate, chatDao, roomDao, roomUserDao, messageDao, fileResourceDao, requestDao, friendLinkDao, oscServerEventBusService, oscServerPropertyComponent);
    }

    @Bean
    @NonNull
    public OscServerUpdateService oscServerUpdateService(@NonNull OscApiChatUserDao chatUserDao,
                                                         @NonNull OscApiRoomDao roomDao,
                                                         @NonNull OscApiRoomUserDao roomUserDao,
                                                         @NonNull OscApiRequestDao requestDao,
                                                         @NonNull OscApiFriendLinkDao friendLinkDao,
                                                         @NonNull OscServerEventBusService oscServerEventBusService) {
        return new OscServerUpdateService(chatUserDao, roomDao, roomUserDao, requestDao, friendLinkDao, oscServerEventBusService);
    }

    @Bean
    @NonNull
    public OscServerDeleteService oscServerDeleteService(@NonNull OscApiChatDao chatDao,
                                                         @NonNull OscApiRoomDao roomDao,
                                                         @NonNull OscApiRoomUserDao roomUserDao,
                                                         @NonNull OscApiMessageDao messageDao,
                                                         @NonNull OscServerEventBusService oscServerEventBusService) {
        return new OscServerDeleteService(chatDao, roomDao, roomUserDao, messageDao, oscServerEventBusService);
    }

}
