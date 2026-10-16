module oc.projects.oc.game.server {

    // The server is deliberately dependency free: the JDK supplies the TCP socket, the X25519 key exchange,
    // AES-GCM and PBKDF2, and plugins bring their own libraries by dropping jars into the plugin folder.
    exports io.github.sob1234509876.oc.server;
    exports io.github.sob1234509876.oc.server.auth;
    exports io.github.sob1234509876.oc.server.net;
    exports io.github.sob1234509876.oc.server.plugin;
}
