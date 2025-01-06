package top.sob.platform.api.models.serialize.io;

import org.jetbrains.annotations.NotNull;
import top.sob.platform.api.misc.model.AbstractSpec;

import java.io.*;
import java.util.Objects;

public class IOSpecSerializerImpl extends AbstractSpec<Object, Object> implements IOSpecSerializer {

    private static final IOSpecSerializer INSTANCE = new IOSpecSerializerImpl();

    public static IOSpecSerializer getInstance() {
        return INSTANCE;
    }

    private final ByteArrayOutputStream stream = new ByteArrayOutputStream();

    private IOSpecSerializerImpl() {
    }

    @Override
    public byte @NotNull [] serialize(@NotNull Object obj) {

        try {
            new ObjectOutputStream(stream).writeObject(obj);
        } catch (IOException e) {
            throw new RuntimeException("This should not happen :", e);
            // Because if an I/O exception get thrown by this, it is probably because when it writes the headers or
            // blocks and an I/O exception occurred
        }

        var tmp = stream.toByteArray();

        stream.reset();

        return tmp;
    }

    @Override
    public @NotNull Object deserialize(byte @NotNull [] bytes) {

        Objects.requireNonNull(bytes);

        try {
            return new ObjectInputStream(new ByteArrayInputStream(bytes)).readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalArgumentException(e);
        }

    }

}
