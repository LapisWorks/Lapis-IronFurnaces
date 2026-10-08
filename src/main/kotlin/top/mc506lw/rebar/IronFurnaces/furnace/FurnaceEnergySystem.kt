package top.mc506lw.rebar.ironfurnaces.furnace

import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import top.mc506lw.rebar.ironfurnaces.IronFurnaceKeys

interface EnergyConsumer {
    fun consumeEnergy(amount: Double): Boolean
    fun retrieveCurrentEnergy(): Double
    fun getMaxEnergy(): Double
    fun getEnergyPercentage(): Double
}

interface EnergyProducer {
    fun produceEnergy(amount: Double)
    fun retrieveTotalProduced(): Double
    fun getProductionRate(): Double
}

class FurnaceEnergySystem(
    private val maxCapacity: Double = 10_000.0
) : EnergyConsumer, EnergyProducer {
    @Suppress("UNUSED_PARAMETER")
    constructor(block: org.bukkit.block.Block, maxCapacity: Double = 10_000.0) : this(maxCapacity)

    private var currentEnergy = 0.0
    private var totalProduced = 0.0
    private var lastProductionRate = 0.0
    private var lastProductionNanos = 0L

    init {
        require(maxCapacity.isFinite() && maxCapacity > 0.0) { "maxCapacity must be finite and positive" }
    }

    override fun consumeEnergy(amount: Double): Boolean {
        if (!amount.isFinite() || amount < 0.0 || currentEnergy < amount) return false
        currentEnergy -= amount
        return true
    }

    override fun retrieveCurrentEnergy(): Double = currentEnergy

    override fun getMaxEnergy(): Double = maxCapacity

    override fun getEnergyPercentage(): Double = currentEnergy / maxCapacity * 100.0

    override fun produceEnergy(amount: Double) {
        if (!amount.isFinite() || amount <= 0.0) return

        val previousEnergy = currentEnergy
        currentEnergy = (currentEnergy + amount).coerceAtMost(maxCapacity)
        totalProduced += currentEnergy - previousEnergy

        val now = System.nanoTime()
        if (lastProductionNanos != 0L) {
            val seconds = (now - lastProductionNanos) / 1_000_000_000.0
            if (seconds > 0.0) lastProductionRate = amount / seconds
        }
        lastProductionNanos = now
    }

    override fun retrieveTotalProduced(): Double = totalProduced

    override fun getProductionRate(): Double = lastProductionRate

    fun convertHeatToEnergy(heatAmount: Double, wattsPerTick: Double): Double {
        if (!heatAmount.isFinite() || !wattsPerTick.isFinite() || heatAmount <= 0.0 || wattsPerTick <= 0.0) {
            return 0.0
        }
        val produced = heatAmount * wattsPerTick
        produceEnergy(produced)
        return produced
    }

    fun restore(pdc: PersistentDataContainer) {
        currentEnergy = pdc.getOrDefault(IronFurnaceKeys.ENERGY_CURRENT, PersistentDataType.DOUBLE, 0.0)
            .takeIf(Double::isFinite)
            ?.coerceIn(0.0, maxCapacity)
            ?: 0.0
        totalProduced = pdc.getOrDefault(
            IronFurnaceKeys.ENERGY_TOTAL_PRODUCED,
            PersistentDataType.DOUBLE,
            0.0
        ).takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
    }

    fun write(pdc: PersistentDataContainer) {
        pdc.set(IronFurnaceKeys.ENERGY_CURRENT, PersistentDataType.DOUBLE, currentEnergy)
        pdc.set(IronFurnaceKeys.ENERGY_TOTAL_PRODUCED, PersistentDataType.DOUBLE, totalProduced)
    }

    fun reset() {
        currentEnergy = 0.0
        totalProduced = 0.0
        lastProductionRate = 0.0
        lastProductionNanos = 0L
    }

    fun isGeneratorMode(): Boolean = totalProduced > 0.0

    fun getEnergyInfo(): String = "能量: %.1f/%.1f (%.1f%%)".format(
        java.util.Locale.ROOT,
        currentEnergy,
        maxCapacity,
        getEnergyPercentage()
    )

    fun getProductionInfo(): String = if (isGeneratorMode()) {
        "发电量: %.1f FE | 速率: %.2f FE/t".format(
            java.util.Locale.ROOT,
            totalProduced,
            lastProductionRate
        )
    } else {
        "未发电"
    }
}
