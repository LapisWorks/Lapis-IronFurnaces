package top.mc506lw.rebar.ironfurnaces

import io.github.pylonmc.rebar.item.RebarItem
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import top.mc506lw.rebar.ironfurnaces.furnace.FurnaceTier
import top.mc506lw.rebar.ironfurnaces.items.*

object IronFurnaceItems {

    val COPPER_FURNACE_ITEM = createFurnaceItem(Material.FURNACE, IronFurnaceKeys.COPPER_FURNACE, FurnaceTier.COPPER)

    val IRON_FURNACE_ITEM = createFurnaceItem(Material.FURNACE, IronFurnaceKeys.IRON_FURNACE, FurnaceTier.IRON)

    val GOLD_FURNACE_ITEM = createFurnaceItem(Material.FURNACE, IronFurnaceKeys.GOLD_FURNACE, FurnaceTier.GOLD)

    val DIAMOND_FURNACE_ITEM = createFurnaceItem(Material.FURNACE, IronFurnaceKeys.DIAMOND_FURNACE, FurnaceTier.DIAMOND)

    val EMERALD_FURNACE_ITEM = createFurnaceItem(Material.FURNACE, IronFurnaceKeys.EMERALD_FURNACE, FurnaceTier.EMERALD)

    val CRYSTAL_FURNACE_ITEM = createFurnaceItem(Material.FURNACE, IronFurnaceKeys.CRYSTAL_FURNACE, FurnaceTier.CRYSTAL)

    val OBSIDIAN_FURNACE_ITEM = createFurnaceItem(Material.FURNACE, IronFurnaceKeys.OBSIDIAN_FURNACE, FurnaceTier.OBSIDIAN)

    val NETHERITE_FURNACE_ITEM = createFurnaceItem(Material.FURNACE, IronFurnaceKeys.NETHERITE_FURNACE, FurnaceTier.NETHERITE)

    val RAINBOW_FURNACE_ITEM = createFurnaceItem(Material.BEACON, IronFurnaceKeys.RAINBOW_FURNACE, FurnaceTier.RAINBOW)

    val RAINBOW_CORE = createItem(Material.NETHER_STAR, IronFurnaceKeys.RAINBOW_CORE)

    /** 彩虹镀层（对齐 26.3 的 `rainbow_plating`；以前叫 `rainbow_shell`）。 */
    val RAINBOW_PLATING = createItem(Material.PRISMARINE_SHARD, IronFurnaceKeys.RAINBOW_PLATING)

    /**
     * 彩虹煤 —— 高效燃料。
     *
     * ⚠️ **最大堆叠 = 1**（对齐 26.3 的 `stacksTo(1)`）：它是"一块顶十块煤"的
     * 高价值燃料，能堆 64 的话一格就是 640 块煤的量，手滑丢掉/烧错都很难受。
     * 这和扳手那件事是同一类问题（可堆叠物品当工具/高价值物用）。
     */
    val RAINBOW_COAL = ItemStackBuilder.rebar(Material.COAL, IronFurnaceKeys.RAINBOW_COAL)
        .editMeta { meta ->
            meta.setMaxStackSize(1)
            // 耐久型燃料：`max_damage` 让它可以"用坏"，而不是一烧就没。
            // ⚠️ `setMaxDamage` / `setDamage` 在 **`Damageable`** 上，不在 `ItemMeta` 上。
            (meta as Damageable).apply {
                setMaxDamage(RAINBOW_COAL_DURABILITY)
                damage = 0
            }
        }
        .build()

    val BLAST_UPGRADE = createItem(Material.BLAST_FURNACE, IronFurnaceKeys.BLAST_UPGRADE)

    val SMOKER_UPGRADE = createItem(Material.SMOKER, IronFurnaceKeys.SMOKER_UPGRADE)

    val SPEED_UPGRADE = createItem(Material.SUGAR, IronFurnaceKeys.SPEED_UPGRADE)

    val FUEL_UPGRADE = createItem(Material.COAL_BLOCK, IronFurnaceKeys.FUEL_UPGRADE)

    val INDUSTRIAL_UPGRADE = createItem(Material.IRON_BLOCK, IronFurnaceKeys.INDUSTRIAL_UPGRADE)

    val GENERATOR_UPGRADE = createItem(Material.REDSTONE_BLOCK, IronFurnaceKeys.GENERATOR_UPGRADE)

    /**
     * 这件物品是不是我们的某台熔炉。
     *
     * 给"拆机保留设置"用 —— 拆掉熔炉时要在掉落物里认出**哪一件是熔炉**，
     * 才能把配置写进它。用 Rebar 物品键而不是材质（材质是 `FURNACE` / `BEACON`，撞得起）。
     */
    fun isFurnaceItem(stack: ItemStack): Boolean =
        io.github.pylonmc.rebar.item.RebarItemSchema.fromStack(stack)?.key in IronFurnaceBlocks.keys

