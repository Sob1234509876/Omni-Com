package io.github.sob1234509876.osc.api;

import lombok.NonNull;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface OscApiRoomUserDao extends CrudRepository<OscApiRoomUser, Long> {

    @NonNull
    List<@NonNull OscApiRoomUser> findByRoomId(long roomId);

    @NonNull
    List<@NonNull OscApiRoomUser> findByUserId(long userId);

    @NonNull
    Optional<OscApiRoomUser> findByRoomIdAndUserId(long roomId, long userId);

    void deleteByRoomId(long roomId);
}
