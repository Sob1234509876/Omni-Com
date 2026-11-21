package io.github.sob1234509876.osc.api;

import lombok.NonNull;
import org.springframework.data.repository.CrudRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OscApiRequestDao extends CrudRepository<OscApiRequest, Long> {

    @NonNull
    List<@NonNull OscApiRequest> findByFromIdOrTargetId(long fromId, long targetId);

    @NonNull
    Optional<OscApiRequest> findByFromIdAndTargetIdAndTypeAndStatus(long fromId, long targetId, @NonNull OscApiRequestType type, @NonNull OscApiRequestStatus status);

    @NonNull
    List<@NonNull OscApiRequest> findByTargetIdAndStatus(long targetId, @NonNull OscApiRequestStatus status);

    @NonNull
    List<@NonNull OscApiRequest> findByFromIdOrTargetIdOrTargetIdIn(@NonNull Long fromId, @NonNull Long targetId, @NonNull Collection<@NonNull Long> targetIds);
}
