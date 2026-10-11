package io.github.sob1234509876.oc.rts.formation;

import io.github.sob1234509876.oc.rts.core.EntityId;
import io.github.sob1234509876.oc.rts.core.Vec2;
import io.github.sob1234509876.oc.rts.entity.Unit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Lays a group of units out in a formation and decides who stands where.
 *
 * <p>The planner is a pure function of the units, the anchor, the shape and the spacing: no world, no
 * random and no wall clock. That is what makes a group order reproducible, and it also means a scenario
 * can lay out a formation without spawning anything.
 *
 * <p>Geometry is generated in a canonical frame - the direction of travel is {@code +X} and the lateral
 * axis is {@code +Y} - and then rotated into the real facing. Slots are therefore always expressed
 * relative to the anchor and the same shape always produces the same pattern, mirrored or turned as a
 * whole rather than reshuffled.
 *
 * <p>Assignment is the part that decides whether a group walks as a group or crosses itself. Units are
 * considered from the one closest to the anchor outwards and each one takes the free slot nearest to it,
 * with the slot index and the entity id breaking ties. Nobody is sent to the far side of the formation
 * while a closer slot is still free, so a clustered group spreads into its slots instead of running
 * through its own ranks.
 */
public final class FormationPlanner {

    /** Close packed slots are useless below this distance, because bodies would overlap at once. */
    public static final float MIN_SPACING = 0.6f;
    /** Default factor between the widest body and the distance between two slots. */
    public static final float DEFAULT_SPACING_SCALE = 1.25f;
    /** Radius assumed for a group of units without a definition, so spacing never collapses to zero. */
    private static final float FALLBACK_RADIUS = 0.3f;

    private final float spacingScale;

    public FormationPlanner() {
        this(DEFAULT_SPACING_SCALE);
    }

    public FormationPlanner(float spacingScale) {
        if (!(spacingScale > 0f)) {
            throw new IllegalArgumentException("spacingScale must be positive, was " + spacingScale);
        }
        this.spacingScale = spacingScale;
    }

    /** Factor applied to {@code 2 * radius} when the spacing of a group is derived from its units. */
    public float spacingScale() {
        return spacingScale;
    }

    /** Spacing a group of these units should use: the widest body plus room to walk, never below 0.6. */
    public float defaultSpacing(Collection<Unit> units) {
        float maxRadius = 0f;
        if (units != null) {
            for (Unit unit : units) {
                if (unit != null && unit.isAlive()) {
                    maxRadius = Math.max(maxRadius, unit.radius());
                }
            }
        }
        if (maxRadius <= 0f) {
            maxRadius = FALLBACK_RADIUS;
        }
        return Math.max(MIN_SPACING, maxRadius * 2f * spacingScale);
    }

    /** Arranges the units in the shape, facing {@code +X}, which is the right default for a list. */
    public FormationPlan plan(Collection<Unit> units, Vec2 anchor, FormationShape shape, float spacing) {
        return plan(units, anchor, 0f, shape, spacing);
    }

