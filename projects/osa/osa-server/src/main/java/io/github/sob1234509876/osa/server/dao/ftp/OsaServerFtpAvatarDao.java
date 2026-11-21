package io.github.sob1234509876.osa.server.dao.ftp;

import io.github.sob1234509876.osa.api.OsaApiAvatar;
import io.github.sob1234509876.osa.api.OsaApiAvatarDao;
import io.github.sob1234509876.osa.server.component.OsaServerPropertyComponent;
import io.github.sob1234509876.osa.server.service.OsaServerUtilityService;
import io.github.sob1234509876.osftp.dao.ftp.FtpTemplate;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.LinkedList;
import java.util.Optional;

@Slf4j
@Data
@NoArgsConstructor
@RequiredArgsConstructor
@Repository
public class OsaServerFtpAvatarDao implements OsaApiAvatarDao, InitializingBean {

    @NonNull
    private FtpTemplate ftpTemplate;

    @NonNull
    private OsaServerUtilityService osaServerUtilityService;

    @NonNull
    private OsaServerPropertyComponent osaServerPropertyComponent;

    @Override
    public <S extends OsaApiAvatar> @NonNull S save(@NonNull S entity) {
        var path = osaServerUtilityService.getAvatarFtpPath(String.valueOf(entity.getId()));

        ftpTemplate.writeFile(path, entity.getImage());

        return entity;
    }

    @Override
    public <S extends OsaApiAvatar> @NonNull Iterable<S> saveAll(@NonNull Iterable<S> entities) {
        for (var e : entities)
            save(e);

        return entities;
    }

    @Override
    public @NonNull Optional<OsaApiAvatar> findById(@NonNull Long s) {
        if (!existsById(s))
            return Optional.empty();

        var path = osaServerUtilityService.getAvatarFtpPath(String.valueOf(s));
        var tmp = new OsaApiAvatar();
        tmp.setId(s);
        tmp.setImage(ftpTemplate.readFile(path));

        return Optional.of(tmp);
    }

    @Override
    public boolean existsById(@NonNull Long s) {
        var path = osaServerUtilityService.getAvatarFtpPath(String.valueOf(s));
        return ftpTemplate.getFile(path) != null;
    }

    @Override
    public @NonNull Iterable<@NonNull OsaApiAvatar> findAll() {
        var path = osaServerPropertyComponent.getAvatarFtpPathPrefix();
        var res = new LinkedList<OsaApiAvatar>();

        for (var f : ftpTemplate.listFiles(path)) {
            var p = osaServerUtilityService.getAvatarFtpPath(f.getName());
            var tmp = new OsaApiAvatar();
            tmp.setId(Long.parseLong(f.getName()));
            tmp.setImage(ftpTemplate.readFile(p));

            res.add(tmp);
        }

        return Collections.unmodifiableList(res);
    }

    @Override
    public @NonNull Iterable<@NonNull OsaApiAvatar> findAllById(@NonNull Iterable<Long> strings) {
        var res = new LinkedList<OsaApiAvatar>();

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
        var path = osaServerPropertyComponent.getAvatarFtpPathPrefix();
        return ftpTemplate.listFiles(path).length;
    }

    @Override
    public void deleteById(@NonNull Long s) {
        var path = osaServerUtilityService.getAvatarFtpPath(String.valueOf(s));

        ftpTemplate.deleteFile(path);
    }

    @Override
    public void delete(@NonNull OsaApiAvatar entity) {
        deleteById(entity.getId());
    }

    @Override
    public void deleteAllById(@NonNull Iterable<? extends Long> strings) {
        for (var s : strings)
            deleteById(s);
    }

    @Override
    public void deleteAll(@NonNull Iterable<? extends OsaApiAvatar> entities) {
        for (var e : entities)
            delete(e);
    }

    @Override
    public void deleteAll() {
        var path = osaServerPropertyComponent.getAvatarFtpPathPrefix();
        var files = ftpTemplate.listFiles(path);

        for (var f : files) {
            var p = osaServerUtilityService.getAvatarFtpPath(f.getName());
            ftpTemplate.deleteFile(p);
        }
    }

    @Override
    public void afterPropertiesSet() {
        ftpTemplate.makeDirectories(osaServerPropertyComponent.getAvatarFtpPathPrefix());
    }
}
