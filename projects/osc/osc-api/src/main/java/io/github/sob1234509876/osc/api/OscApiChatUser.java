package io.github.sob1234509876.osc.api;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.annotation.Id;

@Data
@NoArgsConstructor
public class OscApiChatUser {

    public static final String ID_FIELD = "id";

    @Id
    private long id;
    @Nullable
    private String bio;

}
