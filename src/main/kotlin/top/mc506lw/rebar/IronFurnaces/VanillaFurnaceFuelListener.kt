package top.mc506lw.rebar.ironfurnaces

import io.github.pylonmc.rebar.block.BlockStorage
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.FurnaceBurnEvent
import org.bukkit.inventory.ItemStack
import top.mc506lw.rebar.ironfurnaces.furnace.WirelessEnergyHeater
import top.mc506lw.rebar.ironfurnaces.items.ItemHeater

/**
 * **原版熔炉**的燃料接管。
 *
 * ## 为什么需要它
 *
 * 我们自己的熔炉走 [top.mc506lw.rebar.ironfurnaces.furnace.FurnaceFuelSystem]，
 * 想怎么算就怎么算。但**原版熔炉 / 高炉 / 烟熏炉**只认原版那套"材质 → 燃烧时间"表，
 * 于是两件自定义物品都会走样：
 *
 * | 物品 | 底块 | 原版会怎么理解 | 我们要它怎么样 |
 * |---|---|---|---|
 * | 彩虹煤 | `COAL` | 普通煤（200 tick）| **2000 tick**（10 倍）|
 * | 加热棒 | `BLAZE_ROD` | 烈焰棒燃料（2400 tick）**且会被烧掉** | 不烧它，改从绑定的加热器扣电 |
 *
 * ## 加热棒为什么能走到这里
 *
 * 因为它的底块**正好是烈焰棒** —— 原版本来就认它是燃料，所以 `FurnaceBurnEvent`
 * 会正常触发。于是我们能在事件里把它"劫下来"：
 * `setConsumeFuel(false)`（别烧掉这根钥匙）+ `setBurnTime(...)`（一次算 200 tick）
 * + 从加热器扣电；**电不够就直接取消**（熔炉停着，和没燃料一样）。
 *
 * 换个底块（比如纸）就走不通了 —— 原版压根不会为它触发事件。
 */
object VanillaFurnaceFuelListener : Listener {


    /**
     * ⚠️ 用 **MONITOR** 而不是 NORMAL：
     * 1. MONITOR 是最后一道，`setBurnTime` / `setConsumeFuel` 不会被后面谁盖掉；
     * 2. **不加 `ignoreCancelled`** —— 诊断阶段要能看见"事件触发了但被别人取消了"，
     *    不然它会安静地消失，我们又得猜一轮。
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onBurn(event: FurnaceBurnEvent) {
        val fuel = event.fuel
        // ⚠️ **诊断日志**：只在"燃料是我们的物品"时打。
        // 用途是区分两种失败：① 事件压根没触发（说明原版不认它当燃料）；
        // ② 触发了但我们没处理对。用户报"烧不了"时先看这行在不在日志里。

        if (event.isCancelled) return

        // 我们自己的熔炉内部已经算过了，别抢它的
        if (runCatching { BlockStorage.get(event.block) }.getOrNull() != null) return

        when {
            ItemHeater.isHeater(fuel) -> {
                val heater = resolveHeater(fuel)
                if (heater == null || !heater.drain(WirelessEnergyHeater.ENERGY_PER_USE)) {
                    // 没绑定 / 加热器没加载 / 电不够 → 这台炉子这次点不着
                    event.isCancelled = true
                    return
                }
                event.burnTime = WirelessEnergyHeater.FUEL_TICKS
                // ⚠️ 加热棒是"钥匙"不是燃料 —— 少了这一句原版会把它烧掉
                event.setConsumeFuel(false)
            }

            IronFurnaceItems.isRainbowCoal(fuel) -> {
                // 耐久型燃料：**不消耗**，扣 1 点耐久；扣完把物品从燃料槽拿走
                event.burnTime = IronFurnaceItems.RAINBOW_COAL_BURN_TICKS
                event.setConsumeFuel(false)
                if (IronFurnaceItems.damageRainbowCoal(fuel)) {
                    // 耐久见底 —— 下一 tick 让原版把它当燃料烧掉（这次放行消耗）
                    event.setConsumeFuel(true)
                }
            }
        }
    }

    /**
     * 加热棒绑定的加热器方块。
     *
     * ⚠️ 区块没加载就返回 `null`（**不做强制加载** —— 那是 TPS 灾难）。
     * 表现是"加热器在未加载区块里时炉子点不着"，这是刻意的。
     */
    private fun resolveHeater(rod: ItemStack): WirelessEnergyHeater? {
        val bound = ItemHeater.boundTo(rod) ?: return null
        if (!bound.world.isChunkLoaded(bound.blockX shr 4, bound.blockZ shr 4)) return null
        return runCatching { BlockStorage.get(bound.block) }.getOrNull() as? WirelessEnergyHeater
    }
}
