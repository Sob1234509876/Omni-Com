package io.github.sob1234509876.osc.server.service;

import io.github.sob1234509876.osa.api.OsaApiUser;
import io.github.sob1234509876.osc.api.social.OscApiChat;
import io.github.sob1234509876.osc.api.social.OscApiChatUser;
import io.github.sob1234509876.osc.api.social.OscApiChatUserDao;
import io.github.sob1234509876.osc.api.social.OscApiChatDao;
import io.github.sob1234509876.osc.api.resource.OscApiFileResourceDao;
import io.github.sob1234509876.osc.api.social.OscApiFriendLinkDao;
import io.github.sob1234509876.osc.api.resource.OscApiMessage;
import io.github.sob1234509876.osc.api.resource.OscApiMessageDao;
import io.github.sob1234509876.osc.api.request.OscApiRequest;
import io.github.sob1234509876.osc.api.request.OscApiRequestDao;
import io.github.sob1234509876.osc.api.request.OscApiRequestType;
import io.github.sob1234509876.osc.api.social.OscApiRoom;
import io.github.sob1234509876.osc.api.social.OscApiRoomDao;
import io.github.sob1234509876.osc.api.social.OscApiRoomUser;
import io.github.sob1234509876.osc.api.social.OscApiRoomUserDao;
import io.github.sob1234509876.osc.server.component.OscServerPropertyComponent;
import io.github.sob1234509876.osftp.dao.ftp.FtpTemplate;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

@Slf4j
@Data
@NoArgsConstructor
@RequiredArgsConstructor
@Service
public class OscServerReadService {

    public static final int SEARCH_RESULT_LIMIT = 50;

    @NonNull
    private FtpTemplate ftpTemplate;

    @NonNull
    private OscApiChatUserDao chatUserDao;

    @NonNull
    private OscApiChatDao chatDao;

    @NonNull
    private OscApiRoomDao roomDao;

    @NonNull
    private OscApiRoomUserDao roomUserDao;

    @NonNull
    private OscApiMessageDao messageDao;

    @NonNull
    private OscApiFileResourceDao fileResourceDao;

    @NonNull
    private OscApiRequestDao requestDao;

    @NonNull
    private OscApiFriendLinkDao friendLinkDao;

    @NonNull
    private OscServerPropertyComponent oscServerPropertyComponent;

    @Nullable
    public ResponseEntity<OscApiChatUser> getChatUser(long id) {
        var res = chatUserDao.findById(id);

        if (res.isEmpty())
            return ResponseEntity.notFound()
                    .header("description", "Chat user not found")
                    .build();

        return ResponseEntity.ok(res.get());
    }

    @Nullable
    public ResponseEntity<OscApiChat> getChat(long id) {
        var res = chatDao.findById(id);

        if (res.isEmpty())
            return ResponseEntity.notFound()
                    .header("description", "Chat not found")
                    .build();

        return ResponseEntity.ok(res.get());
    }

    @Nullable
    public ResponseEntity<OscApiRoom> getRoom(long id) {
        var res = roomDao.findById(id);

        if (res.isEmpty())
            return ResponseEntity.notFound()
                    .header("description", "Room not found")
                    .build();

        return ResponseEntity.ok(res.get());
    }

    @Nullable
    public ResponseEntity<OscApiRoomUser> getRoomUser(long roomId, long userId) {
        var res = roomUserDao.findByRoomIdAndUserId(roomId, userId);

        if (res.isEmpty())
            return ResponseEntity.notFound()
                    .header("description", "Room user not found")
                    .build();

        return ResponseEntity.ok(res.get());
    }

    @Nullable
    public ResponseEntity<List<@NonNull OscApiRoomUser>> getRoomUsers(long roomId) {
        var roomRes = roomDao.findById(roomId);

        if (roomRes.isEmpty())
            return ResponseEntity.notFound()
                    .header("description", "Room not found")
                    .build();

        return ResponseEntity.ok(roomUserDao.findByRoomId(roomId));
    }

    @Nullable
    public ResponseEntity<List<@NonNull OscApiChatUser>> searchChatUsers(@NonNull String username) {
        var uri = UriComponentsBuilder.fromUriString(oscServerPropertyComponent.getOsaBaseUri() + OscServerPropertyComponent.USER_SEARCH_PATH)
                .queryParam("username", username)
                .build()
                .encode()
                .toUri();

        OsaApiUser[] users;
        try {
            users = new RestTemplate().getForObject(uri, OsaApiUser[].class);

        } catch (@NonNull Throwable t) {
            log.warn("OSA username search failed", t);
            return ResponseEntity.internalServerError()
                    .header("description", "OSA search failed")
                    .build();
        }

        var res = new ArrayList<OscApiChatUser>();

        if (users != null)
            for (var user : users) {
                var chatUser = chatUserDao.findById(user.getId());

                if (chatUser.isPresent())
                    res.add(chatUser.get());
            }

        return ResponseEntity.ok(res);
    }

