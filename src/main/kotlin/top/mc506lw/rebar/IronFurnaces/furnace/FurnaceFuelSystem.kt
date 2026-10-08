package top.mc506lw.rebar.ironfurnaces.furnace

import io.github.pylonmc.rebar.i18n.RebarArgument
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder
import io.github.pylonmc.rebar.util.MachineUpdateReason
import io.github.pylonmc.rebar.util.gui.GuiItems
import io.github.pylonmc.rebar.util.gui.ProgressItem
import net.kyori.adventure.text.Component
import net.minecraft.server.level.ServerLevel
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.craftbukkit.CraftWorld
import org.bukkit.craftbukkit.inventory.CraftItemStack
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import top.mc506lw.rebar.ironfurnaces.IronFurnaceKeys
import xyz.xenondevs.invui.inventory.VirtualInventory
import java.util.EnumMap
import java.util.logging.Level
import kotlin.math.floor

class FurnaceFuelSystem(
    private val block: Block,
    private val furnaceTier: FurnaceTier,
    private val fuelInv: VirtualInventory
) {
    @Suppress("UNUSED_PARAMETER")
    constructor(
        block: Block,
        furnaceTier: FurnaceTier,
        fuelInv: VirtualInventory,
        inputInv: VirtualInventory,
        fuelEfficiency: Double = 1.0
    ) : this(block, furnaceTier, fuelInv) {
        this.fuelEfficiency = fuelEfficiency
    }

    companion object {
        private val LOGGER = java.util.logging.Logger.getLogger("IronFurnace")
        private val MACHINE_UPDATE_REASON = MachineUpdateReason()
        private val FUEL_DURATIONS = EnumMap<Material, Int>(Material::class.java)
    }

    var currentFuelTime = 0
        internal set(value) {
            field = value.coerceAtLeast(0)
        }

    var fuelRemaining = 0
        internal set(value) {
            field = value.coerceAtLeast(0)
        }

    var fuelEfficiency = 1.0
        set(value) {
            field = value.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
        }

    var fuelConsumptionRate = 1.0
        set(value) {
            field = value.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
        }

    var speedMultiplier = 1.0
        set(value) {
            field = value.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
        }

    /**
     * Whether the furnace's own speed shortens how long a fuel burns. Smelting furnaces scale the
     * burn time with their speed (so every tier smelts the same number of items per fuel), while
     * generator furnaces burn the fuel for its full burn time so higher tiers produce more energy.
     */
    var tierSpeedScaling = true

    val fuelProgressItem = ProgressItem(GuiItems.background())

    val isBurning: Boolean
        get() = fuelRemaining > 0

    val totalSpeedMultiplier: Double
        get() = if (tierSpeedScaling) furnaceTier.speedMultiplier * speedMultiplier else speedMultiplier

    private var burningFuelType: Material? = null
    private var consumptionFraction = 0.0
    private var clearDisplayUpdates = 0

    fun consumeFuel(): Boolean {
        if (isBurning) return true

        val fuelStack = fuelInv.getUnsafeItem(0) ?: return false
        if (fuelStack.isEmpty) return false

        val fuelTime = getFuelTimeFromServer(fuelStack)
        if (fuelTime <= 0) return false

        val remainingStack = if (fuelStack.amount > 1) {
            fuelStack.clone().apply { amount = fuelStack.amount - 1 }
        } else {
            null
        }
        if (!fuelInv.setItem(MACHINE_UPDATE_REASON, 0, remainingStack)) return false

        val adjustedFuelTime = (fuelTime * fuelEfficiency / totalSpeedMultiplier)
            .toInt()
            .coerceAtLeast(1)

        currentFuelTime = adjustedFuelTime
        fuelRemaining = adjustedFuelTime
        burningFuelType = fuelStack.type
        consumptionFraction = 0.0
        clearDisplayUpdates = 0
        updateFuelProgressDisplay(fuelStack.type)
        return true
    }

    fun updateFuelState(elapsedTicks: Int, rateModifier: Double = 1.0) {
        if (elapsedTicks <= 0) return

        if (clearDisplayUpdates > 0) {
            clearDisplayUpdates--
            if (clearDisplayUpdates == 0) clearFuelDisplay()
            return
        }

        if (!isBurning) return

        val safeModifier = rateModifier.takeIf { it.isFinite() && it >= 0.0 } ?: 1.0
        val exactConsumption = elapsedTicks * fuelConsumptionRate * safeModifier + consumptionFraction
        val consumedTicks = floor(exactConsumption).toInt()
        consumptionFraction = exactConsumption - consumedTicks

        if (consumedTicks <= 0) return

        fuelRemaining = (fuelRemaining - consumedTicks).coerceAtLeast(0)
        fuelProgressItem.setRemainingTimeTicks(fuelRemaining)

        if (!isBurning) {
            burningFuelType = null
            consumptionFraction = 0.0
            clearDisplayUpdates = 2
        }
        fuelProgressItem.notifyWindows()
    }

    fun restore(pdc: PersistentDataContainer) {
        currentFuelTime = pdc.getOrDefault(IronFurnaceKeys.FUEL_TIME, PersistentDataType.INTEGER, 0)
        fuelRemaining = pdc.getOrDefault(IronFurnaceKeys.FUEL_REMAINING, PersistentDataType.INTEGER, 0)
            .coerceAtMost(currentFuelTime)
        consumptionFraction = pdc.getOrDefault(
            IronFurnaceKeys.FUEL_CONSUMPTION_FRACTION,
            PersistentDataType.DOUBLE,
            0.0
        ).takeIf { it.isFinite() && it in 0.0..<1.0 } ?: 0.0

        burningFuelType = pdc.get(IronFurnaceKeys.FUEL_TYPE, PersistentDataType.STRING)
            ?.let(Material::matchMaterial)
            ?.takeUnless { it.isAir }

        if (fuelRemaining > 0 && burningFuelType == null) {
            fuelRemaining = 0
            currentFuelTime = 0
        }
    }

    fun write(pdc: PersistentDataContainer) {
        pdc.set(IronFurnaceKeys.FUEL_TIME, PersistentDataType.INTEGER, currentFuelTime)
        pdc.set(IronFurnaceKeys.FUEL_REMAINING, PersistentDataType.INTEGER, fuelRemaining)
        pdc.set(
            IronFurnaceKeys.FUEL_CONSUMPTION_FRACTION,
            PersistentDataType.DOUBLE,
            consumptionFraction
        )

        val fuelType = burningFuelType
        if (fuelType == null) {
            pdc.remove(IronFurnaceKeys.FUEL_TYPE)
        } else {
            pdc.set(IronFurnaceKeys.FUEL_TYPE, PersistentDataType.STRING, fuelType.key.asString())
        }
    }

    fun refreshDisplay() {
        val fuelType = burningFuelType
        if (fuelRemaining > 0 && currentFuelTime > 0 && fuelType != null) {
            updateFuelProgressDisplay(fuelType)
            fuelProgressItem.setRemainingTimeTicks(fuelRemaining)
        } else {
            clearFuelDisplay()
        }
    }

    internal fun getFuelTimeFromServer(stack: ItemStack): Int {
        FUEL_DURATIONS[stack.type]?.let { return it }

        return try {
            val serverLevel: ServerLevel = (block.world as CraftWorld).handle
            serverLevel.fuelValues().burnDuration(CraftItemStack.asNMSCopy(stack)).also { duration ->
                FUEL_DURATIONS[stack.type] = duration
            }
        } catch (exception: Exception) {
            LOGGER.log(
                Level.SEVERE,
                "[${furnaceTier.name}] 无法查询燃料 ${stack.type} 的燃烧时间",
                exception
            )
            0
        }
    }

    private fun updateFuelProgressDisplay(material: Material) {
        fuelProgressItem.setTotalTimeTicks(currentFuelTime)
        fuelProgressItem.setRemainingTimeTicks(fuelRemaining)
        fuelProgressItem.setItem(
            ItemStackBuilder.of(material)
                .name(Component.translatable("ironfurnaces.gui.fuel_status.name"))
                .lore(
                    Component.translatable(
                        "ironfurnaces.gui.fuel_status.lore",
                        RebarArgument.of("fuel", ItemStack.of(material).displayName())
                    )
                )
        )
    }

    private fun clearFuelDisplay() {
        fuelProgressItem.setTotalTimeTicks(null)
        fuelProgressItem.setItem(GuiItems.background())
        fuelProgressItem.notifyWindows()
    }
}
