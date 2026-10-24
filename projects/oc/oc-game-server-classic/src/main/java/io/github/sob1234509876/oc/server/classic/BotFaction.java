package io.github.sob1234509876.oc.server.classic;

import io.github.sob1234509876.oc.rts.core.GridPos;

import java.util.Objects;

/**
 * One bot player of a classic world: which player id it owns, the seed its style and spawn were derived
 * from, that style, and the deterministic anchor its opening base is searched around.
 *
 * <p><strong>Identity.</strong> Bot {@code i} owns player {@code 2} for {@code i = 0}, and player
 * {@code 9 + (i - 1)} for the extra bots: player 2 is the historical single bot, and the extra ids start
 * above the human id range (3 through 8) so a bot never takes a human slot and a human join can never
 * collide with a bot. The name and colour are pure functions of the index, so no player identity is random
 * either.
 *
 * <p><strong>Seed.</strong> The seed is <em>not</em> the world seed: it is
 * {@link ClassicBots#factionSeed(long, int)} of the world seed and the index, so several bots of one world
 * get different styles and anchors while a rebuilt world with the same seed reproduces every one of them.
 *
 * <p>The anchor is only a hint. {@link ClassicAi} still searches rings of buildable ground around it, because
 * the served terrain is noise and a fixed tile can be water or rock; see
 * {@link ClassicBots#spawnAnchor(long, int)} for the exact rule.
 *
 * @param index zero based bot number, 0..{@link ClassicBots#MAX_BOTS}-1
 * @param playerId the player id this bot controls, see {@link ClassicBots#playerIdOf(int)}
 * @param seed the per-bot faction seed both the strategy and the anchor derive from
 * @param strategy the bot's command style
 * @param spawnAnchor the anchor its opening base search starts from
 */
public record BotFaction(int index, int playerId, long seed, BotStrategy strategy, GridPos spawnAnchor) {

    /** Colours of the bots after the first one, cycled by index. The first bot keeps the historical red. */
    private static final int[] EXTRA_COLORS = {
        0xFFFF7043, 0xFFAB47BC, 0xFF26A69A, 0xFF8D6E63, 0xFFEC407A, 0xFFD4E157
    };

    /** Validates the identity the bot factory promises. */
    public BotFaction {
        if (index < 0 || index >= ClassicBots.MAX_BOTS) {
            throw new IllegalArgumentException("bot index must be 0.." + (ClassicBots.MAX_BOTS - 1)
                    + ", was " + index);
        }
        if (playerId != ClassicBots.playerIdOf(index)) {
            throw new IllegalArgumentException("bot " + index + " must own player "
                    + ClassicBots.playerIdOf(index) + ", was " + playerId);
        }
        Objects.requireNonNull(strategy, "strategy");
        Objects.requireNonNull(spawnAnchor, "spawnAnchor");
    }

    /** The display name of this bot's player: the historical {@code Enemy} first, then {@code Enemy 2}, ... */
    public String name() {
        return index == 0 ? ClassicAi.ENEMY_NAME : ClassicAi.ENEMY_NAME + " " + (index + 1);
    }

    /** The colour of this bot's player: the historical red first, then the fixed extra palette. */
    public int color() {
        return index == 0 ? ClassicAi.ENEMY_COLOR : EXTRA_COLORS[(index - 1) % EXTRA_COLORS.length];
    }
}
