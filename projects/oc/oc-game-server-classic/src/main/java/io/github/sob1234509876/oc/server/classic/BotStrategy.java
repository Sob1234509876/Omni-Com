package io.github.sob1234509876.oc.server.classic;

/**
 * One bot's small, deterministic command style: how much economy it keeps, how big it grows its army, when
 * it marches, and whether it upgrades its armory before or after it fills the gaps in its base.
 *
 * <p><strong>Why a record.</strong> The classic AI's numbers used to be constants. A world with several bots
 * must not play the same game several times, so each {@link BotFaction} carries one of these and
 * {@link ClassicAi} reads the instance instead of the constants. {@link #LEGACY} is exactly the old
 * single-bot style, and the default {@code --bots 1} world still uses it, which is what keeps the historical
 * 600 tick snapshot byte for byte identical.
 *
 * <p><strong>The fields and their ranges.</strong> {@link #derive(long)} maps a faction seed to this record;
 * every field has a closed range and no field is random at simulation time:
 *
 * <table border="1">
 *   <caption>Strategy fields</caption>
 *   <tr><th>Field</th><th>Range</th><th>Legacy</th><th>Meaning</th></tr>
 *   <tr><td>{@link #mineTarget()}</td><td>2..4</td><td>3</td>
 *       <td>mines the bot tries to keep standing; drives the same count based site rule the old AI used</td></tr>
 *   <tr><td>{@link #engineerTarget()}</td><td>2..4</td><td>3</td>
 *       <td>engineers the bot tries to keep alive</td></tr>
 *   <tr><td>{@link #armyTarget()}</td><td>6..10</td><td>8</td>
 *       <td>army size the bot grows to before it stops training</td></tr>
 *   <tr><td>{@link #attackArmySize()}</td><td>4..8, and never above {@link #armyTarget()}</td><td>6</td>
 *       <td>the aggression threshold: the army size at which the bot enters {@link ClassicAi.Mode#ATTACK}
 *       and marches</td></tr>
 *   <tr><td>{@link #upgradeArmoryFirst()}</td><td>{@code true} or {@code false}</td><td>{@code true}</td>
 *       <td>{@code true}: an affordable armory upgrade wins a decision before a missing mine or lumber camp;
 *       {@code false}: the bot fills a missing armory, mine or camp first and upgrades only when its base is
 *       otherwise complete</td></tr>
 * </table>
 *
 * <p>The deriver is a pure function of its argument, built from SplitMix64's finalizer and integer
 * remainders. There is no {@code java.util.Random}, no clock and no platform-dependent {@code float} or
 * {@code double} arithmetic in it, so the same faction seed produces the same record on every run.
 *
 * @param mineTarget mines the bot tries to keep standing, 2..4
 * @param engineerTarget engineers the bot tries to keep alive, 2..4
 * @param armyTarget army size the bot grows towards, 6..10
 * @param attackArmySize the aggression threshold, 4..8 and no larger than {@code armyTarget}
 * @param upgradeArmoryFirst whether an affordable armory upgrade outranks a missing economy building
 */
public record BotStrategy(int mineTarget, int engineerTarget, int armyTarget, int attackArmySize,
        boolean upgradeArmoryFirst) {

    /** Smallest mine target a derived strategy may have. */
    public static final int MIN_MINE_TARGET = 2;

    /** Largest mine target a derived strategy may have. */
    public static final int MAX_MINE_TARGET = 4;

    /** Smallest engineer target a derived strategy may have. */
    public static final int MIN_ENGINEER_TARGET = 2;

    /** Largest engineer target a derived strategy may have. */
    public static final int MAX_ENGINEER_TARGET = 4;

    /** Smallest army target a derived strategy may have. */
    public static final int MIN_ARMY_TARGET = 6;

    /** Largest army target a derived strategy may have. */
    public static final int MAX_ARMY_TARGET = 10;

    /** Smallest aggression threshold a derived strategy may have. */
    public static final int MIN_ATTACK_ARMY_SIZE = 4;

    /** Largest aggression threshold a derived strategy may have. */
    public static final int MAX_ATTACK_ARMY_SIZE = 8;

    /**
     * The historical single-bot style, identical to the constants {@link ClassicAi} shipped with.
     *
     * <p>This is not a fallback for a missing derivation: it is the explicit replay contract of the default
     * one-bot world, and {@link ClassicBots} pins it for bot 0 on the default seed.
     */
    public static final BotStrategy LEGACY = new BotStrategy(ClassicAi.MINE_TARGET,
            ClassicAi.ENGINEER_TARGET, ClassicAi.ARMY_TARGET, ClassicAi.ATTACK_ARMY_SIZE, true);

    /** Validates the documented ranges; a strategy outside them is a programming error, not content data. */
    public BotStrategy {
        requireRange("mineTarget", mineTarget, MIN_MINE_TARGET, MAX_MINE_TARGET);
        requireRange("engineerTarget", engineerTarget, MIN_ENGINEER_TARGET, MAX_ENGINEER_TARGET);
        requireRange("armyTarget", armyTarget, MIN_ARMY_TARGET, MAX_ARMY_TARGET);
        requireRange("attackArmySize", attackArmySize, MIN_ATTACK_ARMY_SIZE, MAX_ATTACK_ARMY_SIZE);
        if (attackArmySize > armyTarget) {
            throw new IllegalArgumentException("attackArmySize must not exceed armyTarget, was "
                    + attackArmySize + " > " + armyTarget);
        }
    }

    /**
     * Derives a style from a faction seed.
     *
     * <p>Every field is selected by a different shift of the SplitMix64 finalizer output, so two nearby
     * faction seeds still differ in several fields. The result is nudged away from {@link #LEGACY} when it
     * would equal it exactly, because two bots in one world must not behave identically; the nudge is
     * deterministic, not a retry loop.
     *
     * @param factionSeed the per-bot seed, normally {@link ClassicBots#factionSeed(long, int)}
     * @return a strategy inside every documented range
     */
    public static BotStrategy derive(long factionSeed) {
        long hash = mix64(factionSeed);
        int mines = MIN_MINE_TARGET + (int) Long.remainderUnsigned(hash, 3L);
        int engineers = MIN_ENGINEER_TARGET + (int) Long.remainderUnsigned(hash >>> 8, 3L);
        int army = MIN_ARMY_TARGET + (int) Long.remainderUnsigned(hash >>> 16, 5L);
        int attack = MIN_ATTACK_ARMY_SIZE + (int) Long.remainderUnsigned(hash >>> 24, 5L);
        attack = Math.min(attack, army);
        boolean upgradeFirst = (hash & (1L << 32)) == 0L;
        BotStrategy derived = new BotStrategy(mines, engineers, army, attack, upgradeFirst);
        if (derived.equals(LEGACY)) {
            // Deterministic nudge: the mine target is the safe field to move, because it only changes which
            // missing building the site rule considers after the armory.
            derived = new BotStrategy(mines == MIN_MINE_TARGET ? MIN_MINE_TARGET + 1 : MIN_MINE_TARGET,
                    engineers, army, attack, upgradeFirst);
        }
        return derived;
    }

    /**
     * The SplitMix64 finalizer, used for every deterministic hash this module needs.
     *
     * <p>It is integer only (no {@code Math.sin}, no floating point), so the result is bit identical on
     * every platform and JVM, which is the property a replay needs.
     *
     * @param value the value to mix
     * @return the mixed 64 bit value
     */
    public static long mix64(long value) {
        long z = value + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static void requireRange(String field, int value, int minimum, int maximum) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(field + " must be " + minimum + ".." + maximum
                    + ", was " + value);
        }
    }
}
