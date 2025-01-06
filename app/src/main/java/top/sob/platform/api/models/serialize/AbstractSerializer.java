package top.sob.platform.api.models.serialize;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import top.sob.platform.api.misc.model.AbstractModel;
import top.sob.platform.api.misc.model.ObjectCollector;

public abstract class AbstractSerializer extends AbstractModel<Object, SpecSerializer> implements Serializer {

    @Override
    public byte @NotNull [] serialize(@Nullable Object obj) {
        if (!filter(obj))
            throw new IllegalArgumentException();

        var bytes = ObjectCollector.create(obj, byte[].class);

        getSpecEventBus().post(bytes);

        return bytes.getReturned();

    }

    @Override
    public @NotNull Object deserialize(byte @NotNull [] bytes) {
        if (!filter(bytes))
            throw new IllegalArgumentException();

        var obj = ObjectCollector.create(bytes, Object.class);

        getSpecEventBus().post(obj);

        return obj.getReturned();

    }
}
