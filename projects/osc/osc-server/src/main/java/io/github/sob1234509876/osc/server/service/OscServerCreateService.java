package io.github.sob1234509876.osc.server.service;

import io.github.sob1234509876.osc.api.social.OscApiChat;
import io.github.sob1234509876.osc.api.social.OscApiChatUser;
import io.github.sob1234509876.osc.api.social.OscApiChatDao;
import io.github.sob1234509876.osc.api.resource.OscApiFileResource;
import io.github.sob1234509876.osc.api.resource.OscApiFileResourceDao;
import io.github.sob1234509876.osc.api.social.OscApiFriendLinkDao;
import io.github.sob1234509876.osc.api.resource.OscApiMessage;
import io.github.sob1234509876.osc.api.resource.OscApiMessageDao;
import io.github.sob1234509876.osc.api.resource.OscApiMessageType;
import io.github.sob1234509876.osc.api.request.OscApiRequest;
import io.github.sob1234509876.osc.api.request.OscApiRequestDao;
import io.github.sob1234509876.osc.api.request.OscApiRequestStatus;
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
import org.springframework.beans.factory.InitializingBean;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;

@Slf4j
@Data
@NoArgsConstructor
@RequiredArgsConstructor
@Service
public class OscServerCreateService implements InitializingBean {

    public static final long CHECK_IN_POINTS = 2;
    public static final int DAILY_MESSAGE_POINTS_LIMIT = 2;

    @NonNull
    private FtpTemplate ftpTemplate;

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
    private OscServerEventBusService oscServerEventBusService;

    @NonNull
    private OscServerPropertyComponent oscServerPropertyComponent;

    @Override
    public void afterPropertiesSet() {
        ftpTemplate.makeDirectories(oscServerPropertyComponent.getFileFtpPathPrefix());
    }

    @Nullable
    public ResponseEntity<OscApiRoom> createRoom(@NonNull OscApiChatUser me, @NonNull String name, String info) {
        if (name.isBlank())
            return ResponseEntity.badRequest()
                    .header("description", "Room name is required")
                    .build();

        var now = Instant.now();
        var room = new OscApiRoom(name, now);

        room.setId(System.currentTimeMillis());
        room.setOwnerId(me.getId());
        room.setInfo(info);
        room = roomDao.save(room);

        var roomUser = new OscApiRoomUser(now);

        roomUser.setId(System.currentTimeMillis());
        roomUser.setRoomId(room.getId());
        roomUser.setUserId(me.getId());
        roomUser.setLevel(0);
        roomUserDao.save(roomUser);

        return ResponseEntity.ok(room);
    }

    @Nullable
    public ResponseEntity<OscApiMessage> uploadMessage(@NonNull OscApiChatUser me, Long userId, Long chatId, Long roomId, @NonNull String type, String content, Long fileId) {
        OscApiMessageType messageType;
        try {
            messageType = OscApiMessageType.valueOf(type);
        } catch (@NonNull IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .header("description", "Unknown message type")
                    .build();
        }

        var targets = (userId == null ? 0 : 1) + (chatId == null ? 0 : 1) + (roomId == null ? 0 : 1);

        if (targets != 1)
            return ResponseEntity.badRequest()
                    .header("description", "Exactly one of userId, chatId or roomId is required")
                    .build();

        if (messageType == OscApiMessageType.TEXT && (content == null || content.isBlank()))
            return ResponseEntity.badRequest()
                    .header("description", "Text messages require content")
                    .build();

        if (messageType != OscApiMessageType.TEXT && fileId == null)
            return ResponseEntity.badRequest()
                    .header("description", "Non-text messages require a fileId")
                    .build();

        if (userId != null)
            return uploadChatMessage(me, userId, messageType, content, fileId);

        if (chatId != null)
            return uploadExistingChatMessage(me, chatId, messageType, content, fileId);

        return uploadRoomMessage(me, roomId, messageType, content, fileId);
    }

