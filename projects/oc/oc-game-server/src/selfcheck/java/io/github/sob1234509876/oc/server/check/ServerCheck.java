package io.github.sob1234509876.oc.server.check;

/**
 * Runs every self check of the game server module: accounts and their local store, the encrypted TCP
 * channel, the plugin loader, the plugin request command, the browser facing HTTP/WebSocket endpoint, the
 * plugin asset route it publishes, the server side world snapshot, the plugin world tick hook, the
 * gameplay command grammar (identity, the world command queue, orders, build cost and training), and the
 * starting base a human player receives when it joins.
 *
 * <p>Runs with {@code ./gradlew :projects:oc:oc-game-server:selfcheck}; the same check classes are what a
 * future Gradle test task would call, so the module needs no test framework to be verified.
 */
public final class ServerCheck {

    public static void main(String[] args) {
        Check check = new Check();
        AuthCheck.run(check);
        NetworkCheck.run(check);
        PluginCheck.run(check);
        RequestCheck.run(check);
        WebSocketCheck.run(check);
        HttpCheck.run(check);
        PluginAssetsCheck.run(check);
        WorldHostCheck.run(check);
        WorldTickHookCheck.run(check);
        PlayerCommandCheck.run(check);
        JoinBaseCheck.run(check);
        BotsOptionCheck.run(check);
        check.summary("game server self check");
    }

    private ServerCheck() {
    }
}
