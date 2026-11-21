package io.github.sob1234509876.osa.server.dao.mongo;

import io.github.sob1234509876.osa.api.OsaApiUser;
import io.github.sob1234509876.osa.api.OsaApiUserDao;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
@Data
@NoArgsConstructor
@RequiredArgsConstructor
@Repository
public class OsaServerMongoUserDao implements OsaApiUserDao {
    @NonNull
    private MongoTemplate mongoTemplate;

    @Override
    public <S extends OsaApiUser> @NonNull S save(@NonNull S entity) {
        return mongoTemplate.save(entity);
    }

    @Override
    public <S extends OsaApiUser> @NonNull Iterable<S> saveAll(@NonNull Iterable<S> entities) {
        for (var s : entities)
            save(s);

        return entities;
    }

    @Override
    public @NonNull Optional<@NonNull OsaApiUser> findById(@NonNull Long s) {
        return Optional.ofNullable(mongoTemplate.findById(s, OsaApiUser.class));
    }

    @Override
    public boolean existsById(@NonNull Long s) {
        var query = Criteria.where(OsaApiUser.ID_FIELD)
                .is(s);

        return mongoTemplate.exists(new Query(query), OsaApiUser.class);
    }

    @Override
    public @NonNull Iterable<@NonNull OsaApiUser> findAll() {
        return mongoTemplate.findAll(OsaApiUser.class);
    }

    @Override
    public @NonNull Iterable<@NonNull OsaApiUser> findAllById(@NonNull Iterable<Long> strings) {
        var res = new LinkedList<OsaApiUser>();

        for (var s : strings) {
            var r = findById(s);

            if (r.isEmpty())
                return Collections.emptyList();

            res.add(r.get());
        }

        return Collections.unmodifiableList(res);
    }

    @Override
    public @NonNull List<@NonNull OsaApiUser> findByUsernameContaining(@NonNull String username, @NonNull Pageable pageable) {
        var criteria = Criteria.where(OsaApiUser.USERNAME_FIELD)
                .regex(Pattern.quote(username), "i");

        return mongoTemplate.find(new Query(criteria).with(pageable), OsaApiUser.class);
    }

    @Override
    public long count() {
        return mongoTemplate.count(new Query(), OsaApiUser.class);
    }

    @Override
    public void deleteById(@NonNull Long s) {
        var query = Criteria.where(OsaApiUser.ID_FIELD)
                .is(s);

        mongoTemplate.remove(new Query(query), OsaApiUser.class);
    }

    @Override
    public void delete(@NonNull OsaApiUser entity) {
        mongoTemplate.remove(entity);
    }

    @Override
    public void deleteAllById(@NonNull Iterable<? extends Long> strings) {
        for (var s : strings)
            deleteById(s);
    }

    @Override
    public void deleteAll(@NonNull Iterable<? extends OsaApiUser> entities) {
        for (var e : entities)
            delete(e);
    }

    @Override
    public void deleteAll() {
        mongoTemplate.remove(new Query(), OsaApiUser.class);
    }
}
