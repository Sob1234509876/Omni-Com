package top.sob.platform.spring.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.Unmodifiable;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.util.Assert;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

@Log4j2
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformRunner implements ApplicationRunner, BeanFactoryAware {

    public static final String CP_OPTION_NAME = "cp";
    public static final String PLATFORM_CONFIGURATION_CLASSPATH = "platform.properties";

    BeanFactory beanFactory;

    @Override
    public void run(@NonNull ApplicationArguments args) throws IOException, ClassNotFoundException {
        log.info("Loading...");
        load(resolvePaths(args));
    }

    @Unmodifiable
    public List<Path> resolvePaths(@NonNull ApplicationArguments args) {
        Assert.notNull(args, "Argument \"args\" is null");

        log.info("CP option with \"{}\"", args.getOptionValues(CP_OPTION_NAME));

        var paths = args.getOptionValues(CP_OPTION_NAME);

        if (paths == null)
            return Collections.emptyList();

        var tmp = new LinkedList<Path>();

        for (var path : paths) {
            var p = Path.of(path);

            if (p.toFile().isFile()) tmp.add(p);

            if (p.toFile().isDirectory()) {
                var fs = p.toFile().list();
                if (fs != null) for (var f : fs)
                    tmp.add(Path.of(f));
            }
        }

        return Collections.unmodifiableList(tmp);
    }

    public void load(List<Path> cp) throws IOException, ClassNotFoundException {
        Assert.notNull(cp, "Argument \"cp\" is null");

        var urls = new LinkedList<URL>();

        for (var p : cp)
            urls.add(new URL(p.toString()));

        @SuppressWarnings("resource") var ucl = new URLClassLoader(urls.toArray(urls.toArray(new URL[0])));
        var rs = ucl.findResources(PLATFORM_CONFIGURATION_CLASSPATH);

        Assert.isInstanceOf(BeanDefinitionRegistry.class, getBeanFactory());
        var reg = (BeanDefinitionRegistry) getBeanFactory();

        for (var r : Collections.list(rs)) {
            var pc = new PlatformConfigurationImpl(r.openStream());
            var cls = ucl.loadClass(pc.getMainBeanClass());
            var bean = new RootBeanDefinition(cls);
            reg.registerBeanDefinition(pc.getMainBeanName(), bean);
        }

        log.info("Load finish");
    }
}
