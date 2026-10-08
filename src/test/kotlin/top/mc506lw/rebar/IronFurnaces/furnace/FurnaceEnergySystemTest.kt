package top.mc506lw.rebar.ironfurnaces.furnace

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FurnaceEnergySystemTest {
    @Test
    fun `production is capped and only stored energy counts as produced`() {
        val energy = FurnaceEnergySystem(maxCapacity = 100.0)

        energy.produceEnergy(80.0)
        energy.produceEnergy(50.0)

        assertEquals(100.0, energy.retrieveCurrentEnergy())
        assertEquals(100.0, energy.retrieveTotalProduced())
        assertEquals(100.0, energy.getEnergyPercentage())
    }

    @Test
    fun `consumption is transactional and rejects invalid amounts`() {
        val energy = FurnaceEnergySystem(maxCapacity = 100.0)
        energy.produceEnergy(40.0)

        assertFalse(energy.consumeEnergy(50.0))
        assertFalse(energy.consumeEnergy(Double.NaN))
        assertFalse(energy.consumeEnergy(-1.0))
        assertTrue(energy.consumeEnergy(25.0))
        assertEquals(15.0, energy.retrieveCurrentEnergy())
    }

    @Test
    fun `heat conversion is heat times watts per tick`() {
        val energy = FurnaceEnergySystem(maxCapacity = 1_000.0)

        assertEquals(100.0, energy.convertHeatToEnergy(5.0, 20.0))
        assertEquals(100.0, energy.retrieveCurrentEnergy())
        assertEquals(0.0, energy.convertHeatToEnergy(-1.0, 20.0))
        assertEquals(0.0, energy.convertHeatToEnergy(5.0, -1.0))
    }
}
