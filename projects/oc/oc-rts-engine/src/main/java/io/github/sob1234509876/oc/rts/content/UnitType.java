package io.github.sob1234509876.oc.rts.content;

import io.github.sob1234509876.oc.googology.Natural;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Definition of one kind of unit: everything the simulation needs to spawn it, move it and fight with
 * it.
 *
 * <p>The combat values are data: {@link #attackDamage()}, {@link #attackRange()},
 * {@link #attackCooldownTicks()} and {@link #armour()} are read by
 * {@link io.github.sob1234509876.oc.rts.combat.CombatStats} and used by
 * {@link io.github.sob1234509876.oc.rts.combat.CombatSystem}. A unit without attack damage, or with a
 * range of zero, never attacks.
 *
 * <p>Armour is a flat damage reduction: an attack deals {@code max(1, damage - armour)}. It is a real
 * field and the source of truth; content may additionally publish a tag (the classic catalogue writes
 * {@code armour_<n>}) for display and content tooling, but no system reads the tag.
 */
public final class UnitType implements Comparable<UnitType> {

    private final ResourceKey key;
    private final String displayName;
    private final UnitRole role;
    private final Natural maxHealth;
    private final float speed;
    private final float radius;
    private final ItemBundle cost;
    private final int buildTimeTicks;
    private final Natural cargoCapacity;
    private final String spriteSetId;
    private final Set<String> tags;
    private final Natural attackDamage;
    private final float attackRange;
    private final int attackCooldownTicks;
    private final int armour;

    UnitType(
            ResourceKey key,
            String displayName,
            UnitRole role,
            Natural maxHealth,
            float speed,
            float radius,
            ItemBundle cost,
            int buildTimeTicks,
            Natural cargoCapacity,
            String spriteSetId,
            Set<String> tags,
            Natural attackDamage,
            float attackRange,
            int attackCooldownTicks,
            int armour) {
        this.key = Objects.requireNonNull(key, "key");
        this.displayName = displayName == null ? key.path() : displayName;
        this.role = Objects.requireNonNull(role, "role");
        this.maxHealth = maxHealth;
        this.speed = speed;
        this.radius = radius;
        this.cost = cost;
        this.buildTimeTicks = buildTimeTicks;
        this.cargoCapacity = cargoCapacity;
        this.spriteSetId = spriteSetId;
        this.tags = Collections.unmodifiableSet(new LinkedHashSet<>(tags));
        this.attackDamage = attackDamage;
        this.attackRange = attackRange;
        this.attackCooldownTicks = attackCooldownTicks;
        if (armour < 0) {
            throw new IllegalArgumentException("armour must not be negative, was " + armour);
        }
        this.armour = armour;
    }

    public ResourceKey key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public UnitRole role() {
        return role;
    }

    public Natural maxHealth() {
        return maxHealth;
    }

    /** Movement speed in tiles per second. */
    public float speed() {
        return speed;
    }

    /** Collision radius in tiles; used for separation, arrival tests and formation spacing. */
    public float radius() {
        return radius;
    }

    /** Items that have to be delivered to the producing building before it can start. */
    public ItemBundle cost() {
        return cost;
    }

    public int buildTimeTicks() {
        return buildTimeTicks;
    }

    /** How many item units this unit can carry; {@code 0} means it cannot haul. */
    public Natural cargoCapacity() {
        return cargoCapacity;
    }

    public boolean canHaul() {
        return !cargoCapacity.isZero();
    }

    /** Identifier of the animation set a renderer should use for this unit. */
    public String spriteSetId() {
        return spriteSetId;
    }

    public Set<String> tags() {
        return tags;
    }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    public Natural attackDamage() {
        return attackDamage;
    }

    public float attackRange() {
        return attackRange;
    }

    public int attackCooldownTicks() {
        return attackCooldownTicks;
    }

    /**
     * Flat damage reduction of this unit: an attacker deals {@code max(1, damage - armour)}.
     *
     * <p>{@code 0} when the content did not set it, which is the default for every unit of the sample
     * catalogue. This field is the source of truth; the classic catalogue additionally keeps an
     * {@code armour_<n>} tag for display, and no system reads that tag.
     */
    public int armour() {
        return armour;
    }

    public boolean canAttack() {
        return !attackDamage.isZero() && attackRange > 0f;
    }

    @Override
    public int compareTo(UnitType other) {
        return key.compareTo(other.key);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof UnitType unit && key.equals(unit.key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public String toString() {
        return key.toString();
    }
}
