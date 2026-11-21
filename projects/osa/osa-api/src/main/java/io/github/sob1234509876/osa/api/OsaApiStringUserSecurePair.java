package io.github.sob1234509876.osa.api;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
public class OsaApiStringUserSecurePair {

    @NonNull
    private String password;

    @NonNull
    private OsaApiUser body;

}