    /**
     * Arranges the units in the shape, turned so that the formation advances along {@code facingRadians}.
     *
     * @param facingRadians direction of travel in radians; the anchor is the front of the formation for
     *                      {@link FormationShape#WEDGE} and the centre of it for every other shape
     */
    public FormationPlan plan(
            Collection<Unit> units,
            Vec2 anchor,
            float facingRadians,
            FormationShape shape,
            float spacing) {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(shape, "shape");
        List<Unit> ordered = living(units);
        float resolvedSpacing = spacing > 0f ? Math.max(spacing, MIN_SPACING) : defaultSpacing(ordered);
        if (ordered.isEmpty()) {
            return new FormationPlan(shape, anchor, resolvedSpacing, List.of(), List.of(), Map.of());
        }
        if (shape == FormationShape.NONE) {
            // "No arrangement" means every unit keeps the offset it already has, so the slots belong to
            // the units that produced them and must not be handed out by distance.
            List<Vec2> offsets = new ArrayList<>(ordered.size());
            Map<EntityId, Integer> slotOfUnit = new LinkedHashMap<>();
            for (int slot = 0; slot < ordered.size(); slot++) {
                offsets.add(ordered.get(slot).position().minus(anchor));
                slotOfUnit.put(ordered.get(slot).id(), slot);
            }
            return new FormationPlan(shape, anchor, resolvedSpacing, offsets, ordered, slotOfUnit);
        }

        List<Vec2> offsets = slotOffsets(shape, ordered.size(), resolvedSpacing, facingRadians);

        // Closest unit to the anchor first; ties are broken by id so the result does not depend on the
        // order in which the caller happened to collect the units.
        List<Unit> byDistance = new ArrayList<>(ordered);
        byDistance.sort(Comparator
                .comparingDouble((Unit unit) -> unit.position().distanceSquared(anchor))
                .thenComparingLong(unit -> unit.id().value()));

        boolean[] taken = new boolean[offsets.size()];
        List<Unit> unitsInSlotOrder = new ArrayList<>(Collections.nCopies(offsets.size(), (Unit) null));
        Map<EntityId, Integer> slotOfUnit = new LinkedHashMap<>();
        for (int i = 0; i < byDistance.size(); i++) {
            Unit unit = byDistance.get(i);
            int bestSlot = -1;
            float bestDistance = Float.MAX_VALUE;
            for (int slot = 0; slot < offsets.size(); slot++) {
                if (taken[slot]) {
                    continue;
                }
                float distance = unit.position().distanceSquared(anchor.plus(offsets.get(slot)));
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestSlot = slot;
                }
            }
            taken[bestSlot] = true;
            unitsInSlotOrder.set(bestSlot, unit);
            slotOfUnit.put(unit.id(), bestSlot);
        }
        return new FormationPlan(shape, anchor, resolvedSpacing, offsets, unitsInSlotOrder, slotOfUnit);
    }

    /** Slot offsets of the shape facing {@code +X}; {@code NONE} has no fixed slots and returns none. */
    public List<Vec2> slotOffsets(FormationShape shape, int count, float spacing) {
        return slotOffsets(shape, count, spacing, 0f);
    }

    /**
     * Slot offsets of the shape, rotated so that the formation advances along {@code facingRadians}.
     *
     * <p>This is the geometric core of the planner and is public so that tests and tools can assert a
     * pattern without spawning units: {@code line} spreads perpendicular to the direction of travel,
     * {@code column} single file along it, {@code box} a grid as square as the count allows, {@code wedge}
     * a triangle with its tip on the anchor and {@code circle} a ring around the anchor.
     *
     * @return exactly {@code count} offsets, or an empty list when {@code count} is not positive or the
     *         shape is {@link FormationShape#NONE}, whose slots depend on the units themselves
     */
    public List<Vec2> slotOffsets(FormationShape shape, int count, float spacing, float facingRadians) {
        Objects.requireNonNull(shape, "shape");
        if (count <= 0 || shape == FormationShape.NONE) {
            return List.of();
        }
        float step = Math.max(spacing, MIN_SPACING);
        List<Vec2> offsets = new ArrayList<>(count);
        switch (shape) {
            case LINE -> {
                for (int i = 0; i < count; i++) {
                    offsets.add(new Vec2(0f, (i - (count - 1) * 0.5f) * step));
                }
            }
            case COLUMN -> {
                for (int i = 0; i < count; i++) {
                    offsets.add(new Vec2((i - (count - 1) * 0.5f) * step, 0f));
                }
            }
            case BOX -> {
                int columns = (int) Math.ceil(Math.sqrt(count));
                int rows = (int) Math.ceil(count / (double) columns);
                for (int i = 0; i < count; i++) {
                    int row = i / columns;
                    int column = i % columns;
                    offsets.add(new Vec2(
                            ((rows - 1) * 0.5f - row) * step,
                            (column - (columns - 1) * 0.5f) * step));
                }
            }
            case WEDGE -> {
                for (int i = 0; i < count; i++) {
                    int row = rowOf(i);
                    int column = i - slotsBeforeRow(row);
                    offsets.add(new Vec2(-row * step, (column - row * 0.5f) * step));
                }
            }
            case CIRCLE -> {
                float radius = circleRadius(count, step);
                for (int i = 0; i < count; i++) {
                    float angle = (float) (Math.PI * 2.0 * i / count);
                    offsets.add(new Vec2(
                            (float) Math.cos(angle) * radius,
                            (float) Math.sin(angle) * radius));
                }
            }
            default -> throw new IllegalStateException("unhandled shape " + shape);
        }
        if (facingRadians == 0f) {
            return List.copyOf(offsets);
        }
        return offsets.stream().map(offset -> offset.rotated(facingRadians)).toList();
    }

    /** Row of the wedge that holds the given slot index; row {@code r} holds {@code r + 1} slots. */
    private static int rowOf(int index) {
        int row = 0;
        while (index >= slotsBeforeRow(row + 1)) {
            row++;
        }
        return row;
    }

    /** Number of slots in the rows before the given one, i.e. the triangular number of {@code row}. */
    private static int slotsBeforeRow(int row) {
        return row * (row + 1) / 2;
    }

    /**
     * Radius that puts {@code count} slots evenly on a ring with the requested distance between
     * neighbours, never tighter than the spacing itself.
     */
    private static float circleRadius(int count, float spacing) {
        if (count <= 2) {
            return spacing;
        }
        float chordRadius = (float) (spacing / (2.0 * Math.sin(Math.PI / count)));
        return Math.max(spacing, chordRadius);
    }

    /** Living units in id order: the deterministic base order every later step starts from. */
    private static List<Unit> living(Collection<Unit> units) {
        List<Unit> ordered = new ArrayList<>();
        if (units != null) {
            for (Unit unit : units) {
                if (unit != null && unit.isAlive()) {
                    ordered.add(unit);
                }
            }
        }
        ordered.sort(Comparator.comparingLong(unit -> unit.id().value()));
        return ordered;
    }
}
