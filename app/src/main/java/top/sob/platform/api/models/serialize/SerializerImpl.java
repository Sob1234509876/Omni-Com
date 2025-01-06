package top.sob.platform.api.models.serialize;

import top.sob.platform.api.models.serialize.io.IOSpecSerializer;

import java.util.Map;

public class SerializerImpl extends AbstractSerializer {

    private static final Serializer INSTANCE = new SerializerImpl();
    private static final Map<Class<?>, SpecSerializer> SERIALIZERS = Map.of(Object.class, IOSpecSerializer.getDefault());

    public static Serializer getInstance() {
        return INSTANCE;
    }

    private SerializerImpl() {
    }

    @Override
    public Map<Class<?>, SpecSerializer> getSpecs() {
        return SERIALIZERS;
    }
}
