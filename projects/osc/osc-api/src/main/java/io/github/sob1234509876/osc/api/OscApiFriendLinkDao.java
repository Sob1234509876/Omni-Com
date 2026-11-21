package io.github.sob1234509876.osc.api;

import lombok.NonNull;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface OscApiFriendLinkDao extends CrudRepository<OscApiFriendLink, Long> {

    @NonNull
    List<@NonNull OscApiFriendLink> findByUserId(long userId);

    @NonNull
    Optional<OscApiFriendLink> findByUserIdAndFriendId(long userId, long friendId);
}
