package top.mc506lw.rebar.ironfurnaces.furnace

import java.util.Locale

/**
 * [smeltTimePerItem] is the tick count for a 200-tick recipe, exactly like the classic mod's
 * `<tier>_furnace.speed` config values. [generation] is that furnace's output as a generator,
 * taken 1:1 from the classic mod's `<tier>_furnace.generation` values (RF/t there, W here).
 * [slotTier] mirrors `<tier>_furnace.tier` and decides how many factory slots the furnace unlocks.
 */
enum class FurnaceTier(
    val smeltTimePerItem: Int,
    val generation: Double,
    val slotTier: Int
) {
    COPPER(180, 40.0, 0),
    IRON(160, 40.0, 0),
    GOLD(120, 160.0, 1),
    DIAMOND(80, 240.0, 2),
    EMERALD(40, 320.0, 2),
    CRYSTAL(40, 360.0, 2),
    OBSIDIAN(20, 500.0, 2),
    NETHERITE(5, 1000.0, 2),
    RAINBOW(20, 2000.0, 2);

    /**
     * How many of the six factory slots this furnace unlocks. The classic mod gates them with
     * `start = tier == 0 ? 2 : tier == 1 ? 1 : 0` and `size = tier == 0 ? 4 : tier == 1 ? 5 : 6`,
     * which works out to 2, 4 and 6 usable slots.
     */
    val factorySlots: Int = when (slotTier) {
        0 -> 2
        1 -> 4
        else -> 6
    }

    val speedMultiplier: Double = 200.0 / smeltTimePerItem

    val speedLabel: String = if (speedMultiplier == speedMultiplier.toInt().toDouble()) {
        "${speedMultiplier.toInt()}x"
    } else {
        "%.1fx".format(Locale.ROOT, speedMultiplier)
    }
}
