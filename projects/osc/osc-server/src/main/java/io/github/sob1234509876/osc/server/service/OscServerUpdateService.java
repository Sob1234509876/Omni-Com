package io.github.sob1234509876.osc.server.service;

import io.github.sob1234509876.osc.api.OscApiChatUser;
import io.github.sob1234509876.osc.api.OscApiChatUserDao;
import io.github.sob1234509876.osc.api.OscApiFriendLink;
import io.github.sob1234509876.osc.api.OscApiFriendLinkDao;
import io.github.sob1234509876.osc.api.OscApiRequest;
import io.github.sob1234509876.osc.api.OscApiRequestDao;
import io.github.sob1234509876.osc.api.OscApiRequestStatus;
import io.github.sob1234509876.osc.api.OscApiRequestType;
import io.github.sob1234509876.osc.api.OscApiRoom;
import io.github.sob1234509876.osc.api.OscApiRoomDao;
import io.github.sob1234509876.osc.api.OscApiRoomUser;
import io.github.sob1234509876.osc.api.OscApiRoomUserDao;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Data
@NoArgsConstructor
@RequiredArgsConstructor
@Service
public class OscServerUpdateService {

    public static final long CHECK_IN_POINTS = 2;

    @NonNull
    private OscApiChatUserDao chatUserDao;

    @NonNull
    private OscApiRoomDao roomDao;

    @NonNull
    private OscApiRoomUserDao roomUserDao;

    @NonNull
    private OscApiRequestDao requestDao;

    @NonNull
    private OscApiFriendLinkDao friendLinkDao;

    @NonNull
    private OscServerEventBusService oscServerEventBusService;

    @NonNull
    public ResponseEntity<@NonNull OscApiChatUser> updateChatUser(@NonNull OscApiChatUser me, String bio, Long avatarFileId) {
        if (bio != null)
            me.setBio(bio);

        if (avatarFileId != null)
            me.setAvatarFileId(avatarFileId);

        var res = chatUserDao.save(me);

        return ResponseEntity.ok(res);
    }

    @Nullable
    public ResponseEntity<OscApiRoom> updateRoom(@NonNull OscApiChatUser me, long roomId, String name, String info) {
        var roomRes = roomDao.findById(roomId);

        if (roomRes.isEmpty())
            return ResponseEntity.notFound()
                    .header("description", "Room not found")
                    .build();

        var room = roomRes.get();

        if (room.getOwnerId() != me.getId())
            return ResponseEntity.status(403)
                    .header("description", "Only the room owner can update the room")
                    .build();

        if (name != null) {
            if (name.isBlank())
                return ResponseEntity.badRequest()
                        .header("description", "Room name is required")
                        .build();

            room.setName(name);
        }

        if (info != null)
            room.setInfo(info);

        room = roomDao.save(room);

        var event = new LinkedHashMap<String, Object>();

        event.put("event", "room-update");
        event.put("room", room);

        for (var member : roomUserDao.findByRoomId(room.getId()))
            oscServerEventBusService.push(member.getUserId(), event);

        return ResponseEntity.ok(room);
    }

    @Nullable
    public ResponseEntity<OscApiRoomUser> updateRoomUser(@NonNull OscApiChatUser me, long roomId, long userId, String title, Boolean admin) {
        var roomRes = roomDao.findById(roomId);

        if (roomRes.isEmpty())
            return ResponseEntity.notFound()
                    .header("description", "Room not found")
                    .build();

        var room = roomRes.get();

        if (room.getOwnerId() != me.getId())
            return ResponseEntity.status(403)
                    .header("description", "Only the room owner can update room users")
                    .build();

        if (userId == room.getOwnerId())
            return ResponseEntity.badRequest()
                    .header("description", "Cannot modify the room owner")
                    .build();

        var targetRes = roomUserDao.findByRoomIdAndUserId(room.getId(), userId);

        if (targetRes.isEmpty())
            return ResponseEntity.notFound()
                    .header("description", "Room user not found")
                    .build();

        var target = targetRes.get();

        if (title != null)
            target.setTitle(title);

        if (admin != null)
            target.setAdmin(admin);

        target = roomUserDao.save(target);

        var event = new LinkedHashMap<String, Object>();

        event.put("event", "room-user-update");
        event.put("roomId", room.getId());
        event.put("roomUser", target);

        for (var member : roomUserDao.findByRoomId(room.getId()))
            oscServerEventBusService.push(member.getUserId(), event);

        return ResponseEntity.ok(target);
    }

