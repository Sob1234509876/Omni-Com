package io.github.sob1234509876.osa.api;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.springframework.data.annotation.Id;

@Data
@NoArgsConstructor
public class OsaApiAvatar {

    private byte @NonNull [] image;

    @Id
    private long id;

    @SuppressWarnings({"ConstantValue",
            "unused"})
    public boolean isUnsafe() {
        return getImage() == null;
    }

}
