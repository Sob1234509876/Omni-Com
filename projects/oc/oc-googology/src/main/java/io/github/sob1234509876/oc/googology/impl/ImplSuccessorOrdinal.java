package io.github.sob1234509876.oc.googology.impl;

import io.github.sob1234509876.oc.engine.api.googology.Ordinal;
import io.github.sob1234509876.oc.engine.api.googology.SuccessorOrdinal;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
public class ImplSuccessorOrdinal implements SuccessorOrdinal {

    @NonNull
    Ordinal value;

    @NonNull
    Ordinal origin;

    @Override
    public int compareTo(@NonNull Ordinal o) {
        return value.compareTo(o);
    }

    @NonNull
    @Override
    public String toString() {
        return value.toString();
    }
}