    @Nullable
    private ResponseEntity<OscApiMessage> uploadChatMessage(@NonNull OscApiChatUser me, long userId, @NonNull OscApiMessageType messageType, String content, Long fileId) {
        if (userId == me.getId())
            return ResponseEntity.badRequest()
                    .header("description", "Cannot message yourself")
                    .build();

        var userAId = Math.min(me.getId(), userId);
        var userBId = Math.max(me.getId(), userId);
        var chatRes = chatDao.findByUserAIdAndUserBId(userAId, userBId);
        OscApiChat chat;

        if (chatRes.isEmpty()) {
            var now = Instant.now();
            chat = new OscApiChat(userAId, userBId);
            chat.setId(System.currentTimeMillis());
            chat.setCreateTime(now);
            chat = chatDao.save(chat);
        } else
            chat = chatRes.get();

        var message = buildMessage(messageType, content, fileId, me.getId(), chat.getId(), null);
        message = messageDao.save(message);

        var other = chat.getUserAId() == me.getId() ? chat.getUserBId() : chat.getUserAId();
        var event = new LinkedHashMap<String, Object>();

        event.put("event", "message");
        event.put("chatId", chat.getId());
        event.put("message", message);
        oscServerEventBusService.push(other, event);

        return ResponseEntity.ok(message);
    }

    @Nullable
    private ResponseEntity<OscApiMessage> uploadExistingChatMessage(@NonNull OscApiChatUser me, long chatId, @NonNull OscApiMessageType messageType, String content, Long fileId) {
        var chatRes = chatDao.findById(chatId);

        if (chatRes.isEmpty())
            return ResponseEntity.notFound()
                    .header("description", "Chat not found")
                    .build();

        var chat = chatRes.get();

        if (chat.getUserAId() != me.getId() && chat.getUserBId() != me.getId())
            return ResponseEntity.status(403)
                    .header("description", "Not a chat participant")
                    .build();

        var message = buildMessage(messageType, content, fileId, me.getId(), chat.getId(), null);
        message = messageDao.save(message);

        var other = chat.getUserAId() == me.getId() ? chat.getUserBId() : chat.getUserAId();
        var event = new LinkedHashMap<String, Object>();

        event.put("event", "message");
        event.put("chatId", chat.getId());
        event.put("message", message);
        oscServerEventBusService.push(other, event);

        return ResponseEntity.ok(message);
    }

    @Nullable
    private ResponseEntity<OscApiMessage> uploadRoomMessage(@NonNull OscApiChatUser me, long roomId, @NonNull OscApiMessageType messageType, String content, Long fileId) {
        var roomRes = roomDao.findById(roomId);

        if (roomRes.isEmpty())
            return ResponseEntity.notFound()
                    .header("description", "Room not found")
                    .build();

        var roomUserRes = roomUserDao.findByRoomIdAndUserId(roomId, me.getId());

        if (roomUserRes.isEmpty())
            return ResponseEntity.status(403)
                    .header("description", "Not a room member")
                    .build();

        var roomUser = roomUserRes.get();
        var now = Instant.now();

        if (roomUser.getMutedUntil() != null && roomUser.getMutedUntil().isAfter(now))
            return ResponseEntity.status(403)
                    .header("description", "You are muted in this room")
                    .build();

        var message = buildMessage(messageType, content, fileId, me.getId(), null, roomId);
        message = messageDao.save(message);

        var today = now.truncatedTo(ChronoUnit.DAYS);

        if (roomUser.getTodayMessagePointsDate() == null || !today.equals(roomUser.getTodayMessagePointsDate())) {
            roomUser.setTodayMessagePointsDate(today);
            roomUser.setTodayMessagePoints(0);
        }

        if (roomUser.getTodayMessagePoints() < DAILY_MESSAGE_POINTS_LIMIT) {
            roomUser.setTodayMessagePoints(roomUser.getTodayMessagePoints() + 1);
            roomUser.setPoints(roomUser.getPoints() + 1);
            roomUser.setLevel(OscApiRoomUser.levelOf(roomUser.getPoints()));
            roomUserDao.save(roomUser);
        }

        var event = new LinkedHashMap<String, Object>();

        event.put("event", "message");
        event.put("roomId", roomId);
        event.put("message", message);

        for (var member : roomUserDao.findByRoomId(roomId)) {
            if (member.getUserId() == me.getId())
                continue;

            oscServerEventBusService.push(member.getUserId(), event);
        }

        return ResponseEntity.ok(message);
    }

