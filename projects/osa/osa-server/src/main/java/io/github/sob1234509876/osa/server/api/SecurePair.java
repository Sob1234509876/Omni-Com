package io.github.sob1234509876.osa.server.api;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
public class SecurePair<P, T> {
    @NonNull
    private P password;

    @NonNull
    private T body;
}
