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
public class OscApiRoom {

    public static final String ID_FIELD = "id";
    public static final String NAME_FIELD = "name";
    public static final String OWNER_FIELD = "ownerId";
    public static final String CREATE_TIME_FIELD = "createTime";

    @Id
    private long id;
    @NonNull
    private String name;
    private long ownerId;
    @Nullable
    private String info;
    @NonNull
    private Instant createTime;

}
