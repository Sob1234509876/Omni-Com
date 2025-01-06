package top.sob.platform.api.models.serialize;

import top.sob.platform.api.misc.model.Spec;

import java.util.Map;

public interface SpecSerializer extends Spec<Object, Object>, Serializer {

    Map<Class<?>, SpecSerializer> SPECS = Map.of();

    @Override
    default Map<Class<?>, SpecSerializer> getSpecs() {
        return SPECS;
    }

    @Override
    default Object apply(Object o) {

        if (o instanceof byte[])
            return deserialize((byte[]) o);

        return serialize(o);
    }
}
