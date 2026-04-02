package io.github.sob1234509876.oc.googology.impl;

import io.github.sob1234509876.oc.engine.api.googology.Ordinal;
import io.github.sob1234509876.oc.engine.api.googology.ZeroOrdinal;
import lombok.NonNull;

public class ImplZeroOrdinal implements ZeroOrdinal {

    @SuppressWarnings("ComparatorMethodParameterNotUsed")
    @Override
    public int compareTo(@NonNull Ordinal o) {
        if (o instanceof ZeroOrdinal)
            return 0;

        return -1;
    }

    @NonNull
    @Override
    public String toString() {
        return "0";
    }

}
