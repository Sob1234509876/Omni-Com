package top.sob.platform.api.models.serialize;

import org.jetbrains.annotations.NotNull;
import top.sob.platform.api.misc.model.Model;
import top.sob.platform.proof.Integrity;

@Integrity(integrity = Integrity.STABLE)
public interface Serializer extends Model<Object, SpecSerializer> {

    byte @NotNull [] serialize(@NotNull Object obj);

    @NotNull Object deserialize(byte @NotNull [] bytes);

    static Serializer getDefault() {
        return SerializerImpl.getInstance();
    }

}
