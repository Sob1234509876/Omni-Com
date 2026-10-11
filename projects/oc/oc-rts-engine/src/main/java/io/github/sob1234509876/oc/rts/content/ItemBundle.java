package io.github.sob1234509876.oc.rts.content;

import io.github.sob1234509876.oc.googology.Natural;
import io.github.sob1234509876.oc.googology.NaturalFormat;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable, ordered multiset of items, used for recipe costs, recipe yields, inventory contents and
 * logistics queries. Amounts are {@link Natural} values, so a bundle can describe an arbitrarily large
 * stockpile: ordinary amounts are exact integers, and amounts past the googology threshold keep their
 * magnitude without being written out digit by digit.
 *
 * <p>Amounts are always positive; items that would end up with a zero amount are dropped so two bundles
 * compare equal regardless of how they were built. The {@code long} overloads exist so call sites can
 * keep writing {@code of(item, 50)}.
 */
public final class ItemBundle {

    public static final ItemBundle EMPTY = new ItemBundle(Map.of());

    private final Map<ItemType, Natural> amounts;

    private ItemBundle(Map<ItemType, Natural> amounts) {
        this.amounts = Collections.unmodifiableMap(amounts);
    }

    public static ItemBundle of(ItemType item, Natural amount) {
        return new Builder().add(item, amount).build();
    }

    public static ItemBundle of(ItemType item, long amount) {
        return new Builder().add(item, amount).build();
    }

    /** Convenience for {@code of(itemA, 10, itemB, 25)}; amounts may be a {@link Natural} or a number. */
    public static ItemBundle of(Object... itemAmountPairs) {
        if (itemAmountPairs.length % 2 != 0) {
            throw new IllegalArgumentException("expected alternating ItemType and amount arguments");
        }
        Builder builder = new Builder();
        for (int i = 0; i < itemAmountPairs.length; i += 2) {
            if (!(itemAmountPairs[i] instanceof ItemType item)) {
                throw new IllegalArgumentException("argument " + i + " must be an ItemType, was " + itemAmountPairs[i]);
            }
            builder.add(item, amountOf(itemAmountPairs[i + 1], i + 1));
        }
        return builder.build();
    }

    public static ItemBundle of(Map<ItemType, Natural> amounts) {
        return new Builder().addAll(amounts).build();
    }

    private static Natural amountOf(Object value, int position) {
        if (value instanceof Natural natural) {
            return natural;
        }
        if (value instanceof Number number) {
            return Natural.of(number.longValue());
        }
        throw new IllegalArgumentException("argument " + position + " must be a Natural or a number, was " + value);
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
    public Natural totalUnits() {
        Natural total = Natural.ZERO;
        for (Natural amount : amounts.values()) {
            total = total.add(amount);
        }
        return total;
    }

    public Natural amount(ItemType item) {
        return amounts.getOrDefault(item, Natural.ZERO);
    }

    public boolean contains(ItemType item) {
        return amounts.containsKey(item);
    }

    public boolean contains(ItemType item, Natural amount) {
        return amount(item).compareTo(amount) >= 0;
    }

    public boolean contains(ItemType item, long amount) {
        return contains(item, Natural.of(amount));
    }

    /** True when this bundle holds at least as much of every item as the other bundle needs. */
    public boolean containsAll(ItemBundle other) {
        for (Map.Entry<ItemType, Natural> entry : other.amounts.entrySet()) {
            if (amount(entry.getKey()).compareTo(entry.getValue()) < 0) {
                return false;
            }
        }
        return true;
    }

    public ItemBundle plus(ItemBundle other) {
        Builder builder = new Builder().addAll(amounts);
        return builder.addAll(other.amounts).build();
    }

    public ItemBundle plus(ItemType item, Natural amount) {
        return new Builder().addAll(amounts).add(item, amount).build();
    }

    public ItemBundle plus(ItemType item, long amount) {
        return plus(item, Natural.of(amount));
    }

    /** Subtracts the other bundle, failing when this bundle does not hold enough of an item. */
    public ItemBundle minus(ItemBundle other) {
        if (!containsAll(other)) {
            throw new IllegalArgumentException("cannot subtract " + other + " from " + this);
        }
        Builder builder = new Builder();
        for (Map.Entry<ItemType, Natural> entry : amounts.entrySet()) {
            Natural remaining = entry.getValue().subtractClamped(other.amount(entry.getKey()));
            if (!remaining.isZero()) {
                builder.add(entry.getKey(), remaining);
            }
        }
        return builder.build();
    }

    /** Subtracts the other bundle and simply drops anything that is not available. */
    public ItemBundle minusClamped(ItemBundle other) {
        Builder builder = new Builder();
        for (Map.Entry<ItemType, Natural> entry : amounts.entrySet()) {
            Natural remaining = entry.getValue().subtractClamped(other.amount(entry.getKey()));
            if (!remaining.isZero()) {
                builder.add(entry.getKey(), remaining);
            }
        }
        return builder.build();
    }

    public ItemBundle multiplied(Natural factor) {
        Builder builder = new Builder();
        amounts.forEach((item, amount) -> builder.add(item, amount.multiply(factor)));
        return builder.build();
    }

    public ItemBundle multiplied(long factor) {
        return multiplied(Natural.of(factor));
    }

    /** The items in insertion order. */
    public Set<ItemType> items() {
        return amounts.keySet();
    }

    public Map<ItemType, Natural> asMap() {
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

    /** Readable form: small amounts print exactly, huge ones switch to a scientific magnitude. */
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
            text.append(NaturalFormat.scientific(amount)).append("x ").append(item.key());
        });
        return text.toString();
    }

    /** One item kind with an amount. */
    public record Amount(ItemType item, Natural amount) {

        @Override
        public String toString() {
            return NaturalFormat.scientific(amount) + "x " + item.key();
        }
    }

    /** Mutable helper that sums amounts per item kind. */
    public static final class Builder {

        private final Map<ItemType, Natural> amounts = new LinkedHashMap<>();

        /** Creates an empty builder. */
        public Builder() {
        }

        public Builder add(ItemType item, Natural amount) {
            Objects.requireNonNull(item, "item");
            Objects.requireNonNull(amount, "amount");
            if (amount.isBeyond()) {
                throw new IllegalArgumentException("a bundle cannot hold BEYOND of " + item.key());
            }
            if (!amount.isZero()) {
                amounts.merge(item, amount, Natural::add);
            }
            return this;
        }

        public Builder add(ItemType item, long amount) {
            return add(item, Natural.of(amount));
        }

        public Builder add(ItemType item) {
            return add(item, Natural.ONE);
        }

        public Builder addAll(Map<ItemType, Natural> other) {
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
