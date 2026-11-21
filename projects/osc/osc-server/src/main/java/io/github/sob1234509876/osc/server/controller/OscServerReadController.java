package io.github.sob1234509876.osc.server.controller;

import io.github.sob1234509876.osc.api.OscApiChat;
import io.github.sob1234509876.osc.api.OscApiChatUser;
import io.github.sob1234509876.osc.api.OscApiMessage;
import io.github.sob1234509876.osc.api.OscApiRequest;
import io.github.sob1234509876.osc.api.OscApiRoom;
import io.github.sob1234509876.osc.api.OscApiRoomUser;
import io.github.sob1234509876.osc.server.service.OscServerReadService;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@Data
@NoArgsConstructor
@RequiredArgsConstructor
@RestController
public class OscServerReadController {

    @NonNull
    private OscServerReadService oscServerReadService;

    @GetMapping("/public/chat-user")
    @Nullable
    public ResponseEntity<OscApiChatUser> getChatUser(@RequestParam("id") long id) {
        return oscServerReadService.getChatUser(id);
    }

    @GetMapping("/public/chat")
    @Nullable
    public ResponseEntity<OscApiChat> getChat(@RequestParam("id") long id) {
        return oscServerReadService.getChat(id);
    }

    @GetMapping("/public/room")
    @Nullable
    public ResponseEntity<OscApiRoom> getRoom(@RequestParam("id") long id) {
        return oscServerReadService.getRoom(id);
    }

    @GetMapping("/public/room-user")
    @Nullable
    public ResponseEntity<OscApiRoomUser> getRoomUser(@RequestParam("roomId") long roomId, @RequestParam("userId") long userId) {
        return oscServerReadService.getRoomUser(roomId, userId);
    }

    @GetMapping("/public/search/chat-user")
    @Nullable
    public ResponseEntity<List<@NonNull OscApiChatUser>> searchChatUsers(@RequestParam("username") @NonNull String username) {
        return oscServerReadService.searchChatUsers(username);
    }

    @GetMapping("/public/search/room")
    @NonNull
    public ResponseEntity<@NonNull List<@NonNull OscApiRoom>> searchRooms(@RequestParam("name") @NonNull String name) {
        return oscServerReadService.searchRooms(name);
    }

    @GetMapping("/public/message")
    @Nullable
    public ResponseEntity<List<OscApiMessage>> getMessages(@RequestParam(value = "chatId", required = false) Long chatId,
                                                           @RequestParam(value = "roomId", required = false) Long roomId,
                                                           @RequestParam(value = "before", required = false) Long before,
                                                           @RequestParam(value = "limit", required = false) Long limit) {
        return oscServerReadService.getMessages(chatId, roomId, before, limit);
    }

    @GetMapping("/public/resource/file")
    @Nullable
    public ResponseEntity<Resource> getResourceFile(@RequestParam("id") long id) {
        return oscServerReadService.getResourceFile(id);
    }

    @GetMapping("/private/request")
    @Nullable
    public ResponseEntity<OscApiRequest> getRequest(@AuthenticationPrincipal @NonNull OscApiChatUser me,
                                                    @RequestParam("requestId") long requestId) {
        return oscServerReadService.getRequest(me, requestId);
    }

    @GetMapping("/private/request/requests")
    @NonNull
    public ResponseEntity<@NonNull List<@NonNull Long>> getRequests(@AuthenticationPrincipal @NonNull OscApiChatUser me) {
        return oscServerReadService.getRequests(me);
    }

    @GetMapping("/private/friends")
    @NonNull
    public ResponseEntity<@NonNull List<@NonNull Long>> getFriends(@AuthenticationPrincipal @NonNull OscApiChatUser me) {
        return oscServerReadService.getFriends(me);
    }

    @GetMapping("/private/chats")
    @NonNull
    public ResponseEntity<@NonNull List<@NonNull Long>> getChats(@AuthenticationPrincipal @NonNull OscApiChatUser me) {
        return oscServerReadService.getChats(me);
    }

    @GetMapping("/private/rooms")
    @NonNull
    public ResponseEntity<@NonNull List<@NonNull Long>> getRooms(@AuthenticationPrincipal @NonNull OscApiChatUser me) {
        return oscServerReadService.getRooms(me);
    }

}
