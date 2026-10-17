package io.github.sob1234509876.oc.rts.content;

/**
 * Content catalogue of the engine's sample scenario: terrain kinds, a deliberately large set of items and
 * resources, units, buildings and recipes.
 *
 * <p>It exists to prove that the engine itself never names an item: everything below is data handed to
 * the engine through a {@link ContentRegistry}, and the same engine would run an entirely different
 * game with another catalogue. It is also the default catalogue of the server's world host (wrapped by
 * {@link ScenarioWorldContent}), so a host that was given no content of its own still serves a world.
 *
 * <p>The catalogue was originally part of the self check source set. It lives on the main source path
 * now because it is content a host may serve, not an assertion: production code must never be compiled
 * out of a test source set. Behaviour is unchanged - {@link #omniCom()} returns exactly the registry it
 * always did, and {@link #populate(ContentRegistry.Builder)} is the same chain minus the final
 * {@code build()}, so other content can compose it into a catalogue of its own.
 */
public final class ScenarioContent {

    /** Namespace of every key of this catalogue. */
    public static final String NAMESPACE = "oc";

    private ScenarioContent() {
    }

    /** Item paths used by the scenario, so typos fail in one obvious place. */
    public static final String IRON_ORE = "iron_ore";
    public static final String IRON_INGOT = "iron_ingot";
    public static final String STEEL_INGOT = "steel_ingot";
    public static final String COAL = "coal";
    public static final String WOOD = "wood";
    public static final String PLANK = "plank";
    public static final String STONE = "stone";
    public static final String BRICK = "brick";
    public static final String GEAR = "gear";
    public static final String RIFLE = "rifle";
    public static final String BREAD = "bread";
    public static final String FLOUR = "flour";
    public static final String WHEAT = "wheat";

    public static final String SOLDIER = "soldier";
    public static final String WORKER = "worker";
    public static final String MEDIC = "medic";

    public static final String WAREHOUSE = "warehouse";
    public static final String ORE_SILO = "ore_silo";
    public static final String BARRACKS = "barracks";
    public static final String WORKSHOP = "workshop";

    public static final String TRAIN_SOLDIER = "train_soldier";
    public static final String TRAIN_WORKER = "train_worker";
    public static final String SMELT_STEEL = "smelt_steel";
    public static final String CRAFT_RIFLE = "craft_rifle";
    public static final String BAKE_BREAD = "bake_bread";

    /** Builds the catalogue. */
    public static ContentRegistry omniCom() {
        ContentRegistry.Builder registry = ContentRegistry.builder(NAMESPACE);
        populate(registry);
        return registry.build();
    }

