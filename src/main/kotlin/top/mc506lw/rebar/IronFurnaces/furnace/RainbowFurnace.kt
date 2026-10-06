package top.mc506lw.rebar.ironfurnaces.furnace

import io.github.pylonmc.rebar.block.context.BlockCreateContext
import io.github.pylonmc.rebar.recipe.vanilla.SmeltingRebarRecipe
import io.github.pylonmc.rebar.util.gui.GuiItems
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.block.Block
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import top.mc506lw.rebar.ironfurnaces.IronFurnaceKeys
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.max

class RainbowFurnace : AbstractIronFurnace {
    constructor(block: Block, context: BlockCreateContext) :
        super(block, context, FurnaceTier.RAINBOW, Material.WHITE_WOOL)

    constructor(block: Block, pdc: PersistentDataContainer) :
        super(block, pdc, FurnaceTier.RAINBOW, Material.WHITE_WOOL) {
        colorIndex = Math.floorMod(
            pdc.getOrDefault(IronFurnaceKeys.RAINBOW_COLOR_INDEX, PersistentDataType.INTEGER, 0),
            WOOL_COLORS.size
        )
        colorTickCounter = pdc.getOrDefault(
            IronFurnaceKeys.RAINBOW_COLOR_TICK,
            PersistentDataType.INTEGER,
            0
        ).coerceIn(0, COLOR_CHANGE_INTERVAL - 1)
    }

    companion object {
        private const val COLOR_CHANGE_INTERVAL = 10

        private val WOOL_COLORS = arrayOf(
            Material.WHITE_WOOL,
            Material.ORANGE_WOOL,
            Material.MAGENTA_WOOL,
            Material.LIGHT_BLUE_WOOL,
            Material.YELLOW_WOOL,
            Material.LIME_WOOL,
            Material.PINK_WOOL,
            Material.GRAY_WOOL,
            Material.LIGHT_GRAY_WOOL,
            Material.CYAN_WOOL,
            Material.PURPLE_WOOL,
            Material.BLUE_WOOL,
            Material.BROWN_WOOL,
            Material.GREEN_WOOL,
            Material.RED_WOOL,
            Material.BLACK_WOOL
        )

        private val DUST_OPTIONS = arrayOf(
            Color.WHITE,
            Color.ORANGE,
            Color.FUCHSIA,
            Color.AQUA,
            Color.YELLOW,
            Color.LIME,
            Color.fromRGB(255, 175, 175),
            Color.GRAY,
            Color.SILVER,
            Color.TEAL,
            Color.PURPLE,
            Color.BLUE,
            Color.MAROON,
            Color.GREEN,
            Color.RED,
            Color.BLACK
        ).map { Particle.DustOptions(it, 1.5f) }.toTypedArray()
    }

    override val rainbowSpeedLabel: String = "10x-640x"

    private var colorIndex = 0
    private var colorTickCounter = 0
    private val rainbowParticleLocation by lazy(LazyThreadSafetyMode.NONE) {
        block.location.toCenterLocation().add(0.0, 1.0, 0.0)
    }

    override fun write(pdc: PersistentDataContainer) {
        super.write(pdc)
        pdc.set(IronFurnaceKeys.RAINBOW_COLOR_INDEX, PersistentDataType.INTEGER, colorIndex)
        pdc.set(IronFurnaceKeys.RAINBOW_COLOR_TICK, PersistentDataType.INTEGER, colorTickCounter)
    }

    override fun setupBlockType() {
        block.type = WOOL_COLORS[colorIndex]
    }

    override fun tick() {
        super.tick()

        colorTickCounter++
        if (colorTickCounter >= COLOR_CHANGE_INTERVAL) {
            colorTickCounter = 0
            colorIndex = (colorIndex + 1) % WOOL_COLORS.size
            block.type = WOOL_COLORS[colorIndex]
        }
    }

    override fun spawnSmokeParticle() {
        block.world.spawnParticle(
            Particle.DUST,
            rainbowParticleLocation,
            ThreadLocalRandom.current().nextInt(3, 6),
            0.3,
            0.5,
            0.3,
            DUST_OPTIONS[colorIndex]
        )
    }

    override fun onRecipeFinished(recipe: SmeltingRebarRecipe) {
        val maximumByFuel = max(1, (fuelRemaining + tickInterval - 1) / tickInterval)
        val processed = processRecipeBatch(recipe, maximumByFuel)

        if (processed > 0) {
            fuelRemaining = (fuelRemaining - processed * tickInterval).coerceAtLeast(0)
        }

        tryStartSmelting()
        if (!isProcessingRecipe) recipeProgressItem.setItem(GuiItems.background())
    }
}
