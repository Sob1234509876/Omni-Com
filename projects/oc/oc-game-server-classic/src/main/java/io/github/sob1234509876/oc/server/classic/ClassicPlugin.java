package io.github.sob1234509876.oc.server.classic;

import io.github.sob1234509876.oc.server.plugin.Plugin;
import io.github.sob1234509876.oc.server.plugin.PluginContext;
import io.github.sob1234509876.oc.server.world.WorldTickHook;

import java.util.List;
import java.util.Objects;

/**
 * The {@code oc-classic} plugin: it claims the server's world with {@link ClassicContent} and then plays
 * that world with two tick hooks.
 *
 * <p><strong>What it does.</strong> Two things, in {@link #onLoad(PluginContext)}: it hands its catalogue to
 * the server through {@link PluginContext#registerWorldContent}, and it attaches the game's two
 * {@link io.github.sob1234509876.oc.server.world.WorldTickHook}s through
 * {@link PluginContext#registerWorldTickHook} - {@link ClassicEconomy} first, then {@link ClassicAi}. That
 * is the whole plugin. It registers no request, opens no file, starts no thread and keeps no reference to
 * the server.
 *
 * <p><strong>Why the order matters.</strong> The hooks run in registration order after every fixed world
 * step, so the economy pays a player's mines before the AI takes its decision for the same tick. Both hooks
 * are registered even though the server may serve a different plugin's world (the content seam is
 * first-one-wins): each hook checks that the world really is {@code oc-classic} and does nothing otherwise,
 * so a classic jar in a folder cannot break another game's world.
 *
 * <p><strong>What happens next is the server's decision, not the plugin's.</strong> A server serves the
 * first world content registered in load order and falls back to the engine's sample catalogue when none
 * was registered; see {@code OcGameServer#selectWorldContent}. Dropping this jar into the plugin folder
 * therefore makes the server run {@code oc-classic}, and removing it makes the server fall back - no
 * configuration file, and no way for a plugin to take over a world it did not claim.
 *
 * <p>The jar carries the content's art as well: {@code assets/atlas.png} (96 x 96) and
 * {@code assets/manifest.json}, published by the server's {@code /plugin-assets} route. The atlas was drawn
 * for this module; the manifest merges the terrain tile table of the hello plugin with the entity frames of
 * that art, because the drawn sheet contains units and buildings but no new terrain.
 */
public final class ClassicPlugin implements Plugin {

    /** The name of the plugin, which is also the id in its descriptor. */
    public static final String NAME = "oc-classic";

    /** The version of the plugin, matching the descriptor. */
    public static final String VERSION = "1.0";

    /** The catalogue this plugin offers; stateless, so one instance is enough. */
    private final ClassicContent content = new ClassicContent();

    /** The gold and wood income hook, one per loaded plugin (and therefore one per world). */
    private final ClassicEconomy economy = new ClassicEconomy();

    /** The enemy AI hook, one per loaded plugin (and therefore one per world). */
    private final ClassicAi ai = new ClassicAi();

    /** Creates the plugin; the loader uses the implicit public no-argument constructor it sees here. */
    public ClassicPlugin() {
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public String description() {
        return "Classic game content: five melee tiers, an engineer, two armory levels, three mines, three "
                + "lumber camps and a warehouse, with gold and wood income and an enemy AI";
    }

    /**
     * Registers the catalogue and the two world tick hooks with the server.
     *
     * <p>Registration happens at load time, before any socket opens and before the world is built, which is
     * why a catalogue that cannot be built fails startup instead of serving half a game.
     *
     * @param context the plugin's folder, data root, logger, request registry, content seam and tick seam
     */
    @Override
    public void onLoad(PluginContext context) {
        Objects.requireNonNull(context, "context").registerWorldContent(content);
        context.registerWorldTickHook(economy);
        context.registerWorldTickHook(ai);
    }

    /** The catalogue this plugin registered; exposed so the self check can assert it is the same object. */
    public ClassicContent content() {
        return content;
    }

    /** The income hook this plugin registered, in registration order before the AI. */
    public ClassicEconomy economy() {
        return economy;
    }

    /** The enemy AI hook this plugin registered, in registration order after the economy. */
    public ClassicAi ai() {
        return ai;
    }

    /** The hooks this plugin registers, in registration order; the same list {@link #onLoad} adds. */
    public List<WorldTickHook> worldTickHooks() {
        return List.of(economy, ai);
    }
}
