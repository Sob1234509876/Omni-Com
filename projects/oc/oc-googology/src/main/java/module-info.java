module oc.projects.oc.googology {
    requires spring.boot.autoconfigure;
    requires spring.context;
    requires static lombok;
    requires spring.boot;
    requires oc.projects.oc.engine.api;
    requires spring.expression;
    requires org.slf4j;

    exports io.github.sob1234509876.oc.googology.ordinal.finite.hierarchy;
    exports io.github.sob1234509876.oc.googology.ordinal.transfinite;
    exports io.github.sob1234509876.oc.googology.ordinal;

    exports io.github.sob1234509876.oc.googology.optional;
    exports io.github.sob1234509876.oc.googology.optional.finite;
    exports io.github.sob1234509876.oc.googology.optional.transfinite;

    exports io.github.sob1234509876.oc.googology.impl;
    exports io.github.sob1234509876.oc.googology.fs;
    exports io.github.sob1234509876.oc.googology.support;

    exports io.github.sob1234509876.oc.googology.fs.mountain;
}