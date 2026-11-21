package io.github.sob1234509876.osc.server.dao.mongo;

import io.github.sob1234509876.osc.api.OscApiRequest;
import io.github.sob1234509876.osc.api.OscApiRequestDao;
import io.github.sob1234509876.osc.api.OscApiRequestStatus;
import io.github.sob1234509876.osc.api.OscApiRequestType;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Data
@NoArgsConstructor
@RequiredArgsConstructor
@Repository
public class OscServerMongoRequestDao implements OscApiRequestDao {
    @NonNull
    private MongoTemplate mongoTemplate;

    @Override
    public <S extends OscApiRequest> @NonNull S save(@NonNull S entity) {
        return mongoTemplate.save(entity);
    }

    @Override
    public <S extends OscApiRequest> @NonNull Iterable<S> saveAll(@NonNull Iterable<S> entities) {
        for (var s : entities)
            save(s);

        return entities;
    }

    @Override
    public @NonNull Optional<OscApiRequest> findById(@NonNull Long s) {
        return Optional.ofNullable(mongoTemplate.findById(s, OscApiRequest.class));
    }

    @Override
    public boolean existsById(@NonNull Long s) {
        var query = Criteria.where(OscApiRequest.ID_FIELD)
                .is(s);

        return mongoTemplate.exists(new Query(query), OscApiRequest.class);
    }

    @Override
    public @NonNull Iterable<@NonNull OscApiRequest> findAll() {
        return mongoTemplate.findAll(OscApiRequest.class);
    }

    @Override
    public @NonNull Iterable<@NonNull OscApiRequest> findAllById(@NonNull Iterable<Long> strings) {
        var res = new LinkedList<OscApiRequest>();

        for (var s : strings) {
            var r = findById(s);

            if (r.isEmpty())
                return Collections.emptyList();

            res.add(r.get());
        }

        return Collections.unmodifiableList(res);
    }

    @Override
    public @NonNull List<@NonNull OscApiRequest> findByFromIdOrTargetId(long fromId, long targetId) {
        var criteria = new Criteria().orOperator(Criteria.where(OscApiRequest.FROM_FIELD).is(fromId), Criteria.where(OscApiRequest.TARGET_FIELD).is(targetId));

        return mongoTemplate.find(new Query(criteria), OscApiRequest.class);
    }

    @Override
    public @NonNull Optional<OscApiRequest> findByFromIdAndTargetIdAndTypeAndStatus(long fromId, long targetId, @NonNull OscApiRequestType type, @NonNull OscApiRequestStatus status) {
        var criteria = Criteria.where(OscApiRequest.FROM_FIELD)
                .is(fromId)
                .and(OscApiRequest.TARGET_FIELD)
                .is(targetId)
                .and(OscApiRequest.TYPE_FIELD)
                .is(type)
                .and(OscApiRequest.STATUS_FIELD)
                .is(status);

        return Optional.ofNullable(mongoTemplate.findOne(new Query(criteria), OscApiRequest.class));
    }

    @Override
    public @NonNull List<@NonNull OscApiRequest> findByTargetIdAndStatus(long targetId, @NonNull OscApiRequestStatus status) {
        var criteria = Criteria.where(OscApiRequest.TARGET_FIELD)
                .is(targetId)
                .and(OscApiRequest.STATUS_FIELD)
                .is(status);

        return mongoTemplate.find(new Query(criteria), OscApiRequest.class);
    }

    @Override
    public @NonNull List<@NonNull OscApiRequest> findByFromIdOrTargetIdOrTargetIdIn(@NonNull Long fromId, @NonNull Long targetId, @NonNull Collection<@NonNull Long> targetIds) {
        var criteria = new Criteria().orOperator(Criteria.where(OscApiRequest.FROM_FIELD).is(fromId), Criteria.where(OscApiRequest.TARGET_FIELD).is(targetId), Criteria.where(OscApiRequest.TARGET_FIELD).in(targetIds));

        return mongoTemplate.find(new Query(criteria), OscApiRequest.class);
    }

    @Override
    public long count() {
        return mongoTemplate.count(new Query(), OscApiRequest.class);
    }

    @Override
    public void deleteById(@NonNull Long s) {
        var query = Criteria.where(OscApiRequest.ID_FIELD)
                .is(s);

        mongoTemplate.remove(new Query(query), OscApiRequest.class);
    }

    @Override
    public void delete(@NonNull OscApiRequest entity) {
        mongoTemplate.remove(entity);
    }

    @Override
    public void deleteAllById(@NonNull Iterable<? extends Long> strings) {
        for (var s : strings)
            deleteById(s);
    }

    @Override
    public void deleteAll(@NonNull Iterable<? extends OscApiRequest> entities) {
        for (var e : entities)
            delete(e);
    }

    @Override
    public void deleteAll() {
        mongoTemplate.remove(new Query(), OscApiRequest.class);
    }
}
