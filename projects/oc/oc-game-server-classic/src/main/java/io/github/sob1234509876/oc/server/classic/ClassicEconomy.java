package io.github.sob1234509876.oc.server.classic;

import io.github.sob1234509876.oc.googology.Natural;
import io.github.sob1234509876.oc.rts.Player;
import io.github.sob1234509876.oc.rts.World;
import io.github.sob1234509876.oc.rts.content.BuildingType;
import io.github.sob1234509876.oc.rts.content.ItemBundle;
import io.github.sob1234509876.oc.rts.content.ItemType;
import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.economy.Logistics;
import io.github.sob1234509876.oc.rts.entity.Building;
import io.github.sob1234509876.oc.server.world.WorldTickHook;

/**
 * The classic game's passive income: the world tick hook that turns the content's {@code income_<n>} and
 * {@code wood_income_<n>} tags into gold and wood in the warehouse.
 *
 * <h2>The rule</h2>
 *
 * <p>Every <strong>60 ticks</strong> - the classic game's minute, which is three seconds at the host's
 * twenty ticks per second - each player receives, in gold, <strong>the sum of the incomes of the mines they
 * own</strong>, and in wood, <strong>the sum of the wood incomes of their lumber camps</strong>. The
 * income of one mine is the number its content type publishes as the {@code income_<n>} tag, in gold per
 * minute: {@code 30} for {@code building.mine.t1}, {@code 60} for {@code .t2} and {@code 120} for
 * {@code .t3}. A player with three tier one mines therefore gains {@code 90} gold on that tick; a player
 * with one of each tier gains {@code 210}. A lumber camp publishes its wood rate the same way, in the
 * {@code wood_income_<n>} tag: {@code 20}/{@code 40}/{@code 80} wood per minute for
 * {@code building.lumbercamp.t1}/{@code .t2}/{@code .t3}.
 *
 * <p>The sums are exact integers ({@link Natural} in, {@link Natural} out); no floating point is involved
 * anywhere in this class, so income cannot drift with rounding.
 *
 * <p><strong>Only a standing building pays.</strong> A building counts as a producer when it is
 * {@linkplain Building#isAlive() alive}, {@linkplain Building#isConstructed() constructed} and carries the
 * matching income tag. A building site that is still being raised pays nothing, and a destroyed building
 * pays nothing, because neither is working. The tags are read from the type, so a new mine or lumber camp
 * tier added to {@link ClassicContent} earns its own rate without this class changing.
 *
 * <p><strong>Paid into the warehouse.</strong> Both resources are deposited through
 * {@link Logistics#deposit(ItemBundle, Vec2)} - the engine's drop-off rule, which puts goods into the
 * nearest warehouse of the owning player that accepts them. The deposit is anchored on the position of the
 * player's first paying building of that resource (in entity id order), which is deterministic; because a
 * player's warehouses are one shared network, the anchor only decides which warehouse receives the stack
 * first, not whether the player is paid. Goods that no warehouse can accept are <em>not</em> paid: they are
 * never dropped on the ground and never queued, so a player who lost every warehouse simply stops earning.
 *
 * <p><strong>Paying once.</strong> The hook pays only when the tick number is a positive multiple of
 * {@value #PAYOUT_INTERVAL_TICKS}, and it remembers the last tick it paid, so being called twice with the
 * same tick number cannot pay twice - neither the gold nor the wood. The engine advances its clock by
 * exactly one per step, so under normal operation every minute is paid exactly once.
 *
 * <p>The hook is registered by {@link ClassicPlugin} after the content, runs after the engine tick, and
 * only touches the engine's public API: the player list, the building list and the logistics network. One
 * instance belongs to one world; the plugin creates it once per loaded plugin and the host owns the world
 * for the lifetime of the instance.
 */
public final class ClassicEconomy implements WorldTickHook {

    /** Ticks between two payouts: the classic game's minute, 60 fixed steps. */
    public static final int PAYOUT_INTERVAL_TICKS = 60;

