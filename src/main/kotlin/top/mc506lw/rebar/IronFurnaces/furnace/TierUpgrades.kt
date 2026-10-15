package top.mc506lw.rebar.ironfurnaces.furnace

import io.github.pylonmc.rebar.item.RebarItemSchema
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import top.mc506lw.rebar.ironfurnaces.IronFurnaceKeys

/**
 * **等级升级** —— 复刻 26.3 的 `ItemUpgrade`：右键一台熔炉，把它升到下一级，
 * **保留里面的东西和设置**。
 *
 * ## 命名照抄 26.3
 *
 * 那边同一个目标等级会有两张（`upgrade_iron` = 原版熔炉→铁，`upgrade_iron2` = 铜→铁），
 * 这里保持一致 —— 玩家从 mod 迁移过来时对得上号。
 *
 * ⚠️ 26.3 的 `upgrade_gold2` 是 **银 → 金**。我们**没有银**（Pylon 里没有），所以不做。
 *
 * ## `from = null` 是什么意思
 *
 * "从**原版熔炉**起步" —— 铜 / 铁那两张是进这条链的**入门券**。
 * 它们的目标不是 Rebar 方块，所以走的是另一条路径（见 `TierUpgradeItem`）。
 */
internal enum class TierUpgrade(
    val key: NamespacedKey,
    val material: Material,
    /** `null` = 从**原版熔炉**起步。 */
    val from: FurnaceTier?,
    val to: FurnaceTier,
) {
    COPPER(IronFurnaceKeys.UPGRADE_COPPER, Material.COPPER_INGOT, null, FurnaceTier.COPPER),
    IRON(IronFurnaceKeys.UPGRADE_IRON, Material.IRON_INGOT, null, FurnaceTier.IRON),
    IRON2(IronFurnaceKeys.UPGRADE_IRON2, Material.IRON_INGOT, FurnaceTier.COPPER, FurnaceTier.IRON),
    GOLD(IronFurnaceKeys.UPGRADE_GOLD, Material.GOLD_INGOT, FurnaceTier.IRON, FurnaceTier.GOLD),
    DIAMOND(IronFurnaceKeys.UPGRADE_DIAMOND, Material.DIAMOND, FurnaceTier.GOLD, FurnaceTier.DIAMOND),
    CRYSTAL(
        IronFurnaceKeys.UPGRADE_CRYSTAL, Material.PRISMARINE_SHARD,
        FurnaceTier.DIAMOND, FurnaceTier.CRYSTAL
    ),
    EMERALD(
        IronFurnaceKeys.UPGRADE_EMERALD, Material.EMERALD,
        FurnaceTier.DIAMOND, FurnaceTier.EMERALD
    ),
    OBSIDIAN(
        IronFurnaceKeys.UPGRADE_OBSIDIAN, Material.OBSIDIAN,
        FurnaceTier.EMERALD, FurnaceTier.OBSIDIAN
    ),
    OBSIDIAN2(
        IronFurnaceKeys.UPGRADE_OBSIDIAN2, Material.OBSIDIAN,
        FurnaceTier.CRYSTAL, FurnaceTier.OBSIDIAN
    ),
    NETHERITE(
        IronFurnaceKeys.UPGRADE_NETHERITE, Material.NETHERITE_INGOT,
        FurnaceTier.OBSIDIAN, FurnaceTier.NETHERITE
    ),
    ;

    companion object {
        private val byKey: Map<NamespacedKey, TierUpgrade> = entries.associateBy { it.key }

        /** 这件物品是哪一张等级升级；不是就返回 `null`。 */
        fun of(stack: ItemStack): TierUpgrade? =
            RebarItemSchema.fromStack(stack)?.key?.let { byKey[it] }
    }
}
