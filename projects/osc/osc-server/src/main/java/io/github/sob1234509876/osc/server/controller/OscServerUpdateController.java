package io.github.sob1234509876.osc.server.controller;

import io.github.sob1234509876.osc.api.OscApiChatUser;
import io.github.sob1234509876.osc.api.OscApiRequest;
import io.github.sob1234509876.osc.api.OscApiRoom;
import io.github.sob1234509876.osc.api.OscApiRoomUser;
import io.github.sob1234509876.osc.server.service.OscServerUpdateService;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Data
@NoArgsConstructor
@RequiredArgsConstructor
@RestController
public class OscServerUpdateController {

    @NonNull
    private OscServerUpdateService oscServerUpdateService;

    @PostMapping("/private/update-chat-user")
    @NonNull
    public ResponseEntity<@NonNull OscApiChatUser> updateChatUser(@AuthenticationPrincipal @NonNull OscApiChatUser me,
                                                                  @RequestParam(value = "bio", required = false) String bio) {
        return oscServerUpdateService.updateChatUser(me, bio);
    }

    @PostMapping("/private/room/update-room")
    @Nullable
    public ResponseEntity<OscApiRoom> updateRoom(@AuthenticationPrincipal @NonNull OscApiChatUser me,
                                                 @RequestParam("roomId") long roomId,
                                                 @RequestParam(value = "name", required = false) String name,
                                                 @RequestParam(value = "info", required = false) String info) {
        return oscServerUpdateService.updateRoom(me, roomId, name, info);
    }

    @PostMapping("/private/room/update-user")
    @Nullable
    public ResponseEntity<OscApiRoomUser> updateRoomUser(@AuthenticationPrincipal @NonNull OscApiChatUser me,
                                                         @RequestParam("roomId") long roomId,
                                                         @RequestParam("userId") long userId,
                                                         @RequestParam(value = "title", required = false) String title,
                                                         @RequestParam(value = "admin", required = false) Boolean admin) {
        return oscServerUpdateService.updateRoomUser(me, roomId, userId, title, admin);
    }

    @PostMapping("/private/room/check-in")
    @Nullable
    public ResponseEntity<OscApiRoomUser> checkIn(@AuthenticationPrincipal @NonNull OscApiChatUser me,
                                                  @RequestParam("roomId") long roomId) {
        return oscServerUpdateService.checkIn(me, roomId);
    }

    @PostMapping("/private/room/mute")
    @Nullable
    public ResponseEntity<OscApiRoomUser> mute(@AuthenticationPrincipal @NonNull OscApiChatUser me,
                                               @RequestParam("roomId") long roomId,
                                               @RequestParam("userId") long userId,
                                               @RequestParam("minutes") Long minutes) {
        return oscServerUpdateService.mute(me, roomId, userId, minutes);
    }

    @PostMapping("/private/room/give-nickname")
    @Nullable
    public ResponseEntity<OscApiRoomUser> giveNickname(@AuthenticationPrincipal @NonNull OscApiChatUser me,
                                                       @RequestParam("roomId") long roomId,
                                                       @RequestParam("userId") long userId,
                                                       @RequestParam("title") @NonNull String title) {
        return oscServerUpdateService.giveNickname(me, roomId, userId, title);
    }

    @PostMapping("/private/request/accept-request")
    @Nullable
    public ResponseEntity<OscApiRequest> acceptRequest(@AuthenticationPrincipal @NonNull OscApiChatUser me,
                                                       @RequestParam("requestId") long requestId) {
        return oscServerUpdateService.acceptRequest(me, requestId);
    }

    @PostMapping("/private/request/reject-request")
    @Nullable
    public ResponseEntity<OscApiRequest> rejectRequest(@AuthenticationPrincipal @NonNull OscApiChatUser me,
                                                       @RequestParam("requestId") long requestId) {
        return oscServerUpdateService.rejectRequest(me, requestId);
    }

}
