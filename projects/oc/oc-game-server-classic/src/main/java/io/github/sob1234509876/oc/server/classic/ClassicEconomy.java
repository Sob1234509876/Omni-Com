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
 * The classic game's mine income: the world tick hook that turns the content's {@code income_<n>} tag into
 * gold in the warehouse.
 *
 * <h2>The rule</h2>
 *
 * <p>Every <strong>60 ticks</strong> - the classic game's minute, which is three seconds at the host's
 * twenty ticks per second - each player receives, in gold, <strong>the sum of the incomes of the mines they
 * own</strong>. The income of one mine is the number its content type publishes as the
 * {@code income_<n>} tag, in gold per minute: {@code 30} for {@code building.mine.t1}, {@code 60} for
 * {@code .t2} and {@code 120} for {@code .t3}. A player with three tier one mines therefore gains
 * {@code 90} gold on that tick; a player with one of each tier gains {@code 210}.
 *
 * <p>The sums are exact integers ({@link Natural} in, {@link Natural} out); no floating point is involved
 * anywhere in this class, so income cannot drift with rounding.
 *
 * <p><strong>Only a standing mine pays.</strong> A building counts as a mine when it is
 * {@linkplain Building#isAlive() alive}, {@linkplain Building#isConstructed() constructed} and carries the
 * {@code income_<n>} tag. A building site that is still being raised pays nothing, and a destroyed mine pays
 * nothing, because neither is a working mine. The tag is read from the type, so a new mine tier added to
 * {@link ClassicContent} earns its own rate without this class changing.
 *
 * <p><strong>Paid into the warehouse.</strong> The gold is deposited through
 * {@link Logistics#deposit(ItemBundle, Vec2)} - the engine's drop-off rule, which puts goods into the
 * nearest warehouse of the owning player that accepts them. The deposit is anchored on the position of the
 * player's first paying mine (in entity id order), which is deterministic; because a player's warehouses
 * are one shared network, the anchor only decides which warehouse receives the stack first, not whether the
 * player is paid. Gold that no warehouse can accept is <em>not</em> paid: it is never dropped on the ground
 * and never queued, so a player who lost every warehouse simply stops earning.
 *
 * <p><strong>Paying once.</strong> The hook pays only when the tick number is a positive multiple of
 * {@value #PAYOUT_INTERVAL_TICKS}, and it remembers the last tick it paid, so being called twice with the
 * same tick number cannot pay twice. The engine advances its clock by exactly one per step, so under normal
 * operation every minute is paid exactly once.
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
        if (gold == null) {
            // Not the classic catalogue: the hook is registered for a world another plugin claimed. Doing
            // nothing is the documented behaviour, and the paid tick is still consumed.
            return;
        }
        for (Player player : world.players()) {
            payPlayer(world, player, gold);
        }
    }

    /** Pays one player the summed income of their standing mines, if any. */
    private static void payPlayer(World world, Player player, ItemType gold) {
        if (!player.hasLogistics()) {
            return;
        }
        long income = 0L;
        Vec2 dropOff = null;
        for (Building building : world.buildingsOf(player)) {
            if (!building.isAlive() || !building.isConstructed()) {
                continue;
            }
            long rate = incomePerMinute(building.type());
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
        player.logistics().deposit(ItemBundle.of(gold, Natural.of(income)), dropOff);
    }

    /**
     * The income of one building type in gold per minute, read from its {@code income_<n>} tag; {@code 0}
     * when the type is not an income building.
     *
     * <p>The first matching tag wins, and a malformed one is treated as "no income" instead of throwing:
     * a catalogue with a typo must not crash the world's tick thread.
     */
    public static long incomePerMinute(BuildingType type) {
        for (String tag : type.tags()) {
            if (tag.startsWith(ClassicContent.INCOME_TAG_PREFIX)) {
                String amount = tag.substring(ClassicContent.INCOME_TAG_PREFIX.length());
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
