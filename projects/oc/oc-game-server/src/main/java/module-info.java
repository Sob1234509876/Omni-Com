module oc.projects.oc.game.server {

    // The server stays free of third-party dependencies: the JDK supplies the TCP socket, the X25519 key
    // exchange, AES-GCM and PBKDF2, and plugins bring their own libraries by dropping jars into the plugin
    // folder. The one in-repo dependency is the RTS engine: the optional server side world is an OcRtsEngine.
    requires oc.projects.oc.rts.engine;

    exports io.github.sob1234509876.oc.server;
    exports io.github.sob1234509876.oc.server.auth;
    exports io.github.sob1234509876.oc.server.net;
    exports io.github.sob1234509876.oc.server.plugin;
    exports io.github.sob1234509876.oc.server.ws;
    exports io.github.sob1234509876.oc.server.world;
}
