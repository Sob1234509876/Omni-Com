package io.github.sob1234509876.osftp.dao.ftp;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
public class FtpPath {

    public static final char SEPARATOR = '/';

    @NonNull
    private List<String> dir;

    public FtpPath(@NonNull String dir) {
        setDir(new LinkedList<>());
        if (!dir.isEmpty())
            Collections.addAll(getDir(), stripSeparators(dir).split(String.valueOf(SEPARATOR)));
    }

    public String toString() {
        var sb = new StringBuilder();
        for (var dir : dir)
            sb.append('/')
                    .append(dir);
        return sb.toString();
    }

    public void toParent() {
        var dir = getDir();
        dir.remove(dir.size() - 1);
    }

    @SuppressWarnings("unused")
    public void toChild(String... child) {
        Collections.addAll(getDir(), child);
    }

    @NonNull
    private String stripSeparators(@NonNull String path) {
        var start = 0;
        var end = path.length();
        while (start < path.length() && path.charAt(start) == SEPARATOR)
            start++;
        while (end > 0 && path.charAt(end - 1) == SEPARATOR)
            end--;
        return path.substring(start, end);
    }
}
