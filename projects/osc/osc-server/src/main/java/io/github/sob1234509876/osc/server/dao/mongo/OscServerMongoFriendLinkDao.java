package io.github.sob1234509876.osc.server.dao.mongo;

import io.github.sob1234509876.osc.api.OscApiFriendLink;
import io.github.sob1234509876.osc.api.OscApiFriendLinkDao;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Data
@NoArgsConstructor
@RequiredArgsConstructor
@Repository
public class OscServerMongoFriendLinkDao implements OscApiFriendLinkDao {
    @NonNull
    private MongoTemplate mongoTemplate;

    @Override
    public <S extends OscApiFriendLink> @NonNull S save(@NonNull S entity) {
        return mongoTemplate.save(entity);
    }

    @Override
    public <S extends OscApiFriendLink> @NonNull Iterable<S> saveAll(@NonNull Iterable<S> entities) {
        for (var s : entities)
            save(s);

        return entities;
    }

    @Override
    public @NonNull Optional<OscApiFriendLink> findById(@NonNull Long s) {
        return Optional.ofNullable(mongoTemplate.findById(s, OscApiFriendLink.class));
    }

    @Override
    public boolean existsById(@NonNull Long s) {
        var query = Criteria.where(OscApiFriendLink.ID_FIELD)
                .is(s);

        return mongoTemplate.exists(new Query(query), OscApiFriendLink.class);
    }

    @Override
    public @NonNull Iterable<@NonNull OscApiFriendLink> findAll() {
        return mongoTemplate.findAll(OscApiFriendLink.class);
    }

    @Override
    public @NonNull Iterable<@NonNull OscApiFriendLink> findAllById(@NonNull Iterable<Long> strings) {
        var res = new LinkedList<OscApiFriendLink>();

        for (var s : strings) {
            var r = findById(s);

            if (r.isEmpty())
                return Collections.emptyList();

            res.add(r.get());
        }

        return Collections.unmodifiableList(res);
    }

    @Override
    public @NonNull List<@NonNull OscApiFriendLink> findByUserId(long userId) {
        var criteria = Criteria.where(OscApiFriendLink.USER_FIELD)
                .is(userId);

        return mongoTemplate.find(new Query(criteria), OscApiFriendLink.class);
    }

    @Override
    public @NonNull Optional<OscApiFriendLink> findByUserIdAndFriendId(long userId, long friendId) {
        var criteria = Criteria.where(OscApiFriendLink.USER_FIELD)
                .is(userId)
                .and(OscApiFriendLink.FRIEND_FIELD)
                .is(friendId);

        return Optional.ofNullable(mongoTemplate.findOne(new Query(criteria), OscApiFriendLink.class));
    }

    @Override
    public long count() {
        return mongoTemplate.count(new Query(), OscApiFriendLink.class);
    }

    @Override
    public void deleteById(@NonNull Long s) {
        var query = Criteria.where(OscApiFriendLink.ID_FIELD)
                .is(s);

        mongoTemplate.remove(new Query(query), OscApiFriendLink.class);
    }

    @Override
    public void delete(@NonNull OscApiFriendLink entity) {
        mongoTemplate.remove(entity);
    }

    @Override
    public void deleteAllById(@NonNull Iterable<? extends Long> strings) {
        for (var s : strings)
            deleteById(s);
    }

    @Override
    public void deleteAll(@NonNull Iterable<? extends OscApiFriendLink> entities) {
        for (var e : entities)
            delete(e);
    }

    @Override
    public void deleteAll() {
        mongoTemplate.remove(new Query(), OscApiFriendLink.class);
    }
}
