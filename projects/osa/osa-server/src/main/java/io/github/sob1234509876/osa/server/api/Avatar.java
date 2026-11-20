package io.github.sob1234509876.osa.server.api;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.springframework.data.annotation.Id;

@Data
@NoArgsConstructor
public class Avatar {

    private byte @NonNull [] image;

    @Id
    private long id;

    @SuppressWarnings({"ConstantValue",
            "unused"})
    public boolean isUnsafe() {
        return getImage() == null;
    }

}
