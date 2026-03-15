package io.github.sob1234509876.oc.engine.rm.util;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

@Getter
@Setter
public class OcInMemoryUrlConnection extends URLConnection {

    byte @NonNull [] data;

    /**
     * Constructs a URL connection to the specified URL. A connection to
     * the object referenced by the URL is not created.
     *
     * @param url the specified URL.
     */
    public OcInMemoryUrlConnection(@NonNull URL url, byte @NonNull [] data) {
        super(url);
        this.data = data;
    }

    @Override
    public void connect() {
    }

    @Override
    public InputStream getInputStream() throws IOException {

        if (getDoInput())
            return new ByteArrayInputStream(data);

        throw new IOException("Do input is false");
    }
}
