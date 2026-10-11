package io.github.sob1234509876.oc.engine;

import io.github.sob1234509876.oc.engine.resource.OcResourceManager;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
public class OcInitializer implements ApplicationContextAware {

    public static final String PLUGIN_CLASS_MANIFEST_KEY = "Plugin-Class";

    @NonNull
    ApplicationContext applicationContext;

    @NonNull
    OcEngine ocEngine;

    @NonNull
    OcResourceManager ocResourceManager;

    @NonNull
    String pluginPath;

    @SuppressWarnings("resource")
    @PostConstruct
    public void initialize() throws IOException {
        var pp = new File(pluginPath)
                .getAbsoluteFile();

        if (!pp.exists())
            if (!pp.mkdirs())
                throw new IOException("Failed to mkdirs for " + pp);

        if (!pp.isDirectory())
            throw new IllegalArgumentException("Field pluginPath is a file's path");

        var files = pp.listFiles(File::isFile);

        if (files == null)
            throw new IOException();

        var ucl = new URLClassLoader(Arrays.stream(files)
                .map(File::toURI)
                .map(uri -> {
                    try {
                        return uri.toURL();
                    } catch (@NonNull MalformedURLException e) {
                        throw new RuntimeException(e);
                    }
                })
                .toArray(URL[]::new));

        if (!(applicationContext instanceof BeanDefinitionRegistry registry))
            throw new IllegalArgumentException("applicationContext is not a BeanDefinitionRegistry");

        Arrays.stream(files)
                .map(file -> {
                    try {
                        return new JarFile(file);
                    } catch (@NonNull IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .map(jar -> {
                    try {
                        return jar.getManifest();
                    } catch (@NonNull IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .map(Manifest::getMainAttributes)
                .map(attributes -> attributes.getValue(PLUGIN_CLASS_MANIFEST_KEY))
                .map(className -> {
                    try {
                        return ucl.loadClass(className);
                    } catch (@NonNull ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                })
                .map(RootBeanDefinition::new)
                .forEach(rootBeanDefinition -> registry.registerBeanDefinition(rootBeanDefinition.getBeanClass()
                                .getSimpleName(),
                        rootBeanDefinition));

    }

}
