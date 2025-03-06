package top.sob.platform.spring;

import org.springframework.context.annotation.Import;
import top.sob.platform.spring.configuration.PlatformConfiguration;

import java.lang.annotation.*;

@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(PlatformConfiguration.class)
public @interface EnablePlatform {
}
