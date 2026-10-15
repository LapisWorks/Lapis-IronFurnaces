package top.mc506lw.rebar.ironfurnaces

import io.github.pylonmc.rebar.block.RebarBlock
import org.bukkit.Material
import org.bukkit.NamespacedKey
import top.mc506lw.rebar.ironfurnaces.furnace.CopperFurnace
import top.mc506lw.rebar.ironfurnaces.furnace.CrystalFurnace
import top.mc506lw.rebar.ironfurnaces.furnace.DiamondFurnace
import top.mc506lw.rebar.ironfurnaces.furnace.EmeraldFurnace
import top.mc506lw.rebar.ironfurnaces.furnace.GoldFurnace
import top.mc506lw.rebar.ironfurnaces.furnace.IronFurnaceImpl
import top.mc506lw.rebar.ironfurnaces.furnace.NetheriteFurnace
import top.mc506lw.rebar.ironfurnaces.furnace.ObsidianFurnace
import top.mc506lw.rebar.ironfurnaces.furnace.RainbowFurnace

object IronFurnaceBlocks {
    private data class BlockDefinition(
        val key: NamespacedKey,
        val placementMaterial: Material,
        val implementation: Class<out RebarBlock>
    )

    private val definitions = listOf(
        BlockDefinition(IronFurnaceKeys.COPPER_FURNACE, Material.FURNACE, CopperFurnace::class.java),
        BlockDefinition(IronFurnaceKeys.IRON_FURNACE, Material.FURNACE, IronFurnaceImpl::class.java),
        BlockDefinition(IronFurnaceKeys.GOLD_FURNACE, Material.FURNACE, GoldFurnace::class.java),
        BlockDefinition(IronFurnaceKeys.DIAMOND_FURNACE, Material.FURNACE, DiamondFurnace::class.java),
        BlockDefinition(IronFurnaceKeys.EMERALD_FURNACE, Material.FURNACE, EmeraldFurnace::class.java),
        BlockDefinition(IronFurnaceKeys.CRYSTAL_FURNACE, Material.FURNACE, CrystalFurnace::class.java),
        BlockDefinition(IronFurnaceKeys.OBSIDIAN_FURNACE, Material.FURNACE, ObsidianFurnace::class.java),
        BlockDefinition(IronFurnaceKeys.NETHERITE_FURNACE, Material.FURNACE, NetheriteFurnace::class.java),
        BlockDefinition(IronFurnaceKeys.RAINBOW_FURNACE, Material.BEACON, RainbowFurnace::class.java)
    )

    /** 所有熔炉的实现类 —— 给 Sided-IO 的扳手桥用（RebarWrench 按精确类查表）。 */
    fun implementationClasses(): List<Class<out RebarBlock>> = definitions.map { it.implementation }

    /**
     * 所有熔炉方块的键。
     *
     * 给"拆机保留设置"用：拆掉熔炉时要在掉落物里认出**哪一件是熔炉**，
     * 才能把配置写进它。用 Rebar 物品键而不是材质 —— 材质是 `FURNACE` / `BEACON`，
     * 和别的物品撞得起。
     */
    val keys: List<NamespacedKey> = definitions.map { it.key }

    /**
     * 等级 → 方块键。
     *
     * 给**等级升级**用：升完之后要在**同一个位置**把下一级的方块放回去。
     */
    private val keysByTier: Map<top.mc506lw.rebar.ironfurnaces.furnace.FurnaceTier, NamespacedKey> =
        mapOf(
            top.mc506lw.rebar.ironfurnaces.furnace.FurnaceTier.COPPER to IronFurnaceKeys.COPPER_FURNACE,
            top.mc506lw.rebar.ironfurnaces.furnace.FurnaceTier.IRON to IronFurnaceKeys.IRON_FURNACE,
            top.mc506lw.rebar.ironfurnaces.furnace.FurnaceTier.GOLD to IronFurnaceKeys.GOLD_FURNACE,
            top.mc506lw.rebar.ironfurnaces.furnace.FurnaceTier.DIAMOND to IronFurnaceKeys.DIAMOND_FURNACE,
            top.mc506lw.rebar.ironfurnaces.furnace.FurnaceTier.EMERALD to IronFurnaceKeys.EMERALD_FURNACE,
            top.mc506lw.rebar.ironfurnaces.furnace.FurnaceTier.CRYSTAL to IronFurnaceKeys.CRYSTAL_FURNACE,
            top.mc506lw.rebar.ironfurnaces.furnace.FurnaceTier.OBSIDIAN to IronFurnaceKeys.OBSIDIAN_FURNACE,
            top.mc506lw.rebar.ironfurnaces.furnace.FurnaceTier.NETHERITE to IronFurnaceKeys.NETHERITE_FURNACE,
            top.mc506lw.rebar.ironfurnaces.furnace.FurnaceTier.RAINBOW to IronFurnaceKeys.RAINBOW_FURNACE,
        )

    fun keyFor(tier: top.mc506lw.rebar.ironfurnaces.furnace.FurnaceTier): NamespacedKey? =
        keysByTier[tier]

    fun initialize() {
        for (definition in definitions) {
            RebarBlock.register(definition.key, definition.placementMaterial, definition.implementation)
        }
        // 无线能量加热器 —— **它不是熔炉**，所以不进 `definitions`：
        // `keys` / `implementationClasses` 是给"熔炉"用的（掉落物识别、扳手登记），
        // 混进来会让加热器被当成熔炉。
        RebarBlock.register(
            IronFurnaceKeys.WIRELESS_HEATER,
            Material.IRON_BLOCK,
            top.mc506lw.rebar.ironfurnaces.furnace.WirelessEnergyHeater::class.java
        )
    }
}
