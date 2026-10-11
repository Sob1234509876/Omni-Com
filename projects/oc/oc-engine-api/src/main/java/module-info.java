module oc.projects.oc.engine.api {

    requires static lombok;
    requires spring.context;

    exports io.github.sob1234509876.oc.engine.api;
    exports io.github.sob1234509876.oc.engine.api.googology;
    exports io.github.sob1234509876.oc.engine.api.event;
}