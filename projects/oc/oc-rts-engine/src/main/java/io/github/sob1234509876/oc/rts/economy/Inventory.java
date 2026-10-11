package io.github.sob1234509876.oc.rts.economy;

import io.github.sob1234509876.oc.rts.content.ItemBundle;
import io.github.sob1234509876.oc.rts.content.ItemType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Storage of items inside one entity: the shelves of a warehouse, the input and output buffers of a
 * factory, or the backpack of a hauler.
 *
 * <p>Capacity has two independent limits, both of which may be {@link #UNLIMITED}:
 * <ul>
 *   <li>{@link #slotCount()} is how many different item kinds fit, one slot per kind, and</li>
 *   <li>{@link #unitLimit()} is how many item units fit in total, where a slot of one kind holds at
 *       most {@link ItemType#maxStack()} units.</li>
 * </ul>
 *
 * <p>A {@link Predicate} filter restricts which item kinds may enter, which is how a warehouse that
 * only accepts ore and a factory buffer that only accepts its recipe inputs are expressed.
 */
public final class Inventory {

    /** Value for {@link #slotCount()} or {@link #unitLimit()} meaning "no limit". */
    public static final int UNLIMITED = Integer.MAX_VALUE;

    private final String name;
    private final int slotCount;
    private final int unitLimit;
    private final Predicate<ItemType> filter;
    private final Map<ItemType, Integer> contents = new LinkedHashMap<>();

    public Inventory(String name, int slotCount, int unitLimit, Predicate<ItemType> filter) {
        this.name = name;
        this.slotCount = slotCount;
        this.unitLimit = unitLimit;
        this.filter = Objects.requireNonNull(filter, "filter");
        if (slotCount < 0) {
            throw new IllegalArgumentException("slot count must not be negative, was " + slotCount);
        }
        if (unitLimit < 0) {
            throw new IllegalArgumentException("unit limit must not be negative, was " + unitLimit);
        }
    }

    /**
     * An inventory that accepts nothing at all, as a null object for entities that store nothing.
     *
     * <p>Both limits are zero and the filter rejects every item, so {@link #insert(ItemType, int)}
     * always returns zero. It is deliberately <em>not</em> an unlimited store: for that, construct an
     * inventory with {@link #UNLIMITED} slot count and unit limit.
     */
    public static Inventory empty(String name) {
        return new Inventory(name, 0, 0, item -> false);
    }

    public String name() {
        return name;
    }

    public int slotCount() {
        return slotCount;
    }

    public int unitLimit() {
        return unitLimit;
    }

    /** Whether the filter of this inventory lets the given item in. */
    public boolean accepts(ItemType item) {
        return filter.test(item);
    }

    /**
     * Slots in use: one slot per {@link ItemType#maxStack()} units of a kind, so a kind holding more
     * than one stack occupies more than one slot.
     */
    public int usedSlots() {
        return usedSlots(contents);
    }

    public int freeSlots() {
        return freeSlots(contents);
    }

    public int totalUnits() {
        return totalUnits(contents);
    }

    public int amount(ItemType item) {
        return contents.getOrDefault(item, 0);
    }

    public boolean isEmpty() {
        return contents.isEmpty();
    }

    /** True when nothing more fits: either every slot is taken or the unit limit is reached. */
    public boolean isFull() {
        return freeSlots() == 0 || unitHeadroom(contents) == 0;
    }

    public ItemBundle contents() {
        return ItemBundle.of(contents);
    }

    /** How many units of the item could still be inserted right now. */
    public int capacityFor(ItemType item) {
        return insertable(item, UNLIMITED / 2);
    }

    /** How many of the offered units would be accepted right now. */
    public int insertable(ItemType item, int amount) {
        if (amount <= 0 || !accepts(item)) {
            return 0;
        }
        long capacity = Math.min(headroomFor(contents, item), unitHeadroom(contents));
        return (int) Math.min(capacity, amount);
    }

    /** Inserts as much of the item as fits and returns how much was accepted. */
    public int insert(ItemType item, int amount) {
        return insertInto(contents, item, amount);
    }

    /** Inserts as much of the bundle as fits and returns how much was accepted in total. */
    public int insert(ItemBundle bundle) {
        int accepted = 0;
        for (ItemBundle.Amount component : bundle.components()) {
            accepted += insert(component.item(), component.amount());
        }
        return accepted;
    }

    /** Whether the whole bundle fits, without changing the inventory. */
    public boolean canAcceptAll(ItemBundle bundle) {
        Map<ItemType, Integer> scratch = new LinkedHashMap<>(contents);
        for (ItemBundle.Amount component : bundle.components()) {
            int accepted = insertInto(scratch, component.item(), component.amount());
            if (accepted < component.amount()) {
                return false;
            }
        }
        return true;
    }

    /** Removes up to the given amount and returns how much was actually taken out. */
    public int extract(ItemType item, int amount) {
        int available = amount(item);
        int taken = Math.min(available, Math.max(0, amount));
        if (taken <= 0) {
            return 0;
        }
        int left = available - taken;
        if (left == 0) {
            contents.remove(item);
        } else {
            contents.put(item, left);
        }
        return taken;
    }

    /** Removes as much of the bundle as is available and returns what was taken. */
    public ItemBundle extractUpTo(ItemBundle bundle) {
        ItemBundle.Builder taken = ItemBundle.builder();
        for (ItemBundle.Amount component : bundle.components()) {
            int extracted = extract(component.item(), component.amount());
            if (extracted > 0) {
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
        return name + " [" + totalUnits() + " units in " + usedSlots() + "/"
                + (slotCount == UNLIMITED ? "unlimited" : slotCount) + " slots] " + contents();
    }

    @Override
    public String toString() {
        return describe();
    }

    /**
     * Inserts as much of the item as fits into the given state and returns how much was accepted.
     *
     * <p>The room for an item is the free space in its partially filled stack plus one full stack per
     * free slot, capped by the unit limit. Insert and {@link #insertable(ItemType, int)} share this rule
     * so a capacity query and the insert that follows it always agree.
     */
    private int insertInto(Map<ItemType, Integer> state, ItemType item, int amount) {
        if (amount <= 0 || !accepts(item)) {
            return 0;
        }
        int accepted = (int) Math.min(Math.min(headroomFor(state, item), unitHeadroom(state)), amount);
        if (accepted > 0) {
            state.merge(item, accepted, Integer::sum);
        }
        return accepted;
    }

    /**
     * Units of the item that still fit, counting the free space of its current stack and one full stack
     * for every free slot.
     */
    private int headroomFor(Map<ItemType, Integer> state, ItemType item) {
        int maxStack = item.maxStack();
        int existing = state.getOrDefault(item, 0);
        int headroom = freeSlots(state) == UNLIMITED
                ? Integer.MAX_VALUE / 2
                : freeSlots(state) * maxStack;
        int inOpenStack = existing % maxStack;
        if (inOpenStack > 0) {
            headroom += maxStack - inOpenStack;
        }
        return headroom;
    }

    private int usedSlots(Map<ItemType, Integer> state) {
        int used = 0;
        for (Map.Entry<ItemType, Integer> entry : state.entrySet()) {
            int maxStack = entry.getKey().maxStack();
            used += (entry.getValue() + maxStack - 1) / maxStack;
        }
        return used;
    }

    private int freeSlots(Map<ItemType, Integer> state) {
        return slotCount == UNLIMITED ? UNLIMITED : Math.max(0, slotCount - usedSlots(state));
    }

    private int unitHeadroom(Map<ItemType, Integer> state) {
        return unitLimit == UNLIMITED ? UNLIMITED : unitLimit - totalUnits(state);
    }

    private static int totalUnits(Map<ItemType, Integer> state) {
        int total = 0;
        for (int amount : state.values()) {
            total += amount;
        }
        return total;
    }
}
