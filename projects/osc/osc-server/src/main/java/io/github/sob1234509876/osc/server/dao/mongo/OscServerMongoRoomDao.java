package io.github.sob1234509876.osc.server.dao.mongo;

import io.github.sob1234509876.osc.api.social.OscApiRoom;
import io.github.sob1234509876.osc.api.social.OscApiRoomDao;
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
public class OscServerMongoRoomDao implements OscApiRoomDao {
    @NonNull
    private MongoTemplate mongoTemplate;

    @Override
    public <S extends OscApiRoom> @NonNull S save(@NonNull S entity) {
        return mongoTemplate.save(entity);
    }

    @Override
    public <S extends OscApiRoom> @NonNull Iterable<S> saveAll(@NonNull Iterable<S> entities) {
        for (var s : entities)
            save(s);

        return entities;
    }

    @Override
    public @NonNull Optional<OscApiRoom> findById(@NonNull Long s) {
        return Optional.ofNullable(mongoTemplate.findById(s, OscApiRoom.class));
    }

    @Override
    public boolean existsById(@NonNull Long s) {
        var query = Criteria.where(OscApiRoom.ID_FIELD)
                .is(s);

        return mongoTemplate.exists(new Query(query), OscApiRoom.class);
    }

    @Override
    public @NonNull Iterable<@NonNull OscApiRoom> findAll() {
        return mongoTemplate.findAll(OscApiRoom.class);
    }

    @Override
    public @NonNull Iterable<@NonNull OscApiRoom> findAllById(@NonNull Iterable<Long> strings) {
        var res = new LinkedList<OscApiRoom>();

        for (var s : strings) {
            var r = findById(s);

            if (r.isEmpty())
                return Collections.emptyList();

            res.add(r.get());
        }

        return Collections.unmodifiableList(res);
    }

    @Override
    public @NonNull List<@NonNull OscApiRoom> findByNameContaining(@NonNull String name, @NonNull Pageable pageable) {
        var criteria = Criteria.where(OscApiRoom.NAME_FIELD)
                .regex(Pattern.quote(name), "i");

        return mongoTemplate.find(new Query(criteria).with(pageable), OscApiRoom.class);
    }

    @Override
    public long count() {
        return mongoTemplate.count(new Query(), OscApiRoom.class);
    }

    @Override
    public void deleteById(@NonNull Long s) {
        var query = Criteria.where(OscApiRoom.ID_FIELD)
                .is(s);

        mongoTemplate.remove(new Query(query), OscApiRoom.class);
    }

    @Override
    public void delete(@NonNull OscApiRoom entity) {
        mongoTemplate.remove(entity);
    }

    @Override
    public void deleteAllById(@NonNull Iterable<? extends Long> strings) {
        for (var s : strings)
            deleteById(s);
    }

    @Override
    public void deleteAll(@NonNull Iterable<? extends OscApiRoom> entities) {
        for (var e : entities)
            delete(e);
    }

    @Override
    public void deleteAll() {
        mongoTemplate.remove(new Query(), OscApiRoom.class);
    }
}
