package io.github.sob1234509876.oc.rts.visual;

import io.github.sob1234509876.oc.rts.entity.Unit;
import io.github.sob1234509876.oc.rts.logistics.HaulOrder;
import io.github.sob1234509876.oc.rts.order.AttackMoveOrder;
import io.github.sob1234509876.oc.rts.order.AttackOrder;
import io.github.sob1234509876.oc.rts.order.BuildOrder;
import io.github.sob1234509876.oc.rts.order.HoldOrder;
import io.github.sob1234509876.oc.rts.order.MoveOrder;
import io.github.sob1234509876.oc.rts.order.Order;
import io.github.sob1234509876.oc.rts.order.OrderQueue;
import io.github.sob1234509876.oc.rts.order.RepairOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The capped, player-readable list of a unit's queued tasks, in execution order.
 *
 * <p>The snapshot cannot send an unbounded queue: a player can Shift-click without limit, and a wire
 * array that grows with every click would make the document unbounded. The list is therefore capped at
 * {@value #MAX_ORDER_LABELS} entries. When the queue is longer, the first
 * {@code MAX_ORDER_LABELS - 1} labels are sent and the last entry is a marker such as
 * {@code … 还有 5 项}, so the client can show both the head of the queue and the fact that more exists.
 * The cap is a presentation rule only: the engine's queue keeps every order.
 *
 * <p>The labels are short and deterministic, built from the order's own references:
 *
 * <ul>
 *   <li>{@code 建造 兵工厂 @ (12,5)} for a build order (the site's display name and origin);</li>
 *   <li>{@code 修理 #42} for a repair order;</li>
 *   <li>{@code 搬运 → 仓库} for a haul order (its delivery target);</li>
 *   <li>{@code 移动 → (12,5)}, {@code 攻击 #42}, {@code 攻击移动 → (12,5)} and
 *       {@code 原地待命} for the other orders.</li>
 * </ul>
 *
 * <p>Only a unit has an order queue; a building answers an empty list. The class is DOM-free, draws no
 * random number and reads only the queue, so two captures of an unchanged world are identical.
 */
public final class OrderLabels {

    /** Largest number of entries one snapshot carries, including the truncation marker. */
    public static final int MAX_ORDER_LABELS = 8;

    /** Prefix of the entry that says how many labels did not fit. */
    public static final String TRUNCATION_PREFIX = "… 还有 ";

    /** Suffix of the truncation marker. */
    public static final String TRUNCATION_SUFFIX = " 项";

    private OrderLabels() {
    }

    /**
     * The labels of one unit's queue, capped at {@value #MAX_ORDER_LABELS} entries.
     *
     * @param unit the unit whose queue is read; {@code null} answers an empty list
     * @return an immutable list, never {@code null}
     */
    public static List<String> of(Unit unit) {
        return unit == null ? List.of() : of(unit.orders());
    }

    /**
     * The labels of one order queue, capped at {@value #MAX_ORDER_LABELS} entries.
     *
     * @param queue the queue to read; {@code null} answers an empty list
     * @return an immutable list, never {@code null}
     */
    public static List<String> of(OrderQueue queue) {
        if (queue == null) {
            return List.of();
        }
        List<Order> orders = queue.orders();
        if (orders.size() <= MAX_ORDER_LABELS) {
            List<String> labels = new ArrayList<>(orders.size());
            for (Order order : orders) {
                labels.add(label(order));
            }
            return List.copyOf(labels);
        }
        int shown = MAX_ORDER_LABELS - 1;
        List<String> labels = new ArrayList<>(MAX_ORDER_LABELS);
        for (int index = 0; index < shown; index++) {
            labels.add(label(orders.get(index)));
        }
        labels.add(TRUNCATION_PREFIX + (orders.size() - shown) + TRUNCATION_SUFFIX);
        return List.copyOf(labels);
    }

    /** One short label of one order; an order with no reference falls back to its stable name. */
    public static String label(Order order) {
        Objects.requireNonNull(order, "order");
        if (order instanceof BuildOrder build) {
            return build.site() == null
                    ? "建造（已完成）"
                    : "建造 " + build.site().displayName() + " @ (" + build.site().origin().x() + ","
                            + build.site().origin().y() + ")";
        }
        if (order instanceof RepairOrder repair) {
            return repair.building() == null ? "修理（已完成）" : "修理 #" + repair.building().id().value();
        }
        if (order instanceof HaulOrder haul) {
            return haul.job() == null ? "搬运" : "搬运 → " + haul.job().target().displayName();
        }
        if (order instanceof MoveOrder move) {
            return "移动 → (" + move.destination().tile().x() + "," + move.destination().tile().y() + ")";
        }
        if (order instanceof AttackOrder attack) {
            return "攻击 #" + attack.targetId().value();
        }
        if (order instanceof AttackMoveOrder attackMove) {
            if (attackMove.isTargeted() && attackMove.targetId().isPresent()) {
                return "攻击 #" + attackMove.targetId().orElseThrow().value();
            }
            return "攻击移动";
        }
        if (order instanceof HoldOrder) {
            return "原地待命";
        }
        return order.name();
    }
}
