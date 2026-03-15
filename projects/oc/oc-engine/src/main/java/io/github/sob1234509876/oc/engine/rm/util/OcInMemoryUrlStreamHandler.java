package io.github.sob1234509876.oc.engine.rm.util;

import lombok.*;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@RequiredArgsConstructor
public class OcInMemoryUrlStreamHandler extends URLStreamHandler {

    public static final String PROTOCOL = "memory";

    @NonNull
    URL url;

    byte @NonNull [] data;

    @NonNull
    public static URL create(byte @NonNull [] data) {
        try {
            var ush = new OcInMemoryUrlStreamHandler();
            var url = new URL(PROTOCOL,
                    "localhost",
                    -1,
                    UUID.randomUUID()
                            .toString(),
                    ush);

            ush.setData(data);
            ush.setUrl(url);

            return url;
        } catch (@NonNull MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    @NonNull
    @Override
    protected URLConnection openConnection(@NonNull URL u) throws IOException {

        if (!u.getProtocol()
                .equals(PROTOCOL))
            throw new IOException(u.getProtocol());

        return new OcInMemoryUrlConnection(url, data);
    }

}
