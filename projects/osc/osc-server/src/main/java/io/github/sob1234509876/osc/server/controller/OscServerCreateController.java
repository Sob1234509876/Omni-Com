package io.github.sob1234509876.osc.server.controller;

import io.github.sob1234509876.osc.api.OscApiChatUser;
import io.github.sob1234509876.osc.api.OscApiFileResource;
import io.github.sob1234509876.osc.api.OscApiMessage;
import io.github.sob1234509876.osc.api.OscApiRequest;
import io.github.sob1234509876.osc.api.OscApiRoom;
import io.github.sob1234509876.osc.server.service.OscServerCreateService;
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
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Data
@NoArgsConstructor
@RequiredArgsConstructor
@RestController
public class OscServerCreateController {

    @NonNull
    private OscServerCreateService oscServerCreateService;

    @PostMapping("/private/room/create-room")
    @Nullable
    public ResponseEntity<OscApiRoom> createRoom(@AuthenticationPrincipal @NonNull OscApiChatUser me,
                                                 @RequestParam("name") @NonNull String name,
                                                 @RequestParam(value = "info", required = false) String info) {
        return oscServerCreateService.createRoom(me, name, info);
    }

    @PostMapping("/private/upload-message")
    @Nullable
    public ResponseEntity<OscApiMessage> uploadMessage(@AuthenticationPrincipal @NonNull OscApiChatUser me,
                                                       @RequestParam(value = "userId", required = false) Long userId,
                                                       @RequestParam(value = "chatId", required = false) Long chatId,
                                                       @RequestParam(value = "roomId", required = false) Long roomId,
                                                       @RequestParam("type") @NonNull String type,
                                                       @RequestParam(value = "content", required = false) String content,
                                                       @RequestParam(value = "fileId", required = false) Long fileId) {
        return oscServerCreateService.uploadMessage(me, userId, chatId, roomId, type, content, fileId);
    }

    @PostMapping("/private/upload-file")
    @Nullable
    public ResponseEntity<OscApiFileResource> uploadFile(@AuthenticationPrincipal @NonNull OscApiChatUser me,
                                                         @RequestParam("file") @NonNull MultipartFile file) {
        return oscServerCreateService.uploadFile(me, file);
    }

    @PostMapping("/private/request/operation/request-be-friends")
    @Nullable
    public ResponseEntity<OscApiRequest> requestBeFriends(@AuthenticationPrincipal @NonNull OscApiChatUser me,
                                                          @RequestParam("userId") long userId) {
        return oscServerCreateService.requestBeFriends(me, userId);
    }

    @PostMapping("/private/request/operation/request-join-room")
    @Nullable
    public ResponseEntity<OscApiRequest> requestJoinRoom(@AuthenticationPrincipal @NonNull OscApiChatUser me,
                                                         @RequestParam("roomId") long roomId) {
        return oscServerCreateService.requestJoinRoom(me, roomId);
    }

}
