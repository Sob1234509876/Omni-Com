/**
 * The Omni Com debug plugin: a read-only report surface that lives in a plugin rather than in the server, so
 * no security sensitive engine access has to ship inside the server itself.
 *
 * <p>The module is a plugin and a CLI, both built from the server's plugin API and the RTS engine:
 *
 * <ul>
 *   <li>{@code io.github.sob1234509876.oc.server.debug} - the plugin and its end-to-end encrypted envelope,
 *       dropped into the server's plugin folder as a jar;</li>
 *   <li>{@code io.github.sob1234509876.oc.server.debug.cli} - the command line client that asks the running
 *       server for a report, run from this module's own class path.</li>
 * </ul>
 *
 * <p>Both packages are exported because both are entry points an operator starts: the loader instantiates
 * the plugin reflectively, and the CLI has a {@code main}. Nothing here is meant to be required by another
 * module at compile time.
 */
module oc.projects.oc.game.server.debug {

    // The plugin API: Plugin, PluginContext, RequestHandler and the registry a plugin publishes into.
    requires oc.projects.oc.game.server;

    // The engine the report describes. It re-exports the googology library, which supplies Natural and the
    // hyperoperation table the report prints.
    requires oc.projects.oc.rts.engine;

    // ManagementFactory, for the JVM uptime, heap and thread counts the report carries. A named module has
    // to ask for it even though the package is part of the JDK.
    requires java.management;

    exports io.github.sob1234509876.oc.server.debug;
    exports io.github.sob1234509876.oc.server.debug.cli;
}
