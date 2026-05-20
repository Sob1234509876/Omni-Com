module oc.projects.oc.googology.test {
    requires org.junit.jupiter.api;
    requires oc.projects.oc.googology;
    requires static lombok;
    requires oc.projects.oc.engine.api;

    exports io.github.sob1234509876.oc.googology.test.ordinal.finite;
    exports io.github.sob1234509876.oc.googology.test.ordinal.transfinite;
}