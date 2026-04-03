module oc.projects.oc.googology {
    requires spring.boot.autoconfigure;
    requires spring.context;
    requires static lombok;
    requires spring.boot;
    requires oc.projects.oc.engine.api;
    requires spring.expression;
    requires org.slf4j;

    exports io.github.sob1234509876.oc.googology.ordinal.op;
    exports io.github.sob1234509876.oc.googology.ordinal.finite.function;
    exports io.github.sob1234509876.oc.googology.ordinal.finite.successor;
    exports io.github.sob1234509876.oc.googology.ordinal.finite.hierarchy;
    exports io.github.sob1234509876.oc.googology.ordinal.transfinite;
    exports io.github.sob1234509876.oc.googology.ordinal.transfinite.constant;

    exports io.github.sob1234509876.oc.googology.optional;
    exports io.github.sob1234509876.oc.googology.optional.op;
    exports io.github.sob1234509876.oc.googology.optional.finite;
    exports io.github.sob1234509876.oc.googology.optional.transfinite;
    exports io.github.sob1234509876.oc.googology.optional.transfinite.constant;

    exports io.github.sob1234509876.oc.googology.collection;
    exports io.github.sob1234509876.oc.googology.annotation;
    exports io.github.sob1234509876.oc.googology.impl;
    exports io.github.sob1234509876.oc.googology.support;
}