    @Nullable
    public ResponseEntity<OscApiRoomUser> checkIn(@NonNull OscApiChatUser me, long roomId) {
        var roomUserRes = roomUserDao.findByRoomIdAndUserId(roomId, me.getId());

        if (roomUserRes.isEmpty())
            return ResponseEntity.status(403)
                    .header("description", "Not a room member")
                    .build();

        var roomUser = roomUserRes.get();
        var now = Instant.now();

        if (roomUser.getLastCheckInTime() != null && roomUser.getLastCheckInTime()
                .truncatedTo(ChronoUnit.DAYS)
                .equals(now.truncatedTo(ChronoUnit.DAYS)))
            return ResponseEntity.badRequest()
                    .header("description", "Already checked in today")
                    .build();

        roomUser.setLastCheckInTime(now);
        roomUser.setPoints(roomUser.getPoints() + CHECK_IN_POINTS);
        roomUser.setLevel(OscApiRoomUser.levelOf(roomUser.getPoints()));
        roomUser = roomUserDao.save(roomUser);

        return ResponseEntity.ok(roomUser);
    }

    @Nullable
    public ResponseEntity<OscApiRoomUser> mute(@NonNull OscApiChatUser me, long roomId, long userId, Long minutes) {
        var roomRes = roomDao.findById(roomId);

        if (roomRes.isEmpty())
            return ResponseEntity.notFound()
                    .header("description", "Room not found")
                    .build();

        var room = roomRes.get();
        var actorRes = roomUserDao.findByRoomIdAndUserId(room.getId(), me.getId());

        if (actorRes.isEmpty() || (!actorRes.get()
                .isAdmin() && me.getId() != room.getOwnerId()))
            return ResponseEntity.status(403)
                    .header("description", "Only the room owner or admins can mute users")
                    .build();

        if (userId == room.getOwnerId())
            return ResponseEntity.status(403)
                    .header("description", "Cannot mute the room owner")
                    .build();

        var targetRes = roomUserDao.findByRoomIdAndUserId(room.getId(), userId);

        if (targetRes.isEmpty())
            return ResponseEntity.notFound()
                    .header("description", "Room user not found")
                    .build();

        if (minutes == null || minutes < 0)
            return ResponseEntity.badRequest()
                    .header("description", "Invalid mute duration")
                    .build();

        var target = targetRes.get();

        if (minutes == 0)
            target.setMutedUntil(null);
        else
            target.setMutedUntil(Instant.now()
                    .plusSeconds(minutes * 60));

        target = roomUserDao.save(target);

        var event = new LinkedHashMap<String, Object>();

        event.put("event", "room-mute");
        event.put("roomId", room.getId());
        event.put("roomUser", target);

        for (var member : roomUserDao.findByRoomId(room.getId()))
            oscServerEventBusService.push(member.getUserId(), event);

        return ResponseEntity.ok(target);
    }

    @Nullable
    public ResponseEntity<OscApiRoomUser> giveNickname(@NonNull OscApiChatUser me, long roomId, long userId, @NonNull String title) {
        var roomRes = roomDao.findById(roomId);

        if (roomRes.isEmpty())
            return ResponseEntity.notFound()
                    .header("description", "Room not found")
                    .build();

        var room = roomRes.get();

        if (room.getOwnerId() != me.getId())
            return ResponseEntity.status(403)
                    .header("description", "Only the room owner can give nicknames")
                    .build();

        var targetRes = roomUserDao.findByRoomIdAndUserId(room.getId(), userId);

        if (targetRes.isEmpty())
            return ResponseEntity.notFound()
                    .header("description", "Room user not found")
                    .build();

        var target = targetRes.get();

        target.setTitle(title);
        target = roomUserDao.save(target);

        var event = new LinkedHashMap<String, Object>();

        event.put("event", "room-user-update");
        event.put("roomId", room.getId());
        event.put("roomUser", target);

        for (var member : roomUserDao.findByRoomId(room.getId()))
            oscServerEventBusService.push(member.getUserId(), event);

        return ResponseEntity.ok(target);
    }

