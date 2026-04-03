module oc.projects.oc.googology.test {
    requires org.junit.jupiter.api;
    requires oc.projects.oc.googology;
    requires org.slf4j;
    requires static lombok;
    requires org.junit.jupiter.params;

    exports io.github.sob1234509876.oc.googology.test.ordinal.finite;
    exports io.github.sob1234509876.oc.googology.test.ordinal.transfinite;
}