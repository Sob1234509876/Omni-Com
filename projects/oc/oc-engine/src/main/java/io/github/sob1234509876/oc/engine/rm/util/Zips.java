package io.github.sob1234509876.oc.engine.rm.util;

import lombok.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.zip.ZipFile;

public final class Zips {

    public static final int ZIP_MAGIC_NUMBER = 0x504b0304;

    public static boolean isZipFile(@NonNull File file) {
        if (!file.isFile())
            return false;

        try (var is = new FileInputStream(file)) {
            var buffer = new byte[4];
            if (is.read(buffer) < 4)
                return false;

            var magicNumber = buffer[0] << 24 |
                    buffer[1] << 16 |
                    buffer[2] << 8 |
                    buffer[3];

            return magicNumber == ZIP_MAGIC_NUMBER;
        } catch (@NonNull IOException e) {
            throw new RuntimeException(e);
        }
    }

    @NonNull
    public static ZipFile getZipFile(@NonNull File file) {

        if (!isZipFile(file))
            throw new IllegalArgumentException("File is not a zip file");

        try {
            return new ZipFile(file);
        } catch (@NonNull IOException e) {
            throw new RuntimeException(e);
        }
    }

}