    @NonNull
    private OscApiMessage buildMessage(@NonNull OscApiMessageType type, String content, Long fileId, long senderId, Long chatId, Long roomId) {
        var message = new OscApiMessage(type, Instant.now());

        message.setId(System.currentTimeMillis());
        message.setChatId(chatId);
        message.setRoomId(roomId);
        message.setSenderId(senderId);
        message.setContent(content);
        message.setFileId(fileId);

        return message;
    }

    @Nullable
    public ResponseEntity<OscApiFileResource> uploadFile(@NonNull OscApiChatUser me, @NonNull MultipartFile file) {
        var id = System.currentTimeMillis();
        var name = file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename();
        var contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        var path = oscServerPropertyComponent.getFileFtpPathPrefix() + "/" + id;

        byte @NonNull [] data;
        try {
            data = file.getBytes();
        } catch (@NonNull IOException e) {
            return ResponseEntity.internalServerError()
                    .header("description", "Failed to read the uploaded file")
                    .build();
        }

        ftpTemplate.writeFile(path, data);

        var record = new OscApiFileResource(name, contentType, path, Instant.now());

        record.setId(id);
        record.setUploaderId(me.getId());
        record.setSize(file.getSize());
        fileResourceDao.save(record);

        return ResponseEntity.ok(record);
    }

    @Nullable
    public ResponseEntity<OscApiRequest> requestBeFriends(@NonNull OscApiChatUser me, long userId) {
        if (userId == me.getId())
            return ResponseEntity.badRequest()
                    .header("description", "Cannot befriend yourself")
                    .build();

        if (friendLinkDao.findByUserIdAndFriendId(me.getId(), userId).isPresent())
            return ResponseEntity.badRequest()
                    .header("description", "Already friends")
                    .build();

        var dup = requestDao.findByFromIdAndTargetIdAndTypeAndStatus(me.getId(), userId, OscApiRequestType.BE_FRIENDS, OscApiRequestStatus.PENDING);

        if (dup.isPresent())
            return ResponseEntity.badRequest()
                    .header("description", "Request already exists")
                    .build();

        var request = new OscApiRequest(OscApiRequestType.BE_FRIENDS, OscApiRequestStatus.PENDING, Instant.now());

        request.setId(System.currentTimeMillis());
        request.setFromId(me.getId());
        request.setTargetId(userId);
        request = requestDao.save(request);

        var event = new LinkedHashMap<String, Object>();

        event.put("event", "request");
        event.put("request", request);
        oscServerEventBusService.push(userId, event);

        return ResponseEntity.ok(request);
    }

    @Nullable
    public ResponseEntity<OscApiRequest> requestJoinRoom(@NonNull OscApiChatUser me, long roomId) {
        var roomRes = roomDao.findById(roomId);

        if (roomRes.isEmpty())
            return ResponseEntity.notFound()
                    .header("description", "Room not found")
                    .build();

        var room = roomRes.get();

        if (roomUserDao.findByRoomIdAndUserId(room.getId(), me.getId()).isPresent())
            return ResponseEntity.badRequest()
                    .header("description", "Already a room member")
                    .build();

        var dup = requestDao.findByFromIdAndTargetIdAndTypeAndStatus(me.getId(), room.getId(), OscApiRequestType.JOIN_ROOM, OscApiRequestStatus.PENDING);

        if (dup.isPresent())
            return ResponseEntity.badRequest()
                    .header("description", "Request already exists")
                    .build();

        var request = new OscApiRequest(OscApiRequestType.JOIN_ROOM, OscApiRequestStatus.PENDING, Instant.now());

        request.setId(System.currentTimeMillis());
        request.setFromId(me.getId());
        request.setTargetId(room.getId());
        request = requestDao.save(request);

        var event = new LinkedHashMap<String, Object>();

        event.put("event", "request");
        event.put("request", request);

        for (var member : roomUserDao.findByRoomId(room.getId())) {
            if (member.isAdmin() && member.getUserId() != room.getOwnerId())
                oscServerEventBusService.push(member.getUserId(), event);
        }

        oscServerEventBusService.push(room.getOwnerId(), event);

        return ResponseEntity.ok(request);
    }

}
