package io.github.sob1234509876.oc.rts.combat;

import io.github.sob1234509876.oc.googology.Natural;
import io.github.sob1234509876.oc.rts.content.UnitType;
import io.github.sob1234509876.oc.rts.entity.Unit;

import java.util.Objects;

/**
 * Immutable combat view of one unit: everything {@link CombatSystem} and the attack orders need, and
 * nothing that changes during a tick.
 *
 * <p>It is a projection of the unit's {@link UnitType}, not a second copy of the data: the damage,
 * range, cooldown and hit points are the catalogue's own values, and {@link UnitType#armour()} is the
 * flat damage reduction. A unit therefore has exactly one {@code CombatStats} instance for its whole
 * life, created once by {@link Unit} and returned by {@link Unit#combat()}, so the per tick combat loop
 * neither allocates nor can drift away from the catalogue.
 *
 * <p>Damage is exact natural number arithmetic: a hit deals {@code max(1, attackDamage - armour)}, so
 * the result is never zero and even an armoured target always loses at least one hit point per hit.
 * Range is a distance between unit centers, the same measure the
 * {@link io.github.sob1234509876.oc.rts.entity.SpatialIndex} uses, so a spatial query with
 * {@link #attackRange()} and the in-range test agree exactly.
 *
 * @param attackDamage damage per hit before the target's armour; {@code 0} means the unit cannot attack
 * @param attackRange reach in tiles, measured between unit centers; {@code 0} means the unit cannot attack
 * @param attackCooldownTicks ticks between two hits of the same attacker
 * @param armour flat damage reduction applied to every incoming hit
 * @param maxHealth hit points the unit spawned with
 */
public record CombatStats(
        Natural attackDamage,
        float attackRange,
        int attackCooldownTicks,
        int armour,
        Natural maxHealth) {

    public CombatStats {
        Objects.requireNonNull(attackDamage, "attackDamage");
        Objects.requireNonNull(maxHealth, "maxHealth");
        if (!(attackRange >= 0f)) {
            throw new IllegalArgumentException("attack range must not be negative, was " + attackRange);
        }
        if (attackCooldownTicks < 0) {
            throw new IllegalArgumentException("attack cooldown must not be negative, was " + attackCooldownTicks);
        }
        if (armour < 0) {
            throw new IllegalArgumentException("armour must not be negative, was " + armour);
        }
    }

    /** The combat values declared by a unit type. */
    public static CombatStats of(UnitType type) {
        Objects.requireNonNull(type, "type");
        return new CombatStats(type.attackDamage(), type.attackRange(), type.attackCooldownTicks(),
                type.armour(), type.maxHealth());
    }

    /** The combat values of a spawned unit, i.e. the projection it cached at spawn time. */
    public static CombatStats of(Unit unit) {
        return Objects.requireNonNull(unit, "unit").combat();
    }

    /** Whether this unit can fight at all: it needs positive damage and a positive range. */
    public boolean canAttack() {
        return !attackDamage.isZero() && attackRange > 0f;
    }

    /**
     * Damage one hit of this unit deals to a target with the given armour:
     * {@code max(1, attackDamage - armour)}.
     *
     * <p>The floor of one is deliberate: armour reduces a hit, it never makes a unit immune, and a hit
     * of zero damage would be a hit that published no event and changed nothing while still consuming a
     * cooldown.
     */
    public Natural damageAfterArmour(int targetArmour) {
        if (targetArmour < 0) {
            throw new IllegalArgumentException("armour must not be negative, was " + targetArmour);
        }
        return attackDamage.subtractClamped(Natural.of(targetArmour)).max(Natural.ONE);
    }

    /** Damage one hit of this unit deals to the given target. */
    public Natural damageAgainst(CombatStats target) {
        return damageAfterArmour(Objects.requireNonNull(target, "target").armour());
    }

    /** Whether a point at the given centre-to-centre distance is within this unit's reach. */
    public boolean reaches(float distance) {
        return distance <= attackRange;
    }
}