    /**
     * **设置复制器** —— 右键一台熔炉复制它的配置（面模式 + 自动输入输出 + 红石），
     * 潜行右键另一台粘贴。复刻 26.3 的 `item_copy`。
     *
     * 配置就存在**这件物品自己**的 `IronFurnaceKeys.SAVED_SETTINGS` 里，
     * 和"拆机保留设置"用同一套文本格式（见 [top.mc506lw.rebar.ironfurnaces.furnace.FurnaceSettingsCodec]）。
     *
     * ⚠️ **最大堆叠 = 1**：它是**工具**（26.3 也是 `stacksTo(1)`），
     * 能堆的话玩家分不清"哪一本记的是哪台机器的配置"。
     */
    val COPY_TOOL = ItemStackBuilder.rebar(Material.BOOK, IronFurnaceKeys.COPY_TOOL)
        .editMeta { it.setMaxStackSize(1) }
        .build()

    /**
     * 这件物品是不是**设置复制器**。
     *
     * 用 Rebar 物品键而不是材质（材质是 `BOOK`，撞得起）。
     */
    fun isCopyTool(stack: ItemStack): Boolean =
        io.github.pylonmc.rebar.item.RebarItemSchema.fromStack(stack)?.key == IronFurnaceKeys.COPY_TOOL

    /**
     * **等级升级**（10 张）—— 右键一台熔炉把它升到下一级，保留里面的东西和设置。
     *
     * ⚠️ 十个物品**共用一个实现类** [TierUpgradeItem] —— 具体是哪一张由物品自己的
     * Rebar 键决定（见 `TierUpgrade.of`），没必要写十个空壳类。
     *
     * 不做银（Pylon 没有银），也不做 ATM 三档。
     */
    internal val TIER_UPGRADES: Map<top.mc506lw.rebar.ironfurnaces.furnace.TierUpgrade, ItemStack> =
        top.mc506lw.rebar.ironfurnaces.furnace.TierUpgrade.entries.associateWith { upgrade ->
            createItem(upgrade.material, upgrade.key)
        }

    /** **无线能量加热器**方块物品。 */
    val WIRELESS_HEATER = createItem(Material.IRON_BLOCK, IronFurnaceKeys.WIRELESS_HEATER)

    /**
     * **加热棒** —— 在加热器方块上右键刻坐标，放进熔炉燃料槽当燃料用。
     *
     * ⚠️ **最大堆叠 = 1**：它是**钥匙**（不会消耗，拿回来还能插别的熔炉），
     * 堆起来的话分不清哪根绑的是哪台加热器 —— 26.3 也是 `stacksTo(1)`。
     */
    val ITEM_HEATER = ItemStackBuilder.rebar(Material.BLAZE_ROD, IronFurnaceKeys.ITEM_HEATER)
        .editMeta { it.setMaxStackSize(1) }
        .build()

    /**
     * 彩虹煤的总耐久 —— **每烧一次扣 1 点**，扣完才消失。
     *
     * 对齐 26.3 的 `ItemRainbowCoal extends Item` + `durability(5120)`。
     * 它不是"一次性燃料"，是**耐久的打火石**：一块能烧 5120 次。
     */
    internal const val RAINBOW_COAL_DURABILITY = 5120

    /**
     * 彩虹煤"一次"提供的燃烧时间（tick）—— 对齐 26.3 注册时的 `CookingFuel(200)`。
     *
     * ⚠️ 200 tick 只是**一次**的量（正好烧一件物品）。总账是
     * `5120 × 200 = 1,024,000` tick，相当于 640 块煤 —— 别把它当"一块煤的 10 倍"。
     */
    internal const val RAINBOW_COAL_BURN_TICKS = 200

    /** 彩虹煤还剩多少耐久（没有 `max_damage` 组件时当作满耐久）。 */
    fun rainbowCoalRemaining(stack: ItemStack): Int {
        val meta = stack.itemMeta as? Damageable ?: return RAINBOW_COAL_DURABILITY
        val max = (meta.maxDamage ?: 0).takeIf { it > 0 } ?: RAINBOW_COAL_DURABILITY
        return (max - meta.damage).coerceAtLeast(0)
    }

    /**
     * 给彩虹煤扣 1 点耐久。
     *
     * @return `true` = 这点耐久扣完了，**物品该消失**（对齐 26.3 的
     *         `getCraftingRemainder` 在 `damage + 1 >= 5120` 时返回 `null`）
     */
    fun damageRainbowCoal(stack: ItemStack): Boolean {
        val meta = stack.itemMeta as? Damageable ?: return false
        val max = (meta.maxDamage ?: 0).takeIf { it > 0 } ?: RAINBOW_COAL_DURABILITY
        val next = meta.damage + 1
        if (next >= max) return true
        meta.damage = next
        stack.itemMeta = meta
        return false
    }

