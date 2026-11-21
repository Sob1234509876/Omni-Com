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
public class OscApiRequest {

    public static final String ID_FIELD = "id";
    public static final String TYPE_FIELD = "type";
    public static final String FROM_FIELD = "fromId";
    public static final String TARGET_FIELD = "targetId";
    public static final String STATUS_FIELD = "status";
    public static final String CREATE_TIME_FIELD = "createTime";
    public static final String HANDLED_TIME_FIELD = "handledTime";

    @Id
    private long id;
    @NonNull
    private OscApiRequestType type;
    private long fromId;
    private long targetId;
    @NonNull
    private OscApiRequestStatus status;
    @NonNull
    private Instant createTime;
    @Nullable
    private Instant handledTime;

}
