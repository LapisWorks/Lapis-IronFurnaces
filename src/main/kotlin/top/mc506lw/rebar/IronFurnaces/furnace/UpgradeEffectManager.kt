package top.mc506lw.rebar.ironfurnaces.furnace

import io.github.pylonmc.rebar.item.RebarItemSchema
import org.bukkit.inventory.ItemStack
import top.mc506lw.rebar.ironfurnaces.upgrades.UpgradeType
import xyz.xenondevs.invui.inventory.VirtualInventory

enum class FurnaceMode {
    NORMAL,
    BLAST,
    SMOKER,
    GENERATOR_ONLY,
    INDUSTRIAL,
    GENERATOR_BLAST,
    GENERATOR_SMOKER
}

data class UpgradeEffects(
    val mode: FurnaceMode = FurnaceMode.NORMAL,
    val speedMultiplier: Double = 1.0,
    val fuelConsumptionRate: Double = 1.0,
    val fuelEfficiencyBonus: Double = 1.0,
    val smeltTimeModifier: Double = 1.0,
    /**
     * Multiplier on the energy a factory furnace spends per item, mirroring the classic mod's
     * green augment handling (`energyRecipe * 2` for speed, `energyRecipe / 2` for fuel).
     */
    val powerDrawMultiplier: Double = 1.0,
    val inputSlots: Int = 1,
    val outputSlots: Int = 1,
    val usesEnergy: Boolean = false,
    val generatorPowerMultiplier: Double = 1.0,
    val generatorSpeedMultiplier: Double = 1.0,
    val canSmelt: Boolean = true
)

class UpgradeEffectManager(
    private val upgradeRedSlot: VirtualInventory,
    private val upgradeGreenSlot: VirtualInventory,
    private val upgradeBlueSlot: VirtualInventory
) {
    companion object {
        private val TYPES_BY_KEY = UpgradeType.entries.associateBy(UpgradeType::key)

        fun getUpgradeType(stack: ItemStack?): UpgradeType? {
            if (stack == null || stack.isEmpty) return null
            val schema = RebarItemSchema.fromStack(stack) ?: return null
            return TYPES_BY_KEY[schema.key]
        }

        fun canPlaceInSlot(upgradeType: UpgradeType, slotIndex: Int): Boolean =
            upgradeType.slot.inventoryIndex == slotIndex

        fun isDuplicateUpgrade(
            upgradeType: UpgradeType,
            redSlot: VirtualInventory,
            greenSlot: VirtualInventory,
            blueSlot: VirtualInventory
        ): Boolean =
            getUpgradeType(redSlot.getUnsafeItem(0)) == upgradeType ||
                getUpgradeType(greenSlot.getUnsafeItem(0)) == upgradeType ||
                getUpgradeType(blueSlot.getUnsafeItem(0)) == upgradeType

    }

    private var cachedEffects: UpgradeEffects? = null

    fun calculateEffects(): UpgradeEffects = cachedEffects ?: computeEffects().also { cachedEffects = it }

    fun getInstalledUpgrades(): Set<UpgradeType> = buildSet(3) {
        getUpgradeType(upgradeRedSlot.getUnsafeItem(0))?.let(::add)
        getUpgradeType(upgradeGreenSlot.getUnsafeItem(0))?.let(::add)
        getUpgradeType(upgradeBlueSlot.getUnsafeItem(0))?.let(::add)
    }

    fun invalidateCache() {
        cachedEffects = null
    }

    fun getDisplayBlockType(): String = when (calculateEffects().mode) {
        FurnaceMode.BLAST, FurnaceMode.GENERATOR_BLAST -> "blast_furnace"
        FurnaceMode.SMOKER, FurnaceMode.GENERATOR_SMOKER -> "smoker"
        else -> "furnace"
    }

    fun isRecipeCompatible(recipeType: RecipeCompatibility): Boolean = when (calculateEffects().mode) {
        FurnaceMode.NORMAL, FurnaceMode.INDUSTRIAL -> true
        FurnaceMode.BLAST -> recipeType == RecipeCompatibility.BLAST || recipeType == RecipeCompatibility.ANY
        FurnaceMode.SMOKER -> recipeType == RecipeCompatibility.SMOKER || recipeType == RecipeCompatibility.ANY
        FurnaceMode.GENERATOR_ONLY,
        FurnaceMode.GENERATOR_BLAST,
        FurnaceMode.GENERATOR_SMOKER -> false
    }

    private fun computeEffects(): UpgradeEffects {
        val red = getUpgradeType(upgradeRedSlot.getUnsafeItem(0))
        val green = getUpgradeType(upgradeGreenSlot.getUnsafeItem(0))
        val blue = getUpgradeType(upgradeBlueSlot.getUnsafeItem(0))
        return UpgradeEffectCalculator.calculate(red, green, blue)
    }

    enum class RecipeCompatibility {
        NORMAL,
        BLAST,
        SMOKER,
        ANY
    }
}

