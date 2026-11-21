package io.github.sob1234509876.osc.server.dao.mongo;

import io.github.sob1234509876.osc.api.OscApiChatUser;
import io.github.sob1234509876.osc.api.OscApiChatUserDao;
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
import java.util.Optional;

@Slf4j
@Data
@NoArgsConstructor
@RequiredArgsConstructor
@Repository
public class OscServerMongoChatUserDao implements OscApiChatUserDao {
    @NonNull
    private MongoTemplate mongoTemplate;

    @Override
    public <S extends OscApiChatUser> @NonNull S save(@NonNull S entity) {
        return mongoTemplate.save(entity);
    }

    @Override
    public <S extends OscApiChatUser> @NonNull Iterable<S> saveAll(@NonNull Iterable<S> entities) {
        for (var s : entities)
            save(s);

        return entities;
    }

    @Override
    public @NonNull Optional<OscApiChatUser> findById(@NonNull Long s) {
        return Optional.ofNullable(mongoTemplate.findById(s, OscApiChatUser.class));
    }

    @Override
    public boolean existsById(@NonNull Long s) {
        var query = Criteria.where(OscApiChatUser.ID_FIELD)
                .is(s);

        return mongoTemplate.exists(new Query(query), OscApiChatUser.class);
    }

    @Override
    public @NonNull Iterable<@NonNull OscApiChatUser> findAll() {
        return mongoTemplate.findAll(OscApiChatUser.class);
    }

    @Override
    public @NonNull Iterable<@NonNull OscApiChatUser> findAllById(@NonNull Iterable<Long> strings) {
        var res = new LinkedList<OscApiChatUser>();

        for (var s : strings) {
            var r = findById(s);

            if (r.isEmpty())
                return Collections.emptyList();

            res.add(r.get());
        }

        return Collections.unmodifiableList(res);
    }

    @Override
    public long count() {
        return mongoTemplate.count(new Query(), OscApiChatUser.class);
    }

    @Override
    public void deleteById(@NonNull Long s) {
        var query = Criteria.where(OscApiChatUser.ID_FIELD)
                .is(s);

        mongoTemplate.remove(new Query(query), OscApiChatUser.class);
    }

    @Override
    public void delete(@NonNull OscApiChatUser entity) {
        mongoTemplate.remove(entity);
    }

    @Override
    public void deleteAllById(@NonNull Iterable<? extends Long> strings) {
        for (var s : strings)
            deleteById(s);
    }

    @Override
    public void deleteAll(@NonNull Iterable<? extends OscApiChatUser> entities) {
        for (var e : entities)
            delete(e);
    }

    @Override
    public void deleteAll() {
        mongoTemplate.remove(new Query(), OscApiChatUser.class);
    }
}
