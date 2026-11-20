package io.github.sob1234509876.osftp.lbi;

public interface OsftpLibraryProperty {
    String getHost();

    int getPort();

    String getUsername();

    String getPassword();

    int getTimeout();

    int getHeartbeat();

    boolean isPassiveMode();
}
