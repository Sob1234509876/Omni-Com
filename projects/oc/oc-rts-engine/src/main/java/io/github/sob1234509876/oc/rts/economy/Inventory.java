package io.github.sob1234509876.oc.rts.economy;

import io.github.sob1234509876.oc.googology.Natural;
import io.github.sob1234509876.oc.googology.NaturalFormat;
import io.github.sob1234509876.oc.rts.content.ItemBundle;
import io.github.sob1234509876.oc.rts.content.ItemType;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Storage of items inside one entity: the shelves of a warehouse, the input and output buffers of a
 * factory, or the backpack of a hauler.
 *
 * <p>All magnitudes are {@link Natural} values, so a stockpile can grow past what an {@code int} or a
 * {@code long} could hold. Ordinary quantities behave exactly like plain integers; once an amount passes
 * the googology exact threshold it keeps its order of magnitude instead of its digits, and the slot
 * accounting of such an amount reports {@link Natural#BEYOND} rather than pretending to count slots.
 *
 * <p>Capacity has two independent limits, both of which may be {@link #UNLIMITED}:
 * <ul>
 *   <li>{@link #slotCount()} is how many slots fit, where one slot holds at most
 *       {@link ItemType#maxStack()} units of a single kind, so a kind that needs more than one stack
 *       occupies more than one slot, and</li>
 *   <li>{@link #unitLimit()} is how many item units fit in total.</li>
 * </ul>
 *
 * <p>A {@link Predicate} filter restricts which item kinds may enter, which is how a warehouse that only
 * accepts ore and a factory buffer that only accepts its recipe inputs are expressed.
 */
public final class Inventory {

    /** Value for {@link #slotCount()} or {@link #unitLimit()} meaning "no limit". */
    public static final Natural UNLIMITED = Natural.BEYOND;

    private final String name;
    private final Natural slotCount;
    private final Natural unitLimit;
    private final Predicate<ItemType> filter;
    private final Map<ItemType, Natural> contents = new LinkedHashMap<>();

    public Inventory(String name, Natural slotCount, Natural unitLimit, Predicate<ItemType> filter) {
        this.name = name;
        this.slotCount = Objects.requireNonNull(slotCount, "slotCount");
        this.unitLimit = Objects.requireNonNull(unitLimit, "unitLimit");
        this.filter = Objects.requireNonNull(filter, "filter");
    }

    /** Convenience constructor for limits that fit a long. */
    public Inventory(String name, long slotCount, long unitLimit, Predicate<ItemType> filter) {
        this(name, Natural.of(slotCount), Natural.of(unitLimit), filter);
    }

    /**
     * An inventory that accepts nothing at all, as a null object for entities that store nothing.
     *
     * <p>Both limits are zero and the filter rejects every item, so {@link #insert(ItemType, Natural)}
     * always returns zero. It is deliberately <em>not</em> an unlimited store: for that, construct an
     * inventory with {@link #UNLIMITED} slot count and unit limit.
     */
    public static Inventory empty(String name) {
        return new Inventory(name, Natural.ZERO, Natural.ZERO, item -> false);
    }

    public String name() {
        return name;
    }

    public Natural slotCount() {
        return slotCount;
    }

    public Natural unitLimit() {
        return unitLimit;
    }

    /** Whether the filter of this inventory lets the given item in. */
    public boolean accepts(ItemType item) {
        return filter.test(item);
    }

    /**
     * Slots in use: one slot per {@link ItemType#maxStack()} units of a kind, so a kind holding more than
     * one stack occupies more than one slot. Reports {@link Natural#BEYOND} when an amount is so large
     * that even the slot count cannot be stated exactly.
     */
    public Natural usedSlots() {
        return usedSlots(contents);
    }

    public Natural freeSlots() {
        return freeSlots(contents);
    }

    public Natural totalUnits() {
        return totalUnits(contents);
    }

    public Natural amount(ItemType item) {
        return contents.getOrDefault(item, Natural.ZERO);
    }

    public boolean isEmpty() {
        return contents.isEmpty();
    }

    /** True when nothing more fits: either every slot is taken or the unit limit is reached. */
    public boolean isFull() {
        return freeSlots().isZero() || unitHeadroom(contents).isZero();
    }

    public ItemBundle contents() {
        return ItemBundle.of(contents);
    }

    /** How many units of the item could still be inserted right now. */
    public Natural capacityFor(ItemType item) {
        return insertable(item, Natural.BEYOND);
    }

    /** How many of the offered units would be accepted right now. */
    public Natural insertable(ItemType item, Natural amount) {
        if (amount.isZero() || !accepts(item)) {
            return Natural.ZERO;
        }
        return headroomFor(contents, item).min(unitHeadroom(contents)).min(amount);
    }

    /** Convenience for {@link #insertable(ItemType, Natural)} with a small literal. */
    public Natural insertable(ItemType item, long amount) {
        return insertable(item, Natural.of(amount));
    }

    /** Inserts as much of the item as fits and returns how much was accepted. */
    public Natural insert(ItemType item, Natural amount) {
        return insertInto(contents, item, amount);
    }

    /** Convenience for {@link #insert(ItemType, Natural)} with a small literal. */
    public Natural insert(ItemType item, long amount) {
        return insert(item, Natural.of(amount));
    }

    /** Inserts as much of the bundle as fits and returns how much was accepted in total. */
    public Natural insert(ItemBundle bundle) {
        Natural accepted = Natural.ZERO;
        for (ItemBundle.Amount component : bundle.components()) {
            accepted = accepted.add(insert(component.item(), component.amount()));
        }
        return accepted;
    }

    /** Whether the whole bundle fits, without changing the inventory. */
    public boolean canAcceptAll(ItemBundle bundle) {
        Map<ItemType, Natural> scratch = new LinkedHashMap<>(contents);
        for (ItemBundle.Amount component : bundle.components()) {
            Natural accepted = insertInto(scratch, component.item(), component.amount());
            if (accepted.compareTo(component.amount()) < 0) {
                return false;
            }
        }
        return true;
    }

    /** Removes up to the given amount and returns how much was actually taken out. */
    public Natural extract(ItemType item, Natural amount) {
        Natural available = amount(item);
        Natural taken = available.min(amount);
        if (taken.isZero()) {
            return Natural.ZERO;
        }
        Natural left = available.subtractClamped(taken);
        if (left.isZero()) {
            contents.remove(item);
        } else {
            contents.put(item, left);
        }
        return taken;
    }

    /** Convenience for {@link #extract(ItemType, Natural)} with a small literal. */
    public Natural extract(ItemType item, long amount) {
        return extract(item, Natural.of(amount));
    }

    /** Removes as much of the bundle as is available and returns what was taken. */
    public ItemBundle extractUpTo(ItemBundle bundle) {
        ItemBundle.Builder taken = ItemBundle.builder();
        for (ItemBundle.Amount component : bundle.components()) {
            Natural extracted = extract(component.item(), component.amount());
            if (!extracted.isZero()) {
                taken.add(component.item(), extracted);
            }
        }
        return taken.build();
    }

    /** Empties the inventory and returns what was inside. */
    public ItemBundle extractAll() {
        ItemBundle all = contents();
        contents.clear();
        return all;
    }

    public String describe() {
        return name + " [" + NaturalFormat.scientific(totalUnits()) + " units in "
                + NaturalFormat.scientific(usedSlots()) + "/"
                + (slotCount.isBeyond() ? "unlimited" : NaturalFormat.scientific(slotCount)) + " slots] " + contents();
    }

    @Override
    public String toString() {
        return describe();
    }

    /**
     * Inserts as much of the item as fits into the given state and returns how much was accepted.
     *
     * <p>The room for an item is the free space in its partially filled stack plus one full stack per
     * free slot, capped by the unit limit. Insert and {@link #insertable(ItemType, Natural)} share this
     * rule so a capacity query and the insert that follows it always agree.
     */
    private Natural insertInto(Map<ItemType, Natural> state, ItemType item, Natural amount) {
        if (amount.isBeyond()) {
            throw new IllegalArgumentException("an inventory cannot hold BEYOND " + item.key()
                    + "; insert a finite amount instead");
        }
        if (amount.isZero() || !accepts(item)) {
            return Natural.ZERO;
        }
        Natural accepted = headroomFor(state, item).min(unitHeadroom(state)).min(amount);
        if (!accepted.isZero()) {
            state.merge(item, accepted, Natural::add);
        }
        return accepted;
    }

    /**
     * Units of the item that still fit, counting the free space of its current stack and one full stack
     * for every free slot.
     */
    private Natural headroomFor(Map<ItemType, Natural> state, ItemType item) {
        Natural maxStack = item.maxStack();
        Natural free = freeSlots(state);
        Natural headroom = free.isBeyond() ? Natural.BEYOND : free.multiply(maxStack);
        return headroom.add(openStackHeadroom(state.getOrDefault(item, Natural.ZERO), maxStack));
    }

    /**
     * Room left in the stack the item currently fills. Amounts beyond the exact range are treated as
     * having no room left, because at that resolution the remainder is meaningless.
     */
    private static Natural openStackHeadroom(Natural existing, Natural maxStack) {
        if (existing.isZero() || !existing.isExact() || !maxStack.isExact()) {
            return Natural.ZERO;
        }
        BigInteger remainder = existing.toBigInteger().mod(maxStack.toBigInteger());
        if (remainder.signum() == 0) {
            return Natural.ZERO;
        }
        return maxStack.subtractClamped(Natural.of(remainder));
    }

    private static Natural usedSlots(Map<ItemType, Natural> state) {
        Natural used = Natural.ZERO;
        for (Map.Entry<ItemType, Natural> entry : state.entrySet()) {
            Natural amount = entry.getValue();
            Natural maxStack = entry.getKey().maxStack();
            if (!amount.isExact() || !maxStack.isExact() || maxStack.isZero()) {
                return Natural.BEYOND;
            }
            BigInteger stackSize = maxStack.toBigInteger();
            BigInteger slots = amount.toBigInteger().add(stackSize).subtract(BigInteger.ONE).divide(stackSize);
            used = used.add(Natural.of(slots));
        }
        return used;
    }

    private Natural freeSlots(Map<ItemType, Natural> state) {
        if (slotCount.isBeyond()) {
            return Natural.BEYOND;
        }
        return slotCount.subtractClamped(usedSlots(state));
    }

    private Natural unitHeadroom(Map<ItemType, Natural> state) {
        if (unitLimit.isBeyond()) {
            return Natural.BEYOND;
        }
        return unitLimit.subtractClamped(totalUnits(state));
    }

    private static Natural totalUnits(Map<ItemType, Natural> state) {
        Natural total = Natural.ZERO;
        for (Natural amount : state.values()) {
            total = total.add(amount);
        }
        return total;
    }
}
