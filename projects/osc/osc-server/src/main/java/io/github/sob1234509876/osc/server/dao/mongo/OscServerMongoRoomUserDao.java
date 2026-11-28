package io.github.sob1234509876.osc.server.dao.mongo;

import io.github.sob1234509876.osc.api.social.OscApiRoomUser;
import io.github.sob1234509876.osc.api.social.OscApiRoomUserDao;
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
public class OscServerMongoRoomUserDao implements OscApiRoomUserDao {
    @NonNull
    private MongoTemplate mongoTemplate;

    @Override
    public <S extends OscApiRoomUser> @NonNull S save(@NonNull S entity) {
        return mongoTemplate.save(entity);
    }

    @Override
    public <S extends OscApiRoomUser> @NonNull Iterable<S> saveAll(@NonNull Iterable<S> entities) {
        for (var s : entities)
            save(s);

        return entities;
    }

    @Override
    public @NonNull Optional<OscApiRoomUser> findById(@NonNull Long s) {
        return Optional.ofNullable(mongoTemplate.findById(s, OscApiRoomUser.class));
    }

    @Override
    public boolean existsById(@NonNull Long s) {
        var query = Criteria.where(OscApiRoomUser.ID_FIELD)
                .is(s);

        return mongoTemplate.exists(new Query(query), OscApiRoomUser.class);
    }

    @Override
    public @NonNull Iterable<@NonNull OscApiRoomUser> findAll() {
        return mongoTemplate.findAll(OscApiRoomUser.class);
    }

    @Override
    public @NonNull Iterable<@NonNull OscApiRoomUser> findAllById(@NonNull Iterable<Long> strings) {
        var res = new LinkedList<OscApiRoomUser>();

        for (var s : strings) {
            var r = findById(s);

            if (r.isEmpty())
                return Collections.emptyList();

            res.add(r.get());
        }

        return Collections.unmodifiableList(res);
    }

    @Override
    public @NonNull List<@NonNull OscApiRoomUser> findByRoomId(long roomId) {
        var criteria = Criteria.where(OscApiRoomUser.ROOM_FIELD)
                .is(roomId);

        return mongoTemplate.find(new Query(criteria), OscApiRoomUser.class);
    }

    @Override
    public @NonNull List<@NonNull OscApiRoomUser> findByUserId(long userId) {
        var criteria = Criteria.where(OscApiRoomUser.USER_FIELD)
                .is(userId);

        return mongoTemplate.find(new Query(criteria), OscApiRoomUser.class);
    }

    @Override
    public @NonNull Optional<OscApiRoomUser> findByRoomIdAndUserId(long roomId, long userId) {
        var criteria = Criteria.where(OscApiRoomUser.ROOM_FIELD)
                .is(roomId)
                .and(OscApiRoomUser.USER_FIELD)
                .is(userId);

        return Optional.ofNullable(mongoTemplate.findOne(new Query(criteria), OscApiRoomUser.class));
    }

    @Override
    public void deleteByRoomId(long roomId) {
        var criteria = Criteria.where(OscApiRoomUser.ROOM_FIELD)
                .is(roomId);

        mongoTemplate.remove(new Query(criteria), OscApiRoomUser.class);
    }

    @Override
    public long count() {
        return mongoTemplate.count(new Query(), OscApiRoomUser.class);
    }

    @Override
    public void deleteById(@NonNull Long s) {
        var query = Criteria.where(OscApiRoomUser.ID_FIELD)
                .is(s);

        mongoTemplate.remove(new Query(query), OscApiRoomUser.class);
    }

    @Override
    public void delete(@NonNull OscApiRoomUser entity) {
        mongoTemplate.remove(entity);
    }

    @Override
    public void deleteAllById(@NonNull Iterable<? extends Long> strings) {
        for (var s : strings)
            deleteById(s);
    }

    @Override
    public void deleteAll(@NonNull Iterable<? extends OscApiRoomUser> entities) {
        for (var e : entities)
            delete(e);
    }

    @Override
    public void deleteAll() {
        mongoTemplate.remove(new Query(), OscApiRoomUser.class);
    }
}
