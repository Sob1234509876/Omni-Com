package io.github.sob1234509876.oc.server.check;

/**
 * Runs every self check of the game server module: accounts and their local store, the encrypted TCP
 * channel, and the plugin loader.
 *
 * <p>Runs with {@code ./gradlew :projects:oc:oc-game-server:selfcheck}; the same three check classes are
 * what a future Gradle test task would call, so the module needs no test framework to be verified.
 */
public final class ServerCheck {

    public static void main(String[] args) {
        Check check = new Check();
        AuthCheck.run(check);
        NetworkCheck.run(check);
        PluginCheck.run(check);
        check.summary("game server self check");
    }

    private ServerCheck() {
    }
}
