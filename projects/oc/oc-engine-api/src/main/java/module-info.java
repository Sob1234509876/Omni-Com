module osl.projects.oc.engine {
    requires static lombok;
    requires java.desktop;
    exports io.github.sob1234509876.oc.engine.api;
    exports io.github.sob1234509876.oc.engine.api.collection;
    exports io.github.sob1234509876.oc.engine.api.ui;
    exports io.github.sob1234509876.oc.engine.api.type;
    exports io.github.sob1234509876.oc.engine.api.event;
    exports io.github.sob1234509876.oc.engine.api.registry;
}