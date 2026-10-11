package io.github.sob1234509876.oc.rts.content;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable, ordered multiset of items, used for recipe costs, recipe yields, inventory contents and
 * logistics queries. Amounts are always positive; items that would end up with a zero amount are
 * dropped so two bundles compare equal regardless of how they were built.
 */
public final class ItemBundle {

    public static final ItemBundle EMPTY = new ItemBundle(Map.of());

    private final Map<ItemType, Integer> amounts;

    private ItemBundle(Map<ItemType, Integer> amounts) {
        this.amounts = Collections.unmodifiableMap(amounts);
    }

    public static ItemBundle of(ItemType item, int amount) {
        return new Builder().add(item, amount).build();
    }

    /** Convenience for {@code of(itemA, 10, itemB, 25)}. */
    public static ItemBundle of(Object... itemAmountPairs) {
        if (itemAmountPairs.length % 2 != 0) {
            throw new IllegalArgumentException("expected alternating ItemType and amount arguments");
        }
        Builder builder = new Builder();
        for (int i = 0; i < itemAmountPairs.length; i += 2) {
            if (!(itemAmountPairs[i] instanceof ItemType item)) {
                throw new IllegalArgumentException("argument " + i + " must be an ItemType, was " + itemAmountPairs[i]);
            }
            if (!(itemAmountPairs[i + 1] instanceof Integer amount)) {
                throw new IllegalArgumentException("argument " + (i + 1) + " must be an Integer, was " + itemAmountPairs[i + 1]);
            }
            builder.add(item, amount);
        }
        return builder.build();
    }

    public static ItemBundle of(Map<ItemType, Integer> amounts) {
        return new Builder().addAll(amounts).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isEmpty() {
        return amounts.isEmpty();
    }

    /** Number of distinct item kinds. */
    public int size() {
        return amounts.size();
    }

    /** Sum of all amounts, i.e. the space this bundle occupies in an inventory. */
    public int totalUnits() {
        return amounts.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int amount(ItemType item) {
        return amounts.getOrDefault(item, 0);
    }

    public boolean contains(ItemType item) {
        return amounts.containsKey(item);
    }

    public boolean contains(ItemType item, int amount) {
        return amount(item) >= amount;
    }

    /** True when this bundle holds at least as much of every item as the other bundle needs. */
    public boolean containsAll(ItemBundle other) {
        for (Map.Entry<ItemType, Integer> entry : other.amounts.entrySet()) {
            if (amount(entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    public ItemBundle plus(ItemBundle other) {
        Builder builder = new Builder().addAll(amounts);
        return builder.addAll(other.amounts).build();
    }

    public ItemBundle plus(ItemType item, int amount) {
        return new Builder().addAll(amounts).add(item, amount).build();
    }

    /** Subtracts the other bundle, failing when this bundle does not hold enough of an item. */
    public ItemBundle minus(ItemBundle other) {
        if (!containsAll(other)) {
            throw new IllegalArgumentException("cannot subtract " + other + " from " + this);
        }
        Builder builder = new Builder();
        for (Map.Entry<ItemType, Integer> entry : amounts.entrySet()) {
            int remaining = entry.getValue() - other.amount(entry.getKey());
            if (remaining > 0) {
                builder.add(entry.getKey(), remaining);
            }
        }
        return builder.build();
    }

    /** Subtracts the other bundle and simply drops anything that is not available. */
    public ItemBundle minusClamped(ItemBundle other) {
        Builder builder = new Builder();
        for (Map.Entry<ItemType, Integer> entry : amounts.entrySet()) {
            int remaining = entry.getValue() - other.amount(entry.getKey());
            if (remaining > 0) {
                builder.add(entry.getKey(), remaining);
            }
        }
        return builder.build();
    }

    public ItemBundle multiplied(int factor) {
        if (factor < 0) {
            throw new IllegalArgumentException("factor must not be negative, was " + factor);
        }
        Builder builder = new Builder();
        amounts.forEach((item, amount) -> builder.add(item, amount * factor));
        return builder.build();
    }

    /** The items in insertion order. */
    public Set<ItemType> items() {
        return amounts.keySet();
    }

    public Map<ItemType, Integer> asMap() {
        return amounts;
    }

    public List<Amount> components() {
        return amounts.entrySet().stream().map(entry -> new Amount(entry.getKey(), entry.getValue())).toList();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ItemBundle bundle && amounts.equals(bundle.amounts);
    }

    @Override
    public int hashCode() {
        return amounts.hashCode();
    }

    @Override
    public String toString() {
        if (amounts.isEmpty()) {
            return "nothing";
        }
        StringBuilder text = new StringBuilder();
        amounts.forEach((item, amount) -> {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(amount).append("x ").append(item.key());
        });
        return text.toString();
    }

    /** One item kind with an amount. */
    public record Amount(ItemType item, int amount) {

        @Override
        public String toString() {
            return amount + "x " + item.key();
        }
    }

    /** Mutable helper that sums amounts per item kind. */
    public static final class Builder {

        private final Map<ItemType, Integer> amounts = new LinkedHashMap<>();

        /** Creates an empty builder. */
        public Builder() {
        }

        public Builder add(ItemType item, int amount) {
            Objects.requireNonNull(item, "item");
            if (amount < 0) {
                throw new IllegalArgumentException("amount must not be negative, was " + amount);
            }
            if (amount > 0) {
                amounts.merge(item, amount, Integer::sum);
            }
            return this;
        }

        public Builder add(ItemType item) {
            return add(item, 1);
        }

        public Builder addAll(Map<ItemType, Integer> other) {
            other.forEach(this::add);
            return this;
        }

        public Builder addAll(ItemBundle other) {
            return addAll(other.amounts);
        }

        public boolean isEmpty() {
            return amounts.isEmpty();
        }

        public ItemBundle build() {
            return amounts.isEmpty() ? EMPTY : new ItemBundle(new LinkedHashMap<>(amounts));
        }
    }
}
