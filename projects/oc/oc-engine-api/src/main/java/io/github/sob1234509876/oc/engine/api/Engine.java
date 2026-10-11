package io.github.sob1234509876.oc.engine.api;

import org.springframework.context.ApplicationEventPublisherAware;

public interface Engine extends ApplicationEventPublisherAware {

    int getMsu();

    boolean isStarted();

    void start();

}
