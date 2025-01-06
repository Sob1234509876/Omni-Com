package top.sob.platform.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import top.sob.platform.api.Platform;
import top.sob.platform.api.PlatformBuilder;
import top.sob.platform.api.PlatformExecutor;
import top.sob.platform.api.models.kit.StandardToolKit;
import top.sob.platform.api.reflect.PluginWrapper;
import top.sob.platform.api.reflect.ScriptWrapper;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.Set;

// TODO: Load reflective
public class PlatformImpl implements Platform {

    private final Set<URI> cp;
    private final StandardToolKit tk;

    public PlatformImpl(Set<URI> cp, StandardToolKit tk) {

        this.tk = (tk == null ? StandardToolKit.Companion.getDefault() : tk);
        this.cp = Collections.unmodifiableSet(cp);

    }

    @Unmodifiable
    public Set<URI> getClassPath() {
        return cp;
    }

    @Override
    public @NotNull PlatformBuilder builder() {
        return new PlatformBuilder();
    }

    @Override
    public @NotNull PlatformExecutor executor() {
        return new PlatformExecutorImpl();
    }

    @Override
    public @NotNull StandardToolKit getToolKits() {
        return tk;
    }

    @Override
    public @NotNull Set<@NotNull PluginWrapper> getPlugins() {
        return Set.of();
    }

    @Override
    public @NotNull Set<@NotNull ScriptWrapper> getScripts() {
        return Set.of();
    }

    @Override
    public @NotNull URL url(@NotNull URI uri) {
        try {
            return uri.toURL();
        } catch (MalformedURLException e) {
            throw new RuntimeException("This should not happen :", e);
        }
    }

    @Override
    public @NotNull URL url(@NotNull String string) {
        return url(URI.create(string));
    }
}
