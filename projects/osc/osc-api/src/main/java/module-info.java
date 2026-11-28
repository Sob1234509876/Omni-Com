module osl.projects.osc.api {
    requires spring.data.commons;
    requires static lombok;
    requires org.jetbrains.annotations;

    exports io.github.sob1234509876.osc.api.resource;
    exports io.github.sob1234509876.osc.api.social;
    exports io.github.sob1234509876.osc.api.request;
}
