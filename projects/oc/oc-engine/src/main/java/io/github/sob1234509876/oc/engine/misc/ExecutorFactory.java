package io.github.sob1234509876.oc.engine.misc;

import io.github.sob1234509876.oc.engine.api.Parent;
import lombok.NonNull;

import java.util.concurrent.Executor;

public interface ExecutorFactory extends Parent {

    @NonNull
    Executor create();

}