internal object UpgradeEffectCalculator {
    fun calculate(
        red: UpgradeType?,
        green: UpgradeType?,
        blue: UpgradeType?
    ): UpgradeEffects {
        val hasBlast = red == UpgradeType.BLAST
        val hasSmoker = red == UpgradeType.SMOKER
        val hasSpeed = green == UpgradeType.SPEED
        val hasFuel = green == UpgradeType.FUEL

        return when (blue) {
            UpgradeType.GENERATOR -> generatorEffects(
                when {
                    hasBlast -> FurnaceMode.GENERATOR_BLAST
                    hasSmoker -> FurnaceMode.GENERATOR_SMOKER
                    else -> FurnaceMode.GENERATOR_ONLY
                },
                hasSpeed,
                hasFuel
            )
            UpgradeType.INDUSTRIAL -> industrialEffects(hasBlast, hasSmoker, hasSpeed, hasFuel)
            else -> when {
                hasBlast -> specializedEffects(FurnaceMode.BLAST, hasSpeed, hasFuel)
                hasSmoker -> specializedEffects(FurnaceMode.SMOKER, hasSpeed, hasFuel)
                else -> normalEffects(hasSpeed, hasFuel)
            }
        }
    }

    private fun normalEffects(hasSpeed: Boolean, hasFuel: Boolean): UpgradeEffects {
        var speedMultiplier = 1.0
        var fuelConsumptionRate = 1.0
        var fuelEfficiency = 1.0
        var smeltTimeModifier = 1.0

        if (hasSpeed) {
            speedMultiplier *= 2.0
            smeltTimeModifier *= 0.5
            fuelConsumptionRate *= 2.0
        }
        if (hasFuel) {
            fuelEfficiency *= 2.0
            smeltTimeModifier *= 1.25
        }

        return UpgradeEffects(
            speedMultiplier = speedMultiplier,
            fuelConsumptionRate = fuelConsumptionRate,
            fuelEfficiencyBonus = fuelEfficiency,
            smeltTimeModifier = smeltTimeModifier
        )
    }

    private fun specializedEffects(
        mode: FurnaceMode,
        hasSpeed: Boolean,
        hasFuel: Boolean
    ): UpgradeEffects {
        var speedMultiplier = 2.0
        var fuelConsumptionRate = 2.0
        var smeltTimeModifier = 0.5

        when {
            hasSpeed && hasFuel -> {
                speedMultiplier = 1.6
                smeltTimeModifier *= 1.25
            }
            hasSpeed -> {
                speedMultiplier = 4.0
                fuelConsumptionRate = 4.0
                smeltTimeModifier *= 0.5
            }
            hasFuel -> {
                speedMultiplier = 1.6
                smeltTimeModifier *= 1.25
            }
        }

        return UpgradeEffects(
            mode = mode,
            speedMultiplier = speedMultiplier,
            fuelConsumptionRate = fuelConsumptionRate,
            smeltTimeModifier = smeltTimeModifier
        )
    }

    private fun industrialEffects(
        hasBlast: Boolean,
        hasSmoker: Boolean,
        hasSpeed: Boolean,
        hasFuel: Boolean
    ): UpgradeEffects {
        var speedMultiplier = 1.0
        var smeltTimeModifier = 1.0
        var powerDrawMultiplier = 1.0

        // Blasting/smoking recipes are 100 ticks in vanilla, i.e. half of a smelting recipe.
        if (hasBlast || hasSmoker) smeltTimeModifier *= 0.5
        if (hasSpeed) {
            speedMultiplier *= 2.0
            smeltTimeModifier *= 0.5
            powerDrawMultiplier *= 4.0
        }
        if (hasFuel) {
            smeltTimeModifier *= 1.25
            powerDrawMultiplier *= 0.4
        }

        return UpgradeEffects(
            mode = when {
                hasBlast -> FurnaceMode.BLAST
                hasSmoker -> FurnaceMode.SMOKER
                else -> FurnaceMode.INDUSTRIAL
            },
            speedMultiplier = speedMultiplier,
            smeltTimeModifier = smeltTimeModifier,
            powerDrawMultiplier = powerDrawMultiplier,
            // The factory layout has six slots; how many of them a given furnace unlocks depends on
            // its tier, so the furnace clamps this against its own factory slot count.
            inputSlots = 6,
            outputSlots = 6,
            usesEnergy = true
        )
    }

    private fun generatorEffects(
        mode: FurnaceMode,
        hasSpeed: Boolean,
        hasFuel: Boolean
    ): UpgradeEffects {
        var powerMultiplier = 1.0
        var speedMultiplier = 1.0

        if (hasSpeed) {
            powerMultiplier *= 2.0
            speedMultiplier *= 2.0
        }
        if (hasFuel) {
            powerMultiplier *= 0.75
            speedMultiplier *= 0.5
        }

        return UpgradeEffects(
            mode = mode,
            generatorPowerMultiplier = powerMultiplier,
            generatorSpeedMultiplier = speedMultiplier,
            canSmelt = false
        )
    }
}
