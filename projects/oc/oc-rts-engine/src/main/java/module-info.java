module oc.projects.oc.rts.engine {

    // Transitive because the engine's own API exposes Natural: item amounts, capacities and hit points
    // are part of the public surface, so a consumer must be able to read the googology module.
    requires transitive oc.projects.oc.googology;

    exports io.github.sob1234509876.oc.rts;
    exports io.github.sob1234509876.oc.rts.core;
    exports io.github.sob1234509876.oc.rts.content;
    exports io.github.sob1234509876.oc.rts.economy;
    exports io.github.sob1234509876.oc.rts.entity;
    exports io.github.sob1234509876.oc.rts.event;
    exports io.github.sob1234509876.oc.rts.formation;
    exports io.github.sob1234509876.oc.rts.logistics;
    exports io.github.sob1234509876.oc.rts.map;
    exports io.github.sob1234509876.oc.rts.movement;
    exports io.github.sob1234509876.oc.rts.order;
    exports io.github.sob1234509876.oc.rts.production;
    exports io.github.sob1234509876.oc.rts.sim;
    exports io.github.sob1234509876.oc.rts.visual;
}