    @NonNull
    public ResponseEntity<@NonNull List<@NonNull OscApiRoom>> searchRooms(@NonNull String name) {
        var res = roomDao.findByNameContaining(name, PageRequest.of(0, SEARCH_RESULT_LIMIT));

        return ResponseEntity.ok(res);
    }

    @Nullable
    public ResponseEntity<List<OscApiMessage>> getMessages(Long chatId, Long roomId, Long before, Long limit) {
        var targets = (chatId == null ? 0 : 1) + (roomId == null ? 0 : 1);

        if (targets != 1)
            return ResponseEntity.badRequest()
                    .header("description", "Exactly one of chatId or roomId is required")
                    .build();

        var pageLimit = oscServerPropertyComponent.getMessagePageSize();

        if (limit != null) {
            if (limit < 1)
                return ResponseEntity.badRequest()
                        .header("description", "Limit must be positive")
                        .build();

            pageLimit = Math.min(limit, pageLimit);
        }

        var page = PageRequest.of(0, (int) pageLimit);

        if (chatId != null) {
            if (!chatDao.existsById(chatId))
                return ResponseEntity.notFound()
                        .header("description", "Chat not found")
                        .build();

            var res = before == null ? messageDao.findByChatIdOrderByIdDesc(chatId, page) : messageDao.findByChatIdAndIdLessThanOrderByIdDesc(chatId, before, page);

            return ResponseEntity.ok(res);
        }

        if (!roomDao.existsById(roomId))
            return ResponseEntity.notFound()
                    .header("description", "Room not found")
                    .build();

        var res = before == null ? messageDao.findByRoomIdOrderByIdDesc(roomId, page) : messageDao.findByRoomIdAndIdLessThanOrderByIdDesc(roomId, before, page);

        return ResponseEntity.ok(res);
    }

    @Nullable
    public ResponseEntity<Resource> getResourceFile(long id) {
        var res = fileResourceDao.findById(id);

        if (res.isEmpty())
            return ResponseEntity.notFound()
                    .header("description", "File not found")
                    .build();

        var record = res.get();
        var data = ftpTemplate.readFile(record.getFtpPath());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(record.getContentType()))
                .body(new ByteArrayResource(data));
    }

    @Nullable
    public ResponseEntity<OscApiRequest> getRequest(@NonNull OscApiChatUser me, long requestId) {
        var res = requestDao.findById(requestId);

        if (res.isEmpty())
            return ResponseEntity.notFound()
                    .header("description", "Request not found")
                    .build();

        var request = res.get();

        if (!canSeeRequest(me, request))
            return ResponseEntity.status(403)
                    .header("description", "Not a request participant")
                    .build();

        return ResponseEntity.ok(request);
    }

    @NonNull
    public ResponseEntity<@NonNull List<@NonNull Long>> getRequests(@NonNull OscApiChatUser me) {
        var roomIds = new LinkedList<Long>();

        for (var roomUser : roomUserDao.findByUserId(me.getId()))
            roomIds.add(roomUser.getRoomId());

        var res = new LinkedList<Long>();

        for (var request : requestDao.findByFromIdOrTargetIdOrTargetIdIn(me.getId(), me.getId(), roomIds))
            res.add(request.getId());

        return ResponseEntity.ok(res);
    }

    @NonNull
    public ResponseEntity<@NonNull List<@NonNull Long>> getFriends(@NonNull OscApiChatUser me) {
        var res = new LinkedList<Long>();

        for (var link : friendLinkDao.findByUserId(me.getId()))
            res.add(link.getFriendId());

        return ResponseEntity.ok(res);
    }

    @NonNull
    public ResponseEntity<@NonNull List<@NonNull Long>> getChats(@NonNull OscApiChatUser me) {
        var res = new LinkedList<Long>();

        for (var chat : chatDao.findByUserAIdOrUserBId(me.getId(), me.getId()))
            res.add(chat.getId());

        return ResponseEntity.ok(res);
    }

    @NonNull
    public ResponseEntity<@NonNull List<@NonNull Long>> getRooms(@NonNull OscApiChatUser me) {
        var res = new LinkedList<Long>();

        for (var roomUser : roomUserDao.findByUserId(me.getId()))
            res.add(roomUser.getRoomId());

        return ResponseEntity.ok(res);
    }

    private boolean canSeeRequest(@NonNull OscApiChatUser me, @NonNull OscApiRequest request) {
        if (request.getFromId() == me.getId())
            return true;

        if (request.getType() == OscApiRequestType.BE_FRIENDS)
            return request.getTargetId() == me.getId();

        if (request.getType() == OscApiRequestType.JOIN_ROOM) {
            var roomRes = roomDao.findById(request.getTargetId());

            if (roomRes.isEmpty())
                return false;

            var room = roomRes.get();
            var actorRes = roomUserDao.findByRoomIdAndUserId(room.getId(), me.getId());

            return actorRes.isPresent() && (actorRes.get()
                    .isAdmin() || me.getId() == room.getOwnerId());
        }

        return false;
    }

}
