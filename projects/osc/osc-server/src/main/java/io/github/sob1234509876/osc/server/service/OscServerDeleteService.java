package io.github.sob1234509876.osc.server.service;

import io.github.sob1234509876.osc.api.OscApiChat;
import io.github.sob1234509876.osc.api.OscApiChatDao;
import io.github.sob1234509876.osc.api.OscApiChatUser;
import io.github.sob1234509876.osc.api.OscApiMessageDao;
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

import java.util.LinkedHashMap;

@Slf4j
@Data
@NoArgsConstructor
@RequiredArgsConstructor
@Service
public class OscServerDeleteService {

    @NonNull
    private OscApiChatDao chatDao;

    @NonNull
    private OscApiRoomDao roomDao;

    @NonNull
    private OscApiRoomUserDao roomUserDao;

    @NonNull
    private OscApiMessageDao messageDao;

    @NonNull
    private OscServerEventBusService oscServerEventBusService;

    @Nullable
    public ResponseEntity<OscApiChat> deleteChat(@NonNull OscApiChatUser me, long chatId) {
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

        var other = chat.getUserAId() == me.getId() ? chat.getUserBId() : chat.getUserAId();

        messageDao.deleteByChatId(chat.getId());
        chatDao.delete(chat);

        var event = new LinkedHashMap<String, Object>();

        event.put("event", "chat-delete");
        event.put("chatId", chat.getId());
        oscServerEventBusService.push(other, event);

        return ResponseEntity.ok(chat);
    }

    @Nullable
    public ResponseEntity<OscApiRoom> deleteRoom(@NonNull OscApiChatUser me, long roomId) {
        var roomRes = roomDao.findById(roomId);

        if (roomRes.isEmpty())
            return ResponseEntity.notFound()
                    .header("description", "Room not found")
                    .build();

        var room = roomRes.get();

        if (room.getOwnerId() != me.getId())
            return ResponseEntity.status(403)
                    .header("description", "Only the room owner can delete the room")
                    .build();

        var event = new LinkedHashMap<String, Object>();

        event.put("event", "room-delete");
        event.put("roomId", room.getId());

        for (var member : roomUserDao.findByRoomId(room.getId()))
            oscServerEventBusService.push(member.getUserId(), event);

        messageDao.deleteByRoomId(room.getId());
        roomUserDao.deleteByRoomId(room.getId());
        roomDao.delete(room);

        return ResponseEntity.ok(room);
    }

    @Nullable
    public ResponseEntity<OscApiRoomUser> removeUser(@NonNull OscApiChatUser me, long roomId, long userId) {
        var roomRes = roomDao.findById(roomId);

        if (roomRes.isEmpty())
            return ResponseEntity.notFound()
                    .header("description", "Room not found")
                    .build();

        var room = roomRes.get();

        if (userId == room.getOwnerId())
            return ResponseEntity.status(403)
                    .header("description", "The room owner cannot be removed")
                    .build();

        var targetRes = roomUserDao.findByRoomIdAndUserId(room.getId(), userId);

        if (targetRes.isEmpty())
            return ResponseEntity.notFound()
                    .header("description", "Room user not found")
                    .build();

        var target = targetRes.get();

        if (userId != me.getId()) {
            var actorRes = roomUserDao.findByRoomIdAndUserId(room.getId(), me.getId());

            if (actorRes.isEmpty() || (!actorRes.get()
                    .isAdmin() && me.getId() != room.getOwnerId()))
                return ResponseEntity.status(403)
                        .header("description", "Only the room owner or admins can remove users")
                        .build();
        }

        roomUserDao.delete(target);

        var event = new LinkedHashMap<String, Object>();

        event.put("event", "room-remove");
        event.put("roomId", room.getId());
        event.put("roomUser", target);
        oscServerEventBusService.push(userId, event);

        for (var member : roomUserDao.findByRoomId(room.getId()))
            oscServerEventBusService.push(member.getUserId(), event);

        return ResponseEntity.ok(target);
    }

}
