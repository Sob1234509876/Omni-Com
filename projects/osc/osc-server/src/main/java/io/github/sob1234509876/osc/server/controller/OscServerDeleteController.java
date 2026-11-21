package io.github.sob1234509876.osc.server.controller;

import io.github.sob1234509876.osc.api.OscApiChat;
import io.github.sob1234509876.osc.api.OscApiChatUser;
import io.github.sob1234509876.osc.api.OscApiRoom;
import io.github.sob1234509876.osc.api.OscApiRoomUser;
import io.github.sob1234509876.osc.server.service.OscServerDeleteService;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Data
@NoArgsConstructor
@RequiredArgsConstructor
@RestController
public class OscServerDeleteController {

    @NonNull
    private OscServerDeleteService oscServerDeleteService;

    @DeleteMapping("/private/chat/delete-chat")
    @Nullable
    public ResponseEntity<OscApiChat> deleteChat(@AuthenticationPrincipal @NonNull OscApiChatUser me,
                                                 @RequestParam("chatId") long chatId) {
        return oscServerDeleteService.deleteChat(me, chatId);
    }

    @DeleteMapping("/private/room/delete-room")
    @Nullable
    public ResponseEntity<OscApiRoom> deleteRoom(@AuthenticationPrincipal @NonNull OscApiChatUser me,
                                                 @RequestParam("roomId") long roomId) {
        return oscServerDeleteService.deleteRoom(me, roomId);
    }

    @DeleteMapping("/private/room/remove-user")
    @Nullable
    public ResponseEntity<OscApiRoomUser> removeUser(@AuthenticationPrincipal @NonNull OscApiChatUser me,
                                                     @RequestParam("roomId") long roomId,
                                                     @RequestParam("userId") long userId) {
        return oscServerDeleteService.removeUser(me, roomId, userId);
    }

}
