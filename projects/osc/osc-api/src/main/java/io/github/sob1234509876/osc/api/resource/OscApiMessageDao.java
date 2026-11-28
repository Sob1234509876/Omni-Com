package io.github.sob1234509876.osc.api.resource;

import lombok.NonNull;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface OscApiMessageDao extends CrudRepository<OscApiMessage, Long> {

    @NonNull
    List<@NonNull OscApiMessage> findByChatIdOrderByIdDesc(@NonNull Long chatId, @NonNull Pageable pageable);

    @NonNull
    List<@NonNull OscApiMessage> findByChatIdAndIdLessThanOrderByIdDesc(@NonNull Long chatId, long before, @NonNull Pageable pageable);

    @NonNull
    List<@NonNull OscApiMessage> findByRoomIdOrderByIdDesc(@NonNull Long roomId, @NonNull Pageable pageable);

    @NonNull
    List<@NonNull OscApiMessage> findByRoomIdAndIdLessThanOrderByIdDesc(@NonNull Long roomId, long before, @NonNull Pageable pageable);

    void deleteByChatId(long chatId);

    void deleteByRoomId(long roomId);
}
