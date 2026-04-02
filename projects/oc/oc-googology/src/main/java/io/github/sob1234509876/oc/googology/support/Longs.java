package io.github.sob1234509876.oc.googology.support;

import lombok.NonNull;

import java.math.BigInteger;
import java.util.Optional;

public class Longs {

    @NonNull
    public static Optional<Long> safeAddition(Long @NonNull ... l) {

        var tmp = 0L;
        for (var t : l) {
            tmp += t;
            // Overflow
            if (tmp < 0)
                return Optional.empty();
        }

        return Optional.of(tmp);
    }

    @NonNull
    public static Optional<Long> safeMultiplication(Long @NonNull ... l) {

        if (l.length == 0)
            return Optional.of(0L);

        for (var t : l)
            if (t == 0)
                return Optional.of(0L);

        var tmp = BigInteger.ONE;
        for (var t : l) {
            tmp = tmp.multiply(BigInteger.valueOf(t));
            // Overflow
            if (tmp.bitLength() > 63)
                return Optional.empty();
        }

        return Optional.of(tmp.longValue());
    }

    @NonNull
    public static Optional<Long> safePow(Long @NonNull ... l) {

        if (l.length == 0)
            return Optional.of(0L);

        var tmp = BigInteger.valueOf(l[0]);
        for (var i = 1; i < l.length; i++) {

            // log_2(tmp^l[i]) >= log_2(Long.MAX_VALUE) -> Optional.empty()
            if (Math.log(tmp.longValue()) / Math.log(2) * l[i] >= 64)
                return Optional.empty();

            // l[i] = byte8 ~ byte6 ~ byte4 ~ byte2
            var byte2 = (int) (l[i] & 0x7fff);
            var byte4 = (int) ((l[i] >> 16) & 0x7fff);
            var byte6 = (int) ((l[i] >> 32) & 0x7fff);
            var byte8 = (int) ((l[i] >> 48) & 0x7fff);

            var e16 = 1 << 16;

            // te2 = tmp^l[i][0:16]
            var te2 = tmp.pow(byte2);
            // te4 = tmp^(l[i][16:32] << 16)
            var te4 = tmp.pow(byte4)
                    .pow(e16);
            // te6 = tmp^(l[i][32:48] << 32)
            var te6 = tmp.pow(byte6)
                    .pow(e16)
                    .pow(e16);
            // te6 = tmp^(l[i][48:64] << 48)
            var te8 = tmp.pow(byte8)
                    .pow(e16)
                    .pow(e16)
                    .pow(e16);

            // tmp* = tmp^(te8 + te6 + te4 + te2) = tmp^(byte8 ~ byte6 ~ byte4 ~ byte2)
            tmp = te2.multiply(te4)
                    .multiply(te6)
                    .multiply(te8);
        }

        return Optional.of(tmp.longValue());
    }
}
