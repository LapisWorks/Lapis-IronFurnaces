package top.mc506lw.rebar.ironfurnaces.items

import io.github.pylonmc.rebar.item.RebarItemSchema
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import top.mc506lw.rebar.ironfurnaces.IronFurnaceKeys

/**
 * **加热棒** —— 复刻 26.3 的 `ItemHeater`。
 *
 * ## 它是干什么的
 *
 * 在**无线能量加热器**上右键一次，它就会被"刻上"那台加热器的坐标（见
 * [top.mc506lw.rebar.ironfurnaces.furnace.WirelessEnergyHeater.onInteractedWith]）。
 * 之后把它放进任意一台熔炉的**燃料槽**，那台熔炉就改吃电 —— 烧的其实是加热器
 * 缓冲里的能量（见 `FurnaceFuelSystem.consumeFuel`）。
 *
 * 棒**不会被消耗**（它不是燃料，是"钥匙"），拿回来还能插到别的熔炉上。
 *
 * ## 为什么用 PDC 存坐标而不是物品 NBT 容器
 *
 * 和别处一样的理由：PDC 的键只允许 `[a-z0-9_-./]`，但这里存的都是普通字符串/整数，
 * 其实两种都行 —— 用 PDC 是为了和本项目其它物品保持一致。
 */
object ItemHeater {

    /** 这件物品是不是加热棒（按 Rebar 物品键认，不看材质）。 */
    fun isHeater(stack: ItemStack): Boolean =
        RebarItemSchema.fromStack(stack)?.key == IronFurnaceKeys.ITEM_HEATER

    /** 这根棒刻过坐标没有。 */
    fun isBound(stack: ItemStack): Boolean = boundTo(stack) != null

    /** 把 [block] 的坐标刻进棒里。 */
    fun bind(stack: ItemStack, block: Block) {
        stack.editMeta { meta ->
            val pdc = meta.persistentDataContainer
            pdc.set(IronFurnaceKeys.HEATER_WORLD, PersistentDataType.STRING, block.world.name)
            pdc.set(IronFurnaceKeys.HEATER_X, PersistentDataType.INTEGER, block.x)
            pdc.set(IronFurnaceKeys.HEATER_Y, PersistentDataType.INTEGER, block.y)
            pdc.set(IronFurnaceKeys.HEATER_Z, PersistentDataType.INTEGER, block.z)
        }
    }

    /**
     * 棒刻的坐标；没刻过、或那个世界已经没了就返回 `null`。
     *
     * ⚠️ 这里**不检查区块加载** —— 调用方自己判（`world.isChunkLoaded`）。
     * 理由是调用方通常还要顺手决定"没加载时怎么办"（跳过 / 提示玩家）。
     */
    fun boundTo(stack: ItemStack): Location? {
        val pdc = stack.itemMeta?.persistentDataContainer ?: return null
        val worldName = pdc.get(IronFurnaceKeys.HEATER_WORLD, PersistentDataType.STRING)
            ?: return null
        val world = Bukkit.getWorld(worldName) ?: return null
        val x = pdc.get(IronFurnaceKeys.HEATER_X, PersistentDataType.INTEGER) ?: return null
        val y = pdc.get(IronFurnaceKeys.HEATER_Y, PersistentDataType.INTEGER) ?: return null
        val z = pdc.get(IronFurnaceKeys.HEATER_Z, PersistentDataType.INTEGER) ?: return null
        return Location(world, x.toDouble(), y.toDouble(), z.toDouble())
    }
}
