package io.github.sob1234509876.oc.rts.formation;

import io.github.sob1234509876.oc.rts.core.EntityId;
import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.entity.Unit;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The result of arranging a group: where the formation sits, which slot every unit received and how much
 * room the slots have.
 *
 * <p>A plan is an immutable value object. Orders copy the fields they need into the unit's
 * {@link io.github.sob1234509876.oc.rts.movement.Motion}, so a plan may be thrown away as soon as the
 * group has been ordered - which is what happens for a frame perfect order that is replaced a tick later.
 *
 * <p>The slot list is authoritative: {@link #unitsInSlotOrder()} is indexed by slot, so walking the list
 * in order visits the formation from its first slot to its last one, identically on every machine. The
 * unit to slot map is only meant for lookups of a single unit; its iteration order is not specified and
 * nothing in the simulation iterates it.
 *
 * @param shape           pattern the slots were generated from
 * @param anchor          world position the slots are relative to, usually the ordered destination
 * @param spacing         distance between neighbouring slots in tiles
 * @param slotOffsets     offset of every slot from the anchor, indexed by slot
 * @param unitsInSlotOrder unit that owns each slot, indexed by slot and aligned with {@code slotOffsets}
 * @param slotOfUnit      reverse lookup from a unit to its slot, for steering and debugging
 */
public record FormationPlan(
        FormationShape shape,
        Vec2 anchor,
        float spacing,
        List<Vec2> slotOffsets,
        List<Unit> unitsInSlotOrder,
        Map<EntityId, Integer> slotOfUnit) {

    public FormationPlan {
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(slotOffsets, "slotOffsets");
        Objects.requireNonNull(unitsInSlotOrder, "unitsInSlotOrder");
        Objects.requireNonNull(slotOfUnit, "slotOfUnit");
        if (slotOffsets.size() != unitsInSlotOrder.size()) {
            throw new IllegalArgumentException("every slot needs exactly one unit, but there are "
                    + slotOffsets.size() + " slots for " + unitsInSlotOrder.size() + " units");
        }
        slotOffsets = List.copyOf(slotOffsets);
        unitsInSlotOrder = List.copyOf(unitsInSlotOrder);
        slotOfUnit = Map.copyOf(slotOfUnit);
        spacing = Math.max(0f, spacing);
    }

    /** Number of slots, which is also the number of units in the plan. */
    public int slotCount() {
        return slotOffsets.size();
    }

    /** Offset of the slot from the anchor. */
    public Vec2 slotOffset(int slot) {
        return slotOffsets.get(slot);
    }

    /** World position of the slot. */
    public Vec2 slotPosition(int slot) {
        return anchor.plus(slotOffsets.get(slot));
    }

    /** World positions of every slot, in slot order. */
    public List<Vec2> slotPositions() {
        return slotOffsets.stream().map(anchor::plus).toList();
    }

    /** Unit that owns the slot. */
    public Unit unitInSlot(int slot) {
        return unitsInSlotOrder.get(slot);
    }

    /** Slot of the unit, or {@code -1} when the unit is not part of this plan. */
    public int slotFor(Unit unit) {
        Integer slot = slotOfUnit.get(unit.id());
        return slot == null ? -1 : slot;
    }

    /** World position the unit was assigned to, for debug output. */
    public Vec2 slotPositionFor(Unit unit) {
        int slot = slotFor(unit);
        return slot < 0 ? anchor : slotPosition(slot);
    }

    /** Whether the unit takes part in this formation at all. */
    public boolean contains(Unit unit) {
        return slotOfUnit.containsKey(unit.id());
    }

    /** Number of units, the same as {@link #slotCount()}. */
    public int unitCount() {
        return unitsInSlotOrder.size();
    }

    @Override
    public String toString() {
        return "FormationPlan{" + shape.id() + ", " + slotCount() + " slots at " + anchor
                + ", spacing " + String.format(java.util.Locale.ROOT, "%.2f", spacing) + "}";
    }
}
