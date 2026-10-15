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

        // ⚠️ **无线供能**：燃料槽里放的是**加热棒**时**不烧它** ——
        // 改从它绑定的加热器方块里扣电。棒是"钥匙"，不消耗（见 [ItemHeater]）。
        if (top.mc506lw.rebar.ironfurnaces.items.ItemHeater.isHeater(fuelStack)) {
            return consumeFromHeater(fuelStack)
        }

        // ⚠️ **彩虹煤**：也不"烧掉"，而是**扣 1 点耐久**（对齐 26.3 的
        // `getCraftingRemainder` 把"消耗"换成"耐久 -1"）。扣完才消失。
        if (top.mc506lw.rebar.ironfurnaces.IronFurnaceItems.isRainbowCoal(fuelStack)) {
            return consumeRainbowCoal(fuelStack)
        }

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

    /**
     * **彩虹煤**：扣 1 点耐久，按 [IronFurnaceItems.RAINBOW_COAL_BURN_TICKS] 记账。
     *
     * 耐久扣完才真的把物品从槽里拿走 —— 这就是 26.3 那个
     * `getCraftingRemainder`（返回"耐久 +1 的自己"）在 Paper 这边的等价做法。
     */
    private fun consumeRainbowCoal(stack: org.bukkit.inventory.ItemStack): Boolean {
        val exhausted = top.mc506lw.rebar.ironfurnaces.IronFurnaceItems.damageRainbowCoal(stack)
        // 扣完耐久 → 物品消失；否则**原样写回**（`setItem` 会带上新的 damage 组件）
        if (exhausted && !fuelInv.setItem(MACHINE_UPDATE_REASON, 0, null)) return false
        if (!exhausted && !fuelInv.setItem(MACHINE_UPDATE_REASON, 0, stack.clone())) return false

        currentFuelTime = top.mc506lw.rebar.ironfurnaces.IronFurnaceItems.RAINBOW_COAL_BURN_TICKS
        fuelRemaining = currentFuelTime
        burningFuelType = stack.type
        consumptionFraction = 0.0
        clearDisplayUpdates = 0
        updateFuelProgressDisplay(stack.type)
        return true
    }

    /**
     * **无线供能**：从加热棒绑定的**无线能量加热器**里扣电，按"一次相当于
     * [WirelessEnergyHeater.FUEL_TICKS] tick 的燃料"记账。
     *
     * 三种情况都返回 false（熔炉就停着，和没燃料一样）：
     * 棒没绑定 / 加热器所在区块没加载 / 加热器里的电不够一次。
     *
     * ⚠️ 区块没加载时**不做任何强制加载** —— 那在服务端是 TPS 灾难。
     * 表现就是"加热器在未加载区块里时熔炉不工作"，这是刻意的。
     */
    private fun consumeFromHeater(rod: org.bukkit.inventory.ItemStack): Boolean {
        val bound = top.mc506lw.rebar.ironfurnaces.items.ItemHeater.boundTo(rod) ?: return false
        if (!bound.world.isChunkLoaded(bound.blockX shr 4, bound.blockZ shr 4)) return false

        val heater = runCatching {
            io.github.pylonmc.rebar.block.BlockStorage.get(bound.block)
        }.getOrNull() as? WirelessEnergyHeater ?: return false

        if (!heater.drain(WirelessEnergyHeater.ENERGY_PER_USE)) return false

        currentFuelTime = WirelessEnergyHeater.FUEL_TICKS
        fuelRemaining = WirelessEnergyHeater.FUEL_TICKS
        // ⚠️ 燃烧进度条显示**加热棒自己**（烈焰棒），不是红石块 ——
        // 燃料槽里放的就是它，显示成别的东西玩家会以为机器里多了个物件。
        burningFuelType = rod.type
        consumptionFraction = 0.0
        clearDisplayUpdates = 0
        updateFuelProgressDisplay(rod.type)
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
        // ⚠️ **彩虹煤**：底块是 `Material.COAL`，原版燃料表只认材质。
        // 它是"耐久型燃料"（见 IronFurnaceItems），这里只报**一次**的量。
        if (top.mc506lw.rebar.ironfurnaces.IronFurnaceItems.isRainbowCoal(stack)) {
            return top.mc506lw.rebar.ironfurnaces.IronFurnaceItems.RAINBOW_COAL_BURN_TICKS
        }

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
