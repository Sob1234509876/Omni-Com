/**
 * The Omni Com hello world plugin: the smallest possible plugin, kept in its own module so the browser
 * client can show a complete plugin round trip without any game knowledge.
 *
 * <p>The module contains exactly one package and one plugin. It requires the server's passive plugin API -
 * {@code Plugin}, {@code PluginContext}, {@code RequestHandler} and the registry - and nothing else, which
 * is the point of the example: a plugin outside the server can answer requests, and that is all.
 */
module oc.projects.oc.game.server.hello {

    // The plugin API. There is deliberately no engine, management or networking dependency here.
    requires oc.projects.oc.game.server;

    // The loader instantiates the plugin reflectively from the jar, so the package has to be exported.
    exports io.github.sob1234509876.oc.server.hello;
}
