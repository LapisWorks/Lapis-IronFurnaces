package top.mc506lw.rebar.ironfurnaces

import org.bukkit.NamespacedKey

object IronFurnaceKeys {
    private const val NAMESPACE = "ironfurnaces"

    fun key(name: String) = NamespacedKey(NAMESPACE, name)

    val RAINBOW_CORE = key("rainbow_core")
    val RAINBOW_SHELL = key("rainbow_shell")
    val RAINBOW_COAL = key("rainbow_coal")
    val RAINBOW_CONNECTOR = key("rainbow_connector")

    val COPPER_FURNACE = key("copper_furnace")
    val IRON_FURNACE = key("iron_furnace")
    val GOLD_FURNACE = key("gold_furnace")
    val DIAMOND_FURNACE = key("diamond_furnace")
    val EMERALD_FURNACE = key("emerald_furnace")
    val CRYSTAL_FURNACE = key("crystal_furnace")
    val OBSIDIAN_FURNACE = key("obsidian_furnace")
    val NETHERITE_FURNACE = key("netherite_furnace")
    val RAINBOW_FURNACE = key("rainbow_furnace")

    val BLAST_UPGRADE = key("blast_upgrade")
    val SMOKER_UPGRADE = key("smoker_upgrade")
    val SPEED_UPGRADE = key("speed_upgrade")
    val FUEL_UPGRADE = key("fuel_upgrade")
    val INDUSTRIAL_UPGRADE = key("industrial_upgrade")
    val GENERATOR_UPGRADE = key("generator_upgrade")

    val FUEL_TIME = key("fuel_time")
    val FUEL_REMAINING = key("fuel_remaining")
    val FUEL_TYPE = key("fuel_type")
    val FUEL_CONSUMPTION_FRACTION = key("fuel_consumption_fraction")
    val ENERGY_CURRENT = key("energy_current")
    val ENERGY_TOTAL_PRODUCED = key("energy_total_produced")
    val RAINBOW_COLOR_INDEX = key("rainbow_color_index")
    val RAINBOW_COLOR_TICK = key("rainbow_color_tick")

    /** Per-slot smelting progress of the factory slots, mirroring the mod's FactoryCookTime arrays. */
    val TASK_TICKS_TOTAL = key("task_ticks_total")
    val TASK_TICKS_REMAINING = key("task_ticks_remaining")

    /** The classic mod's redstone setting (ignored / stop on signal / run only on signal). */
    val REDSTONE_MODE = key("redstone_mode")
}
