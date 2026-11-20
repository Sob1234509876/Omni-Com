package io.github.sob1234509876.osftp.dao.ftp;

import lombok.NonNull;
import org.apache.commons.net.ftp.FTPFile;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.lang.Nullable;

import java.io.InputStream;
import java.io.OutputStream;

public interface FtpOperations extends InitializingBean, DisposableBean {
    void makeDirectories(@NonNull String path);

    void createFile(@NonNull String path);

    byte @NonNull [] readFile(@NonNull String path);

    void readFile(@NonNull String path, @NonNull OutputStream os);

    @NonNull
    FTPFile @NonNull [] listFiles(@NonNull String path);

    void writeFile(@NonNull String path, byte @NonNull [] data);

    void writeFile(@NonNull String path, @NonNull InputStream is);

    @SuppressWarnings("unused")
    void appendFile(@NonNull String path, byte @NonNull [] data);

    void appendFile(@NonNull String path, @NonNull InputStream is);

    void deleteFile(@NonNull String path);

    @Nullable
    FTPFile getFile(@NonNull String path);
}
