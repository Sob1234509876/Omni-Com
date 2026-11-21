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
public class OscApiRoomUser {

    public static final String ID_FIELD = "id";
    public static final String ROOM_FIELD = "roomId";
    public static final String USER_FIELD = "userId";
    public static final String TITLE_FIELD = "title";
    public static final String POINTS_FIELD = "points";
    public static final String ADMIN_FIELD = "admin";
    public static final String JOIN_TIME_FIELD = "joinTime";
    public static final String MUTED_UNTIL_FIELD = "mutedUntil";
    public static final String LAST_CHECK_IN_FIELD = "lastCheckInTime";
    public static final String TODAY_MESSAGE_POINTS_FIELD = "todayMessagePoints";
    public static final String TODAY_MESSAGE_POINTS_DATE_FIELD = "todayMessagePointsDate";
    public static final String LEVEL_FIELD = "level";

    @Id
    private long id;
    private long roomId;
    private long userId;
    @Nullable
    private String title;
    private long points;
    private boolean admin;
    @NonNull
    private Instant joinTime;
    @Nullable
    private Instant mutedUntil;
    @Nullable
    private Instant lastCheckInTime;
    private int todayMessagePoints;
    @Nullable
    private Instant todayMessagePointsDate;
    private long level;

    public static long levelOf(long points) {
        var level = 0L;

        while ((level + 1) * (level + 1) + 4 * (level + 1) <= points)
            level++;

        return level;
    }

}
