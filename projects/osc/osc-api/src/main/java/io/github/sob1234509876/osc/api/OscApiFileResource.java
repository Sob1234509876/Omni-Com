package io.github.sob1234509876.osc.api;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.annotation.Id;

import java.time.Instant;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
public class OscApiFileResource {

    public static final String ID_FIELD = "id";
    public static final String UPLOADER_FIELD = "uploaderId";
    public static final String NAME_FIELD = "name";
    public static final String SIZE_FIELD = "size";
    public static final String CONTENT_TYPE_FIELD = "contentType";
    public static final String FTP_PATH_FIELD = "ftpPath";
    public static final String CREATE_TIME_FIELD = "createTime";

    @Id
    private long id;
    private long uploaderId;
    @NonNull
    private String name;
    private long size;
    @NonNull
    private String contentType;
    @NonNull
    private String ftpPath;
    @NonNull
    private Instant createTime;

}
