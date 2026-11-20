package io.github.sob1234509876.osa.server.api;

import lombok.NonNull;

public class StringUserSecurePair extends SecurePair<String, User> {
    public StringUserSecurePair() {
    }

    @SuppressWarnings("unused")
    public StringUserSecurePair(@NonNull String password, @NonNull User body) {
        super(password, body);
    }
}