    /** The last tick this hook paid, or {@code -1} before the first payout. */
    private long lastPaidTick = -1L;

    /** Creates the hook; a plugin owns one instance for the world it is loaded into. */
    public ClassicEconomy() {
    }

    @Override
    public void tick(World world, long tick) {
        if (tick <= 0 || tick % PAYOUT_INTERVAL_TICKS != 0 || tick <= lastPaidTick) {
            return;
        }
        lastPaidTick = tick;
        ItemType gold = world.content().itemOrNull(world.content().key(ClassicContent.GOLD));
        ItemType wood = world.content().itemOrNull(world.content().key(ClassicContent.WOOD));
        if (gold == null && wood == null) {
            // Not the classic catalogue: the hook is registered for a world another plugin claimed. Doing
            // nothing is the documented behaviour, and the paid tick is still consumed.
            return;
        }
        for (Player player : world.players()) {
            payPlayer(world, player, gold, wood);
        }
    }

    /** Pays one player the summed gold of their standing mines and wood of their standing lumber camps. */
    private static void payPlayer(World world, Player player, ItemType gold, ItemType wood) {
        if (!player.hasLogistics()) {
            return;
        }
        pay(world, player, gold, true);
        pay(world, player, wood, false);
    }

    /**
     * One resource payout: sum the matching rate of every constructed, living building that produces it
     * and deposit the total through the player's logistics network.
     *
     * <p>The anchor is the first paying building in {@link World#buildingsOf(Player)} order, which is
     * creation order, so two runs of the same world pay into the same warehouse.
     */
    private static void pay(World world, Player player, ItemType item, boolean gold) {
        if (item == null) {
            return;
        }
        long income = 0L;
        Vec2 dropOff = null;
        for (Building building : world.buildingsOf(player)) {
            if (!building.isAlive() || !building.isConstructed()) {
                continue;
            }
            long rate = gold ? incomePerMinute(building.type()) : woodIncomePerMinute(building.type());
            if (rate <= 0L) {
                continue;
            }
            income += rate;
            if (dropOff == null) {
                dropOff = building.position();
            }
        }
        if (income <= 0L || dropOff == null) {
            return;
        }
        player.logistics().deposit(ItemBundle.of(item, Natural.of(income)), dropOff);
    }

    /**
     * The income of one building type in gold per minute, read from its {@code income_<n>} tag; {@code 0}
     * when the type is not a gold income building.
     *
     * <p>The first matching tag wins, and a malformed one is treated as "no income" instead of throwing:
     * a catalogue with a typo must not crash the world's tick thread.
     */
    public static long incomePerMinute(BuildingType type) {
        return taggedRate(type, ClassicContent.INCOME_TAG_PREFIX);
    }

    /**
     * The income of one building type in wood per minute, read from its {@code wood_income_<n>} tag;
     * {@code 0} when the type is not a lumber camp.
     *
     * <p>It uses the separate {@code wood_income_} prefix so a lumber camp can never be mistaken for a
     * gold mine: {@link #incomePerMinute(BuildingType)} only matches the shorter {@code income_} prefix.
     */
    public static long woodIncomePerMinute(BuildingType type) {
        return taggedRate(type, ClassicContent.WOOD_INCOME_TAG_PREFIX);
    }

    /** First {@code prefix<number>} tag of a type as a non-negative rate, or {@code 0}. */
    private static long taggedRate(BuildingType type, String prefix) {
        for (String tag : type.tags()) {
            if (tag.startsWith(prefix)) {
                String amount = tag.substring(prefix.length());
                try {
                    long value = Long.parseLong(amount);
                    return Math.max(0L, value);
                } catch (NumberFormatException malformed) {
                    return 0L;
                }
            }
        }
        return 0L;
    }

    /** The last tick this hook paid, for diagnostics and checks; {@code -1} before the first payout. */
    public long lastPaidTick() {
        return lastPaidTick;
    }
}
