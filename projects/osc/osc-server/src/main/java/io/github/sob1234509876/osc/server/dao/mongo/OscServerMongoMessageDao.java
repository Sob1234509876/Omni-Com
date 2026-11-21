package io.github.sob1234509876.osc.server.dao.mongo;

import io.github.sob1234509876.osc.api.OscApiMessage;
import io.github.sob1234509876.osc.api.OscApiMessageDao;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
public class OscServerMongoMessageDao implements OscApiMessageDao {
    @NonNull
    private MongoTemplate mongoTemplate;

    @Override
    public <S extends OscApiMessage> @NonNull S save(@NonNull S entity) {
        return mongoTemplate.save(entity);
    }

    @Override
    public <S extends OscApiMessage> @NonNull Iterable<S> saveAll(@NonNull Iterable<S> entities) {
        for (var s : entities)
            save(s);

        return entities;
    }

    @Override
    public @NonNull Optional<OscApiMessage> findById(@NonNull Long s) {
        return Optional.ofNullable(mongoTemplate.findById(s, OscApiMessage.class));
    }

    @Override
    public boolean existsById(@NonNull Long s) {
        var query = Criteria.where(OscApiMessage.ID_FIELD)
                .is(s);

        return mongoTemplate.exists(new Query(query), OscApiMessage.class);
    }

    @Override
    public @NonNull Iterable<@NonNull OscApiMessage> findAll() {
        return mongoTemplate.findAll(OscApiMessage.class);
    }

    @Override
    public @NonNull Iterable<@NonNull OscApiMessage> findAllById(@NonNull Iterable<Long> strings) {
        var res = new LinkedList<OscApiMessage>();

        for (var s : strings) {
            var r = findById(s);

            if (r.isEmpty())
                return Collections.emptyList();

            res.add(r.get());
        }

        return Collections.unmodifiableList(res);
    }

    @Override
    public @NonNull List<@NonNull OscApiMessage> findByChatIdOrderByIdDesc(@NonNull Long chatId, @NonNull Pageable pageable) {
        var criteria = Criteria.where(OscApiMessage.CHAT_FIELD)
                .is(chatId);

        return mongoTemplate.find(new Query(criteria).with(pageable).with(Sort.by(Sort.Direction.DESC, OscApiMessage.ID_FIELD)), OscApiMessage.class);
    }

    @Override
    public @NonNull List<@NonNull OscApiMessage> findByChatIdAndIdLessThanOrderByIdDesc(@NonNull Long chatId, long before, @NonNull Pageable pageable) {
        var criteria = Criteria.where(OscApiMessage.CHAT_FIELD)
                .is(chatId)
                .and(OscApiMessage.ID_FIELD)
                .lt(before);

        return mongoTemplate.find(new Query(criteria).with(pageable).with(Sort.by(Sort.Direction.DESC, OscApiMessage.ID_FIELD)), OscApiMessage.class);
    }

    @Override
    public @NonNull List<@NonNull OscApiMessage> findByRoomIdOrderByIdDesc(@NonNull Long roomId, @NonNull Pageable pageable) {
        var criteria = Criteria.where(OscApiMessage.ROOM_FIELD)
                .is(roomId);

        return mongoTemplate.find(new Query(criteria).with(pageable).with(Sort.by(Sort.Direction.DESC, OscApiMessage.ID_FIELD)), OscApiMessage.class);
    }

    @Override
    public @NonNull List<@NonNull OscApiMessage> findByRoomIdAndIdLessThanOrderByIdDesc(@NonNull Long roomId, long before, @NonNull Pageable pageable) {
        var criteria = Criteria.where(OscApiMessage.ROOM_FIELD)
                .is(roomId)
                .and(OscApiMessage.ID_FIELD)
                .lt(before);

        return mongoTemplate.find(new Query(criteria).with(pageable).with(Sort.by(Sort.Direction.DESC, OscApiMessage.ID_FIELD)), OscApiMessage.class);
    }

    @Override
    public void deleteByChatId(long chatId) {
        var criteria = Criteria.where(OscApiMessage.CHAT_FIELD)
                .is(chatId);

        mongoTemplate.remove(new Query(criteria), OscApiMessage.class);
    }

    @Override
    public void deleteByRoomId(long roomId) {
        var criteria = Criteria.where(OscApiMessage.ROOM_FIELD)
                .is(roomId);

        mongoTemplate.remove(new Query(criteria), OscApiMessage.class);
    }

    @Override
    public long count() {
        return mongoTemplate.count(new Query(), OscApiMessage.class);
    }

    @Override
    public void deleteById(@NonNull Long s) {
        var query = Criteria.where(OscApiMessage.ID_FIELD)
                .is(s);

        mongoTemplate.remove(new Query(query), OscApiMessage.class);
    }

    @Override
    public void delete(@NonNull OscApiMessage entity) {
        mongoTemplate.remove(entity);
    }

    @Override
    public void deleteAllById(@NonNull Iterable<? extends Long> strings) {
        for (var s : strings)
            deleteById(s);
    }

    @Override
    public void deleteAll(@NonNull Iterable<? extends OscApiMessage> entities) {
        for (var e : entities)
            delete(e);
    }

    @Override
    public void deleteAll() {
        mongoTemplate.remove(new Query(), OscApiMessage.class);
    }
}
