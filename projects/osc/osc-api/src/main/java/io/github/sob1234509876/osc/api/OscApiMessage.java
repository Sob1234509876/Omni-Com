package io.github.sob1234509876.osc.api;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.annotation.Id;

import java.time.Instant;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
public class OscApiMessage {

    public static final String ID_FIELD = "id";
    public static final String TYPE_FIELD = "type";
    public static final String CHAT_FIELD = "chatId";
    public static final String ROOM_FIELD = "roomId";
    public static final String SENDER_FIELD = "senderId";
    public static final String CONTENT_FIELD = "content";
    public static final String FILE_FIELD = "fileId";
    public static final String CREATE_TIME_FIELD = "createTime";

    @Id
    private long id;
    @Nullable
    private Long chatId;
    @Nullable
    private Long roomId;
    private long senderId;
    @NonNull
    private OscApiMessageType type;
    @Nullable
    private String content;
    @Nullable
    private Long fileId;
    @NonNull
    private Instant createTime;

    public boolean isAmbiguousTarget() {
        return (chatId == null) == (roomId == null);
    }

}
