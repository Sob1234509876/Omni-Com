package io.github.sob1234509876.oc.engine.api;

import org.springframework.context.ApplicationContextAware;

public interface Plugin extends ApplicationContextAware {

    void initialize();

}
