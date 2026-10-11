package io.github.sob1234509876.oc.engine.resource;

import io.github.sob1234509876.oc.engine.api.Plugin;
import io.github.sob1234509876.oc.engine.api.ResourceManager;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
public class OcResourceManager implements ResourceManager {

    @NonNull
    List<@NonNull File> files = new LinkedList<>();

    @NonNull
    List<@NonNull Plugin> plugins = new LinkedList<>();

}
