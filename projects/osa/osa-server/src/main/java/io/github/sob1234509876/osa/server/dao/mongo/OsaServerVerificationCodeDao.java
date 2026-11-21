package io.github.sob1234509876.osa.server.dao.mongo;

import io.github.sob1234509876.osa.server.entity.OsaServerVerificationCode;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Slf4j
@Data
@NoArgsConstructor
@RequiredArgsConstructor
@Repository
public class OsaServerVerificationCodeDao implements InitializingBean {

    @NonNull
    private MongoTemplate mongoTemplate;

    @Override
    public void afterPropertiesSet() {
        var index = new Index();
        index.on(OsaServerVerificationCode.EXPIRES_AT_FIELD, Sort.Direction.ASC)
                .expire(0L);

        mongoTemplate.indexOps(OsaServerVerificationCode.class)
                .createIndex(index);
    }

    public void save(@NonNull OsaServerVerificationCode code) {
        mongoTemplate.save(code);
    }

    public OsaServerVerificationCode find(@NonNull String email, @NonNull String purpose) {
        var criteria = Criteria.where(OsaServerVerificationCode.EMAIL_FIELD)
                .is(email)
                .and(OsaServerVerificationCode.PURPOSE_FIELD)
                .is(purpose);

        return mongoTemplate.findOne(new Query(criteria), OsaServerVerificationCode.class);
    }

    public void delete(@NonNull OsaServerVerificationCode code) {
        mongoTemplate.remove(code);
    }

}
