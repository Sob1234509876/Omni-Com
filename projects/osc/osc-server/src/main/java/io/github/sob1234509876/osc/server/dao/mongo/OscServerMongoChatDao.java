package io.github.sob1234509876.osc.server.dao.mongo;

import io.github.sob1234509876.osc.api.OscApiChat;
import io.github.sob1234509876.osc.api.OscApiChatDao;
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
public class OscServerMongoChatDao implements OscApiChatDao {
    @NonNull
    private MongoTemplate mongoTemplate;

    @Override
    public <S extends OscApiChat> @NonNull S save(@NonNull S entity) {
        return mongoTemplate.save(entity);
    }

    @Override
    public <S extends OscApiChat> @NonNull Iterable<S> saveAll(@NonNull Iterable<S> entities) {
        for (var s : entities)
            save(s);

        return entities;
    }

    @Override
    public @NonNull Optional<OscApiChat> findById(@NonNull Long s) {
        return Optional.ofNullable(mongoTemplate.findById(s, OscApiChat.class));
    }

    @Override
    public boolean existsById(@NonNull Long s) {
        var query = Criteria.where(OscApiChat.ID_FIELD)
                .is(s);

        return mongoTemplate.exists(new Query(query), OscApiChat.class);
    }

    @Override
    public @NonNull Iterable<@NonNull OscApiChat> findAll() {
        return mongoTemplate.findAll(OscApiChat.class);
    }

    @Override
    public @NonNull Iterable<@NonNull OscApiChat> findAllById(@NonNull Iterable<Long> strings) {
        var res = new LinkedList<OscApiChat>();

        for (var s : strings) {
            var r = findById(s);

            if (r.isEmpty())
                return Collections.emptyList();

            res.add(r.get());
        }

        return Collections.unmodifiableList(res);
    }

    @Override
    public @NonNull Optional<OscApiChat> findByUserAIdAndUserBId(long userAId, long userBId) {
        var a = Math.min(userAId, userBId);
        var b = Math.max(userAId, userBId);
        var criteria = Criteria.where(OscApiChat.USER_A_FIELD)
                .is(a)
                .and(OscApiChat.USER_B_FIELD)
                .is(b);

        return Optional.ofNullable(mongoTemplate.findOne(new Query(criteria), OscApiChat.class));
    }

    @Override
    public @NonNull List<@NonNull OscApiChat> findByUserAIdOrUserBId(long userAId, long userBId) {
        var criteria = new Criteria().orOperator(Criteria.where(OscApiChat.USER_A_FIELD).is(userAId), Criteria.where(OscApiChat.USER_B_FIELD).is(userBId));

        return mongoTemplate.find(new Query(criteria), OscApiChat.class);
    }

    @Override
    public long count() {
        return mongoTemplate.count(new Query(), OscApiChat.class);
    }

    @Override
    public void deleteById(@NonNull Long s) {
        var query = Criteria.where(OscApiChat.ID_FIELD)
                .is(s);

        mongoTemplate.remove(new Query(query), OscApiChat.class);
    }

    @Override
    public void delete(@NonNull OscApiChat entity) {
        mongoTemplate.remove(entity);
    }

    @Override
    public void deleteAllById(@NonNull Iterable<? extends Long> strings) {
        for (var s : strings)
            deleteById(s);
    }

    @Override
    public void deleteAll(@NonNull Iterable<? extends OscApiChat> entities) {
        for (var e : entities)
            delete(e);
    }

    @Override
    public void deleteAll() {
        mongoTemplate.remove(new Query(), OscApiChat.class);
    }
}