    @Nullable
    public ResponseEntity<OscApiRequest> acceptRequest(@NonNull OscApiChatUser me, long requestId) {
        var requestRes = requestDao.findById(requestId);

        if (requestRes.isEmpty())
            return ResponseEntity.notFound()
                    .header("description", "Request not found")
                    .build();

        var request = requestRes.get();

        if (request.getType() == OscApiRequestType.BE_FRIENDS && request.getTargetId() != me.getId())
            return ResponseEntity.status(403)
                    .header("description", "Only the request target can handle it")
                    .build();

        OscApiRoom room = null;

        if (request.getType() == OscApiRequestType.JOIN_ROOM) {
            var roomRes = roomDao.findById(request.getTargetId());

            if (roomRes.isEmpty())
                return ResponseEntity.badRequest()
                        .header("description", "Room no longer exists")
                        .build();

            room = roomRes.get();

            var actorRes = roomUserDao.findByRoomIdAndUserId(room.getId(), me.getId());

            if (actorRes.isEmpty() || (!actorRes.get()
                    .isAdmin() && me.getId() != room.getOwnerId()))
                return ResponseEntity.status(403)
                        .header("description", "Only the room owner or admins can handle join requests")
                        .build();
        }

        if (request.getStatus() != OscApiRequestStatus.PENDING)
            return ResponseEntity.badRequest()
                    .header("description", "Request already handled")
                    .build();

        if (request.getType() == OscApiRequestType.BE_FRIENDS && friendLinkDao.findByUserIdAndFriendId(me.getId(), request.getFromId())
                .isEmpty()) {
            var linkTime = System.currentTimeMillis();
            var direct = new OscApiFriendLink(me.getId(), request.getFromId());

            direct.setId(linkTime);
            friendLinkDao.save(direct);

            var reverse = new OscApiFriendLink(request.getFromId(), me.getId());

            reverse.setId(linkTime + 1);
            friendLinkDao.save(reverse);
        }

        if (request.getType() == OscApiRequestType.JOIN_ROOM && roomUserDao.findByRoomIdAndUserId(room.getId(), request.getFromId())
                .isEmpty()) {
            var roomUser = new OscApiRoomUser(Instant.now());

            roomUser.setId(System.currentTimeMillis());
            roomUser.setRoomId(room.getId());
            roomUser.setUserId(request.getFromId());
            roomUser.setLevel(0);
            roomUserDao.save(roomUser);
        }

        request.setStatus(OscApiRequestStatus.ACCEPTED);
        request.setHandledTime(Instant.now());
        request = requestDao.save(request);

        var event = new LinkedHashMap<String, Object>();

        event.put("event", "request");
        event.put("request", request);
        oscServerEventBusService.push(request.getFromId(), event);

        return ResponseEntity.ok(request);
    }

    @Nullable
    public ResponseEntity<OscApiRequest> rejectRequest(@NonNull OscApiChatUser me, long requestId) {
        var requestRes = requestDao.findById(requestId);

        if (requestRes.isEmpty())
            return ResponseEntity.notFound()
                    .header("description", "Request not found")
                    .build();

        var request = requestRes.get();

        if (request.getType() == OscApiRequestType.BE_FRIENDS && request.getTargetId() != me.getId())
            return ResponseEntity.status(403)
                    .header("description", "Only the request target can handle it")
                    .build();

        if (request.getType() == OscApiRequestType.JOIN_ROOM) {
            var roomRes = roomDao.findById(request.getTargetId());

            if (roomRes.isEmpty())
                return ResponseEntity.badRequest()
                        .header("description", "Room no longer exists")
                        .build();

            var room = roomRes.get();
            var actorRes = roomUserDao.findByRoomIdAndUserId(room.getId(), me.getId());

            if (actorRes.isEmpty() || (!actorRes.get()
                    .isAdmin() && me.getId() != room.getOwnerId()))
                return ResponseEntity.status(403)
                        .header("description", "Only the room owner or admins can handle join requests")
                        .build();
        }

        if (request.getStatus() != OscApiRequestStatus.PENDING)
            return ResponseEntity.badRequest()
                    .header("description", "Request already handled")
                    .build();

        request.setStatus(OscApiRequestStatus.REJECTED);
        request.setHandledTime(Instant.now());
        request = requestDao.save(request);

        var event = new LinkedHashMap<String, Object>();

        event.put("event", "request");
        event.put("request", request);
        oscServerEventBusService.push(request.getFromId(), event);

        return ResponseEntity.ok(request);
    }

}