    /**
     * Registers the whole catalogue into a builder the caller owns.
     *
     * <p>This is the registration API of the engine: a catalogue is written as a fluent chain on
     * {@link ContentRegistry.Builder} and becomes an immutable {@link ContentRegistry} when
     * {@link ContentRegistry.Builder#build()} runs. Content that wants to be composable - a plugin's
     * catalogue, a scenario that starts from this one - calls this method instead of {@link #omniCom()}.
     *
     * @param registry the builder to add to, in the namespace it was created with
     */
    public static void populate(ContentRegistry.Builder registry) {
        registry
                // ------------------------------------------------------------------ terrain
                .terrain("water").name("Water").impassable().moveCost(1f).sprite("terrain/water").mapColor(0x2F5DA8)
                .terrain("sand").name("Sand").buildable().moveCost(1.2f).sprite("terrain/sand").mapColor(0xD8C88A)
                .terrain("grass").name("Grass").buildable().moveCost(1f).sprite("terrain/grass").mapColor(0x5FA84F)
                .terrain("forest").name("Forest").moveCost(1.8f).sprite("terrain/forest").mapColor(0x2E6B32)
                .terrain("hill").name("Hills").moveCost(2.2f).sprite("terrain/hill").mapColor(0x8A7A5C)
                .terrain("rock").name("Rock").impassable().sprite("terrain/rock").mapColor(0x5A5A5A)

                // ------------------------------------------------------------------ raw resources
                .item(IRON_ORE).name("Iron Ore").maxStack(100).tag("raw").tag("ore").tag("metal")
                .item(COAL).name("Coal").maxStack(100).tag("raw").tag("ore").tag("fuel")
                .item(WOOD).name("Wood").maxStack(100).tag("raw").tag("organic")
                .item(STONE).name("Stone").maxStack(100).tag("raw").tag("mineral")
                .item(WHEAT).name("Wheat").maxStack(100).tag("raw").tag("organic").tag("food")
                .item("copper_ore").name("Copper Ore").maxStack(100).tag("raw").tag("ore").tag("metal")
                .item("sand_pile").name("Sand Pile").maxStack(100).tag("raw").tag("mineral")
                .item("clay").name("Clay").maxStack(100).tag("raw").tag("mineral")
                .item("oil").name("Oil").maxStack(100).tag("raw").tag("fuel")
                .item("cotton").name("Cotton").maxStack(100).tag("raw").tag("organic")
                .item("hide").name("Hide").maxStack(100).tag("raw").tag("organic")
                .item("sulfur").name("Sulfur").maxStack(100).tag("raw").tag("mineral")

                // ------------------------------------------------------------------ refined goods
                .item(IRON_INGOT).name("Iron Ingot").maxStack(60).tag("refined").tag("metal")
                .item(STEEL_INGOT).name("Steel Ingot").maxStack(60).tag("refined").tag("metal")
                .item("copper_ingot").name("Copper Ingot").maxStack(60).tag("refined").tag("metal")
                .item(PLANK).name("Plank").maxStack(80).tag("refined").tag("material")
                .item(BRICK).name("Brick").maxStack(80).tag("refined").tag("material")
                .item("glass").name("Glass").maxStack(80).tag("refined").tag("material")
                .item("cloth").name("Cloth").maxStack(80).tag("refined").tag("material")
                .item("leather").name("Leather").maxStack(80).tag("refined").tag("material")
                .item("plastic").name("Plastic").maxStack(80).tag("refined").tag("material")
                .item("gunpowder").name("Gunpowder").maxStack(60).tag("refined").tag("military")
                .item(FLOUR).name("Flour").maxStack(80).tag("refined").tag("food")
                .item("rope").name("Rope").maxStack(80).tag("refined").tag("material")
                .item("paper").name("Paper").maxStack(80).tag("refined").tag("material")

                // ------------------------------------------------------------------ components
                .item(GEAR).name("Gear").maxStack(40).tag("component").tag("mechanical")
                .item("pipe").name("Pipe").maxStack(40).tag("component").tag("mechanical")
                .item("circuit").name("Circuit").maxStack(40).tag("component").tag("electronic")
                .item("engine").name("Engine").maxStack(20).tag("component").tag("mechanical")
                .item("wheel").name("Wheel").maxStack(40).tag("component").tag("mechanical")
                .item("barrel").name("Barrel").maxStack(40).tag("component")
                .item("wire").name("Wire").maxStack(60).tag("component").tag("electronic")
                .item("nail").name("Nail").maxStack(100).tag("component")
                .item("tool").name("Tool").maxStack(30).tag("component")

                // ------------------------------------------------------------------ finished goods
                .item(RIFLE).name("Rifle").maxStack(20).tag("good").tag("military")
                .item("machine_gun").name("Machine Gun").maxStack(10).tag("good").tag("military")
                .item("rifle_ammo").name("Rifle Ammo").maxStack(100).tag("good").tag("military")
                .item("artillery_shell").name("Artillery Shell").maxStack(40).tag("good").tag("military")
                .item("armor_plate").name("Armor Plate").maxStack(20).tag("good").tag("military")
                .item("helmet").name("Helmet").maxStack(40).tag("good").tag("military")
                .item("uniform").name("Uniform").maxStack(40).tag("good")
                .item("medkit").name("Medkit").maxStack(40).tag("good").tag("medical")
                .item("fuel_can").name("Fuel Can").maxStack(40).tag("good").tag("fuel")
                .item(BREAD).name("Bread").maxStack(60).tag("good").tag("food")
                .item("gold_coin").name("Gold Coin").maxStack(1000).tag("good").tag("currency")
                .item("gem").name("Gem").maxStack(20).tag("good").tag("luxury")

                // ------------------------------------------------------------------ units
                .unit(SOLDIER).name("Soldier").role(UnitRole.COMBAT).health(120).speed(2.4f).radius(0.35f)
                .cost(IRON_INGOT, 50).cost(PLANK, 20).buildTime(120).spriteSet("soldier")
                .attack(12f, 4.5f).attackCooldown(20)
                .unit(WORKER).name("Worker").role(UnitRole.WORKER).health(80).speed(2.8f).radius(0.32f)
                .cost(IRON_INGOT, 30).buildTime(90).cargo(120).spriteSet("worker")
                .unit(MEDIC).name("Medic").role(UnitRole.SUPPORT).health(90).speed(2.6f).radius(0.33f)
                .cost(IRON_INGOT, 40).cost("cloth", 20).buildTime(110).spriteSet("medic").cargo(20)

                // ------------------------------------------------------------------ buildings
                .building(WAREHOUSE).name("Warehouse").size(3, 3).health(600).cost(PLANK, 60).buildTime(200)
                .storage(24).warehouse().spriteSet("warehouse")
                .building(ORE_SILO).name("Ore Silo").size(2, 2).health(400).cost(PLANK, 30).buildTime(150)
                .storage(12).storageTag("ore").warehouse().spriteSet("ore_silo")
                .building(BARRACKS).name("Barracks").size(3, 3).health(800).cost(IRON_INGOT, 100).cost(PLANK, 50)
                .buildTime(300).storage(6).producer("barracks").spriteSet("barracks")
                .building(WORKSHOP).name("Workshop").size(4, 3).health(700).cost(PLANK, 80).cost(BRICK, 40)
                .buildTime(260).storage(8).producer("workshop").spriteSet("workshop")

                // ------------------------------------------------------------------ recipes
                .recipe(TRAIN_SOLDIER).name("Train Soldier").producer("barracks")
                .input(IRON_INGOT, 50).input(PLANK, 20).unitOutput(SOLDIER).duration(120)
                .recipe(TRAIN_WORKER).name("Train Worker").producer("barracks")
                .input(IRON_INGOT, 30).unitOutput(WORKER).duration(90)
                .recipe(SMELT_STEEL).name("Smelt Steel").producer("workshop")
                .input(IRON_INGOT, 20).input(COAL, 10).itemOutput(STEEL_INGOT, 10).duration(100)
                .recipe(CRAFT_RIFLE).name("Craft Rifle").producer("workshop")
                .input(STEEL_INGOT, 10).input(PLANK, 5).itemOutput(RIFLE, 1).duration(80)
                .recipe(BAKE_BREAD).name("Bake Bread").producer("workshop")
                .input(FLOUR, 10).itemOutput(BREAD, 5).duration(60);
    }
}
