package top.mc506lw.rebar.ironfurnaces.furnace

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.mc506lw.rebar.ironfurnaces.upgrades.UpgradeType

class UpgradeEffectCalculatorTest {
    @Test
    fun `normal furnace has neutral effects`() {
        assertEquals(UpgradeEffects(), UpgradeEffectCalculator.calculate(null, null, null))
    }

    @Test
    fun `speed and fuel upgrades preserve their balancing tradeoffs`() {
        val speed = UpgradeEffectCalculator.calculate(null, UpgradeType.SPEED, null)
        assertEquals(2.0, speed.speedMultiplier)
        assertEquals(2.0, speed.fuelConsumptionRate)
        assertEquals(0.5, speed.smeltTimeModifier)

        val fuel = UpgradeEffectCalculator.calculate(null, UpgradeType.FUEL, null)
        assertEquals(2.0, fuel.fuelEfficiencyBonus)
        assertEquals(1.25, fuel.smeltTimeModifier)
    }

    @Test
    fun `specialized furnace modes keep their recipe restrictions`() {
        val blast = UpgradeEffectCalculator.calculate(UpgradeType.BLAST, UpgradeType.SPEED, null)
        assertEquals(FurnaceMode.BLAST, blast.mode)
        assertEquals(4.0, blast.speedMultiplier)
        assertEquals(4.0, blast.fuelConsumptionRate)
        assertEquals(0.25, blast.smeltTimeModifier)

        val smoker = UpgradeEffectCalculator.calculate(UpgradeType.SMOKER, null, null)
        assertEquals(FurnaceMode.SMOKER, smoker.mode)
    }

    @Test
    fun `industrial and generator modes expose the expected capabilities`() {
        val industrial = UpgradeEffectCalculator.calculate(null, null, UpgradeType.INDUSTRIAL)
        assertEquals(FurnaceMode.INDUSTRIAL, industrial.mode)
        // The factory layout has six slots; the furnace clamps that against its own tier.
        assertEquals(6, industrial.inputSlots)
        assertEquals(6, industrial.outputSlots)
        assertEquals(1.0, industrial.powerDrawMultiplier)
        assertTrue(industrial.usesEnergy)
        assertTrue(industrial.canSmelt)

        val generator = UpgradeEffectCalculator.calculate(
            UpgradeType.BLAST,
            UpgradeType.FUEL,
            UpgradeType.GENERATOR
        )
        assertEquals(FurnaceMode.GENERATOR_BLAST, generator.mode)
        assertEquals(0.75, generator.generatorPowerMultiplier)
        assertEquals(0.5, generator.generatorSpeedMultiplier)
        assertFalse(generator.canSmelt)
    }

    @Test
    fun `factory furnaces keep blasting speed and scale their energy per item`() {
        // Blasting/smoking recipes are half as long, whether or not the furnace is a factory.
        val blast = UpgradeEffectCalculator.calculate(UpgradeType.BLAST, null, UpgradeType.INDUSTRIAL)
        assertEquals(FurnaceMode.BLAST, blast.mode)
        assertEquals(0.5, blast.smeltTimeModifier)

        // Speed halves the cook time but doubles the energy spent on the item, so the draw per tick
        // ends up four times as high; the fuel augment is the exact opposite trade.
        val speed = UpgradeEffectCalculator.calculate(null, UpgradeType.SPEED, UpgradeType.INDUSTRIAL)
        assertEquals(4.0, speed.powerDrawMultiplier)
        assertEquals(0.5, speed.smeltTimeModifier)

        val fuel = UpgradeEffectCalculator.calculate(null, UpgradeType.FUEL, UpgradeType.INDUSTRIAL)
        assertEquals(0.4, fuel.powerDrawMultiplier)
        assertEquals(1.25, fuel.smeltTimeModifier)
    }

    @Test
    fun `generator green augments trade output against burn speed`() {
        val speed = UpgradeEffectCalculator.calculate(null, UpgradeType.SPEED, UpgradeType.GENERATOR)
        assertEquals(2.0, speed.generatorPowerMultiplier)
        assertEquals(2.0, speed.generatorSpeedMultiplier)

        val fuel = UpgradeEffectCalculator.calculate(null, UpgradeType.FUEL, UpgradeType.GENERATOR)
        assertEquals(0.75, fuel.generatorPowerMultiplier)
        assertEquals(0.5, fuel.generatorSpeedMultiplier)
    }

    @Test
    fun `furnace tiers match the classic mod speeds, generation and factory slots`() {
        assertEquals(180, FurnaceTier.COPPER.smeltTimePerItem)
        assertEquals(160, FurnaceTier.IRON.smeltTimePerItem)
        assertEquals(120, FurnaceTier.GOLD.smeltTimePerItem)
        assertEquals(80, FurnaceTier.DIAMOND.smeltTimePerItem)
        assertEquals(40, FurnaceTier.EMERALD.smeltTimePerItem)
        assertEquals(40, FurnaceTier.CRYSTAL.smeltTimePerItem)
        assertEquals(20, FurnaceTier.OBSIDIAN.smeltTimePerItem)
        assertEquals(5, FurnaceTier.NETHERITE.smeltTimePerItem)
        assertEquals(20, FurnaceTier.RAINBOW.smeltTimePerItem)

        assertEquals(40.0, FurnaceTier.COPPER.generation)
        assertEquals(40.0, FurnaceTier.IRON.generation)
        assertEquals(160.0, FurnaceTier.GOLD.generation)
        assertEquals(240.0, FurnaceTier.DIAMOND.generation)
        assertEquals(320.0, FurnaceTier.EMERALD.generation)
        assertEquals(360.0, FurnaceTier.CRYSTAL.generation)
        assertEquals(500.0, FurnaceTier.OBSIDIAN.generation)
        assertEquals(1000.0, FurnaceTier.NETHERITE.generation)
        assertEquals(2000.0, FurnaceTier.RAINBOW.generation)

        // tier 0 -> 2 slots, tier 1 -> 4 slots, tier 2 -> 6 slots
        assertEquals(2, FurnaceTier.COPPER.factorySlots)
        assertEquals(2, FurnaceTier.IRON.factorySlots)
        assertEquals(4, FurnaceTier.GOLD.factorySlots)
        assertEquals(6, FurnaceTier.DIAMOND.factorySlots)
        assertEquals(6, FurnaceTier.EMERALD.factorySlots)
        assertEquals(6, FurnaceTier.CRYSTAL.factorySlots)
        assertEquals(6, FurnaceTier.OBSIDIAN.factorySlots)
        assertEquals(6, FurnaceTier.NETHERITE.factorySlots)
        assertEquals(6, FurnaceTier.RAINBOW.factorySlots)
    }
}
