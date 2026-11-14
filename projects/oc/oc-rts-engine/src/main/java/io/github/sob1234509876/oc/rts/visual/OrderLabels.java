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
 * The capped, client-parseable list of a unit's queued tasks, in execution order.
 *
 * <p>The snapshot cannot send an unbounded queue: a player can Shift-click without limit, and a wire
 * array that grows with every click would make the document unbounded. The list is therefore capped at
 * {@value #MAX_ORDER_LABELS} entries. When the queue is longer, the first
 * {@code MAX_ORDER_LABELS - 1} entries are sent and the last entry is the truncation marker
 * {@code more <n>}, so the client can show both the head of the queue and the fact that more exists.
 * The cap is a presentation rule only: the engine's queue keeps every order.
 *
 * <p>Every entry is a <strong>stable ASCII token</strong> followed by numeric arguments, never a
 * rendered sentence. The engine used to emit Chinese display text here, which forced every client to
 * parse one fixed language; the token vocabulary below instead lets a client map
 * {@code order.<token>} through its own dictionary and resolve the referenced entity's name from the
 * same snapshot. An old client that renders the raw string shows the readable token, and a Chinese
 * client gets its own words back from the dictionary.
 *
 * <ul>
 *   <li>{@code build <siteId> <x> <y>} - build order on the site with that entity id at that origin
 *       tile; bare {@code build} when the order already dropped its site reference;</li>
 *   <li>{@code repair <buildingId>} - repair order, bare {@code repair} without a reference;</li>
 *   <li>{@code haul <buildingId>} - haul order towards its delivery target, bare {@code haul}
 *       without one;</li>
 *   <li>{@code move <x> <y>} - plain move to the destination tile;</li>
 *   <li>{@code attack <entityId>} - attack order on one entity;</li>
 *   <li>{@code attackMove <entityId>} or bare {@code attackMove} - attack move onto an entity or
 *       towards a point;</li>
 *   <li>{@code guard} - hold position;</li>
 *   <li>{@code more <n>} - truncation marker, always the last entry when present;</li>
 *   <li>any order the vocabulary does not list falls back to its engine name, which is already a
 *       lowercase ASCII token.</li>
 * </ul>
 *
 * <p>Only a unit has an order queue; a building answers an empty list. The class is DOM-free, draws no
 * random number and reads only the queue, so two captures of an unchanged world are identical.
 */
public final class OrderLabels {

    /** Largest number of entries one snapshot carries, including the truncation marker. */
    public static final int MAX_ORDER_LABELS = 8;

    /** Token of a build order; arguments are the site id and the site origin tile. */
    public static final String TOKEN_BUILD = "build";
    /** Token of a repair order; the argument is the building's entity id. */
    public static final String TOKEN_REPAIR = "repair";
    /** Token of a haul order; the argument is the delivery target's entity id. */
    public static final String TOKEN_HAUL = "haul";
    /** Token of a plain move; the arguments are the destination tile coordinates. */
    public static final String TOKEN_MOVE = "move";
    /** Token of an attack order; the argument is the target's entity id. */
    public static final String TOKEN_ATTACK = "attack";
    /** Token of an attack move; optional argument is a chased entity's id. */
    public static final String TOKEN_ATTACK_MOVE = "attackMove";
    /** Token of a hold order; it carries no argument. */
    public static final String TOKEN_GUARD = "guard";
    /** Token of the truncation marker; the argument is how many labels did not fit. */
    public static final String TOKEN_MORE = "more";

    private OrderLabels() {
    }

    /**
     * The entries of one unit's queue, capped at {@value #MAX_ORDER_LABELS} entries.
     *
     * @param unit the unit whose queue is read; {@code null} answers an empty list
     * @return an immutable list, never {@code null}
     */
    public static List<String> of(Unit unit) {
        return unit == null ? List.of() : of(unit.orders());
    }

    /**
     * The entries of one order queue, capped at {@value #MAX_ORDER_LABELS} entries.
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
        labels.add(TOKEN_MORE + " " + (orders.size() - shown));
        return List.copyOf(labels);
    }

    /** One token entry of one order; an order with no reference sends the bare token. */
    public static String label(Order order) {
        Objects.requireNonNull(order, "order");
        if (order instanceof BuildOrder build) {
            return build.site() == null
                    ? TOKEN_BUILD
                    : TOKEN_BUILD + " " + build.site().id().value() + " " + build.site().origin().x()
                            + " " + build.site().origin().y();
        }
        if (order instanceof RepairOrder repair) {
            return repair.building() == null
                    ? TOKEN_REPAIR
                    : TOKEN_REPAIR + " " + repair.building().id().value();
        }
        if (order instanceof HaulOrder haul) {
            return haul.job() == null
                    ? TOKEN_HAUL
                    : TOKEN_HAUL + " " + haul.job().target().id().value();
        }
        if (order instanceof MoveOrder move) {
            return TOKEN_MOVE + " " + move.destination().tile().x() + " " + move.destination().tile().y();
        }
        if (order instanceof AttackOrder attack) {
            return TOKEN_ATTACK + " " + attack.targetId().value();
        }
        if (order instanceof AttackMoveOrder attackMove) {
            if (attackMove.isTargeted() && attackMove.targetId().isPresent()) {
                return TOKEN_ATTACK_MOVE + " " + attackMove.targetId().orElseThrow().value();
            }
            return TOKEN_ATTACK_MOVE;
        }
        if (order instanceof HoldOrder) {
            return TOKEN_GUARD;
        }
        return order.name();
    }
}
