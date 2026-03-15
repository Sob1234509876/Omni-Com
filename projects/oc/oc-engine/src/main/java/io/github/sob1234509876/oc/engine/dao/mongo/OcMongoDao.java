package io.github.sob1234509876.oc.engine.dao.mongo;

import io.github.sob1234509876.oc.engine.AbstractParent;
import lombok.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.LinkedList;
import java.util.Optional;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@RequiredArgsConstructor
public class OcMongoDao<@NonNull T, @NonNull ID> extends AbstractParent implements CrudRepository<@NonNull T, @NonNull ID> {

    @NonNull
    MongoTemplate mongo;

    @NonNull
    Class<? extends T> valueClass;

    @NonNull
    String collectionName;

    @NonNull
    String keyName;

    @Override
    public <S extends @NonNull T> S save(@NonNull S entity) {
        mongo.save(entity, collectionName);
        return entity;
    }

    @Override
    public <S extends @NonNull T> @NonNull Iterable<@NonNull S> saveAll(@NonNull Iterable<@NonNull S> entities) {
        entities.forEach(this::save);
        return entities;
    }

    @Override
    public @NonNull Optional<@NonNull T> findById(@NonNull ID id) {
        return Optional.ofNullable(mongo.findById(id,
                valueClass,
                collectionName));
    }

    @Override
    public boolean existsById(@NonNull ID id) {
        return mongo.exists(Query.query(Criteria.where(keyName)
                .is(id)), collectionName);
    }

    @SuppressWarnings("unchecked")
    @Override
    public @NonNull Iterable<@NonNull T> findAll() {
        return (Iterable<@NonNull T>) mongo.findAll(valueClass, collectionName);
    }

    @Override
    public @NonNull Iterable<@NonNull T> findAllById(@NonNull Iterable<@NonNull ID> ids) {
        var tmp = new LinkedList<@NonNull T>();

        ids.forEach(id -> findById(id).ifPresent(tmp::add));

        return tmp;
    }

    @Override
    public long count() {
        return mongo.estimatedCount(collectionName);
    }

    @Override
    public void deleteById(@NonNull ID id) {
        mongo.remove(Query.query(Criteria.where(keyName)
                        .is(id)),
                valueClass,
                collectionName);
    }

    @Override
    public void delete(@NonNull T entity) {
        mongo.remove(entity, collectionName);
    }

    @Override
    public void deleteAllById(@NonNull Iterable<? extends @NonNull ID> ids) {
        ids.forEach(this::deleteById);
    }

    @Override
    public void deleteAll(@NonNull Iterable<? extends @NonNull T> entities) {
        entities.forEach(this::delete);
    }

    @Override
    public void deleteAll() {
        deleteAll(findAll());
    }
}
