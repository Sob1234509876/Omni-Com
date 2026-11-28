package io.github.sob1234509876.osc.api.social;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

@Data
@NoArgsConstructor
public class OscApiFriendLink {

    public static final String ID_FIELD = "id";
    public static final String USER_FIELD = "userId";
    public static final String FRIEND_FIELD = "friendId";

    @Id
    private long id;
    private long userId;
    private long friendId;

    public OscApiFriendLink(long userId, long friendId) {
        this.userId = userId;
        this.friendId = friendId;
    }

}
