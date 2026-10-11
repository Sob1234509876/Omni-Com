package io.github.sob1234509876.oc.rts.content;

import io.github.sob1234509876.oc.googology.Natural;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Definition of one kind of unit: everything the simulation needs to spawn it, move it and later
 * fight with it.
 *
 * <p>The attack values are part of the data model but no combat system reads them yet; they are
 * reserved for the combat layer so that scenarios do not have to be rewritten once it lands.
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
            int attackCooldownTicks) {
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
