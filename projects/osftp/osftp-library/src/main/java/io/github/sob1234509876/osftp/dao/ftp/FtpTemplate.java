package io.github.sob1234509876.osftp.dao.ftp;

import io.github.sob1234509876.osftp.lbi.OsftpLibraryProperty;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.springframework.lang.Nullable;

import java.io.*;
import java.util.Timer;
import java.util.TimerTask;

// Extremely inefficient
@Slf4j
@Getter
@ToString
@NoArgsConstructor
@RequiredArgsConstructor
public class FtpTemplate implements FtpOperations {

    private static final Timer HEARTBEAT = new Timer(true);

    @NonNull
    private FTPClient client;

    @NonNull
    private OsftpLibraryProperty config;

    private boolean connected;

    private final TimerTask heartbeatTask = new TimerTask() {
        @Override
        public void run() {

            if (!connected)
                return;

            try {
                client.noop();
                log.info("Beep");
            } catch (@NonNull Throwable t) {
                connected = false;

                try {
                    client.disconnect();
                } catch (@NonNull IOException ignored) {

                }
                log.warn("Heartbeat failed", t);
            }
        }
    };

    {
        log.info("Constructed {}", this);
    }

    // Make sure to make directories before invoking some methods, it is best to invoke this method at the start of FTP
    // service bean initializing phase (I guess, because it is quite inefficient, O(n) operation, not O(1)).
    @SneakyThrows
    @Override
    public void makeDirectories(@NonNull String path) {
        try {
            connect();
            client.changeWorkingDirectory("/");
            for (var s : new FtpPath(path).getDir()) {
                client.makeDirectory(s);
                client.changeWorkingDirectory(s);
            }
        } catch (@NonNull Throwable t) {
            disconnect();
            throw new RuntimeException(t);
        }
    }

    @SneakyThrows
    @Override
    public void createFile(@NonNull String path) {
        try {
            connect();
            client.changeWorkingDirectory("/");

            client.storeFile(path, InputStream.nullInputStream());
        } catch (@NonNull Throwable t) {
            disconnect();
            throw new RuntimeException(t);
        }
    }

    @SneakyThrows
    @Override
    public byte @NonNull [] readFile(@NonNull String path) {
        try {
            connect();
            var buffer = new ByteArrayOutputStream();
            readFile(path, buffer);
            return buffer.toByteArray();
        } catch (@NonNull Throwable t) {
            disconnect();
            throw new RuntimeException(t);
        }
    }

    @SneakyThrows
    @Override
    public void readFile(@NonNull String path, @NonNull OutputStream os) {
        try {
            connect();
            client.changeWorkingDirectory("/");

            client.retrieveFile(path, os);
        } catch (@NonNull Throwable t) {
            disconnect();
            throw new RuntimeException(t);
        }
    }

    @SneakyThrows
    @Override
    public @NonNull FTPFile @NonNull [] listFiles(@NonNull String path) {
        try {
            connect();
            client.changeWorkingDirectory("/");
            return client.listFiles(path);
        } catch (@NonNull Throwable t) {
            disconnect();
            throw new RuntimeException(t);
        }
    }

    @SneakyThrows
    @Override
    public void writeFile(@NonNull String path, byte @NonNull [] data) {
        try {
            connect();
            writeFile(path, new ByteArrayInputStream(data));
        } catch (@NonNull Throwable t) {
            disconnect();
            throw new RuntimeException(t);
        }
    }

    @SneakyThrows
    @Override
    public void writeFile(@NonNull String path, @NonNull InputStream is) {
        try {
            connect();
            client.changeWorkingDirectory("/");

            client.storeFile(path, is);

        } catch (@NonNull Throwable t) {
            disconnect();
            throw new RuntimeException(t);
        }
    }

    @SneakyThrows
    @Override
    public void appendFile(@NonNull String path, byte @NonNull [] data) {
        try {
            connect();
            appendFile(path, new ByteArrayInputStream(data));
        } catch (@NonNull Throwable t) {
            disconnect();
            throw new RuntimeException(t);
        }
    }

    @SneakyThrows
    @Override
    public void appendFile(@NonNull String path, @NonNull InputStream is) {
        try {
            connect();
            client.changeWorkingDirectory("/");

            client.appendFile(path, is);
        } catch (@NonNull Throwable t) {
            disconnect();
            throw new RuntimeException(t);
        }
    }

    @SneakyThrows
    @Override
    public void deleteFile(@NonNull String path) {
        try {
            connect();
            client.changeWorkingDirectory("/");
            client.deleteFile(path);
        } catch (@NonNull Throwable t) {
            disconnect();
            throw new RuntimeException(t);
        }
    }

    @Nullable
    @Override
    public FTPFile getFile(@NonNull String path) {
        var fp = new FtpPath(path);
        var dir = fp.getDir();
        if (dir.isEmpty())
            return null;
        var name = dir.get(dir.size() - 1);
        fp.toParent();
        for (var f : listFiles(fp.toString()))
            if (f.getName().equals(name))
                return f;
        return null;
    }

    @Override
    public void destroy() throws Exception {
        heartbeatTask.cancel();
        disconnect();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        client.setDefaultTimeout(config.getTimeout());
        connect();
        HEARTBEAT.scheduleAtFixedRate(heartbeatTask, 0, config.getHeartbeat());
    }

    private void connect() throws IOException {

        if (connected)
            return;


        client.connect(config.getHost(), config.getPort());
        client.login(config.getUsername(), config.getPassword());

        client.setFileType(FTP.BINARY_FILE_TYPE);

        if (config.isPassiveMode())
            client.enterLocalPassiveMode();

        client.sendNoOp();

        connected = true;
    }

    private void disconnect() throws IOException {

        if (!connected)
            return;

        client.logout();
        client.disconnect();
        connected = false;
    }
}