    /**
     * 这件物品是不是彩虹煤。
     *
     * ⚠️ 必须按 Rebar 物品键认，**不能看材质** —— 彩虹煤的底块就是 `Material.COAL`，
     * 原版的燃料表只认材质，不特判的话它会被当成普通煤烧（200 tick）。
     */
    fun isRainbowCoal(stack: ItemStack): Boolean =
        io.github.pylonmc.rebar.item.RebarItemSchema.fromStack(stack)?.key == IronFurnaceKeys.RAINBOW_COAL

    fun initialize() {        RebarItem.register(CopperFurnaceItem::class.java, COPPER_FURNACE_ITEM, IronFurnaceKeys.COPPER_FURNACE)
        RebarItem.register(IronFurnaceItem::class.java, IRON_FURNACE_ITEM, IronFurnaceKeys.IRON_FURNACE)
        RebarItem.register(GoldFurnaceItem::class.java, GOLD_FURNACE_ITEM, IronFurnaceKeys.GOLD_FURNACE)
        RebarItem.register(DiamondFurnaceItem::class.java, DIAMOND_FURNACE_ITEM, IronFurnaceKeys.DIAMOND_FURNACE)
        RebarItem.register(EmeraldFurnaceItem::class.java, EMERALD_FURNACE_ITEM, IronFurnaceKeys.EMERALD_FURNACE)
        RebarItem.register(CrystalFurnaceItem::class.java, CRYSTAL_FURNACE_ITEM, IronFurnaceKeys.CRYSTAL_FURNACE)
        RebarItem.register(ObsidianFurnaceItem::class.java, OBSIDIAN_FURNACE_ITEM, IronFurnaceKeys.OBSIDIAN_FURNACE)
        RebarItem.register(NetheriteFurnaceItem::class.java, NETHERITE_FURNACE_ITEM, IronFurnaceKeys.NETHERITE_FURNACE)
        RebarItem.register(RainbowFurnaceItem::class.java, RAINBOW_FURNACE_ITEM, IronFurnaceKeys.RAINBOW_FURNACE)

        RebarItem.register(RainbowCoreItem::class.java, RAINBOW_CORE)
        RebarItem.register(RainbowPlatingItem::class.java, RAINBOW_PLATING)
        RebarItem.register(RainbowCoalItem::class.java, RAINBOW_COAL)

        RebarItem.register(BlastUpgradeItem::class.java, BLAST_UPGRADE)
        RebarItem.register(SmokerUpgradeItem::class.java, SMOKER_UPGRADE)
        RebarItem.register(SpeedUpgradeItem::class.java, SPEED_UPGRADE)
        RebarItem.register(FuelUpgradeItem::class.java, FUEL_UPGRADE)
        RebarItem.register(IndustrialUpgradeItem::class.java, INDUSTRIAL_UPGRADE)
        RebarItem.register(GeneratorUpgradeItem::class.java, GENERATOR_UPGRADE)

        RebarItem.register(FurnaceCopyItem::class.java, COPY_TOOL, IronFurnaceKeys.COPY_TOOL)

        // 10 张等级升级共用一个类（具体是哪张由物品键决定）
        TIER_UPGRADES.forEach { (upgrade, stack) ->
            RebarItem.register(TierUpgradeItem::class.java, stack, upgrade.key)
        }

        // 无线供能：加热器方块物品 + 加热棒
        RebarItem.register(WirelessHeaterItem::class.java, WIRELESS_HEATER, IronFurnaceKeys.WIRELESS_HEATER)
        RebarItem.register(HeaterRodItem::class.java, ITEM_HEATER, IronFurnaceKeys.ITEM_HEATER)
    }

    private fun createItem(material: Material, key: org.bukkit.NamespacedKey): ItemStack =
        ItemStackBuilder.rebar(material, key).build()

    /**
     * 熔炉物品 —— 比普通物品多一行**烧炼时间**，对齐 26.3 的
     * `tooltip.ironfurnaces.cooktime (N)`（N = 一件 200 tick 的配方要多少 tick）。
     *
     * 玩家挑熔炉时最关心的就是这一条，放在物品上省得去查表。
     */
    private fun createFurnaceItem(
        material: Material,
        key: org.bukkit.NamespacedKey,
        tier: FurnaceTier
    ): ItemStack = ItemStackBuilder.rebar(material, key)
        // ⚠️ `lore()` 是**追加**（Rebar 会先读现有的 LORE 再加行），
        // 所以不会把 `rebar()` 设的默认说明冲掉。
        // 占位符必须走 RebarArgument —— Rebar 的翻译器只替换 RebarArgument 类型的参数。
        .lore(
            net.kyori.adventure.text.Component.translatable(
                "ironfurnaces.tooltip.cooktime",
                io.github.pylonmc.rebar.i18n.RebarArgument.of("ticks", tier.smeltTimePerItem)
            )
        )
        .build()
}
