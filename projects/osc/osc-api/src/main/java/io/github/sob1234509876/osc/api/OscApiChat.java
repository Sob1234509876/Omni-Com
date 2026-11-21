package io.github.sob1234509876.osc.api;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.springframework.data.annotation.Id;

import java.time.Instant;

@Data
@NoArgsConstructor
public class OscApiChat {

    public static final String ID_FIELD = "id";
    public static final String USER_A_FIELD = "userAId";
    public static final String USER_B_FIELD = "userBId";
    public static final String CREATE_TIME_FIELD = "createTime";

    @Id
    private long id;
    private long userAId;
    private long userBId;
    @NonNull
    private Instant createTime;

    public OscApiChat(long userAId, long userBId) {
        this.userAId = userAId;
        this.userBId = userBId;
    }

}
