/**
 * The Omni Com classic content plugin: the {@code oc-classic} game, shipped as a jar the server loads.
 *
 * <p>The module contains one plugin and its catalogue. It requires the server's plugin API - {@code Plugin},
 * {@code PluginContext} and the world content seam - and the engine, whose {@code ContentRegistry} the
 * catalogue fills. There is deliberately no third-party dependency: the art is a bundled PNG, the numbers are
 * Java constants and the server publishes both.
 */
module oc.projects.oc.game.server.classic {

    // The plugin API, and the engine whose content model this module fills.
    requires oc.projects.oc.game.server;
    requires oc.projects.oc.rts.engine;

    // The loader instantiates the plugin reflectively from the jar, so the package has to be exported.
    exports io.github.sob1234509876.oc.server.classic;
}
