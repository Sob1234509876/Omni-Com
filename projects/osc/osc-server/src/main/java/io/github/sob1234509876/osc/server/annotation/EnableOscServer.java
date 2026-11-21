package io.github.sob1234509876.osc.server.annotation;

import io.github.sob1234509876.osc.server.configuration.OscServerConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(OscServerConfiguration.class)
public @interface EnableOscServer {
}
