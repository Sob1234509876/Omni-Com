package io.github.sob1234509876.osc.api.social;

import lombok.NonNull;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface OscApiChatDao extends CrudRepository<OscApiChat, Long> {

    @NonNull
    Optional<OscApiChat> findByUserAIdAndUserBId(long userAId, long userBId);

    @NonNull
    List<@NonNull OscApiChat> findByUserAIdOrUserBId(long userAId, long userBId);
}
