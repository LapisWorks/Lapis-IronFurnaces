package top.mc506lw.rebar.ironfurnaces

import org.bukkit.NamespacedKey

object IronFurnaceKeys {
    private const val NAMESPACE = "ironfurnaces"

    fun key(name: String) = NamespacedKey(NAMESPACE, name)

    val RAINBOW_CORE = key("rainbow_core")

    /**
     * 彩虹镀层。
     *
     * ⚠️ 名字对齐 26.3（那边是 `rainbow_core` + **`rainbow_plating`**）。
     * 我们以前叫 `rainbow_shell`，还多一个 `rainbow_connector` —— 后者已经删掉
     * （26.3 没有这个物品，而我们没有任何配方用到它）。
     */
    val RAINBOW_PLATING = key("rainbow_plating")

    val RAINBOW_COAL = key("rainbow_coal")

    val COPPER_FURNACE = key("copper_furnace")
    val IRON_FURNACE = key("iron_furnace")
    val GOLD_FURNACE = key("gold_furnace")
    val DIAMOND_FURNACE = key("diamond_furnace")
    val EMERALD_FURNACE = key("emerald_furnace")
    val CRYSTAL_FURNACE = key("crystal_furnace")
    val OBSIDIAN_FURNACE = key("obsidian_furnace")
    val NETHERITE_FURNACE = key("netherite_furnace")
    val RAINBOW_FURNACE = key("rainbow_furnace")

    val BLAST_UPGRADE = key("blast_upgrade")
    val SMOKER_UPGRADE = key("smoker_upgrade")
    val SPEED_UPGRADE = key("speed_upgrade")
    val FUEL_UPGRADE = key("fuel_upgrade")
    val INDUSTRIAL_UPGRADE = key("industrial_upgrade")
    val GENERATOR_UPGRADE = key("generator_upgrade")

    val FUEL_TIME = key("fuel_time")
    val FUEL_REMAINING = key("fuel_remaining")
    val FUEL_TYPE = key("fuel_type")
    val FUEL_CONSUMPTION_FRACTION = key("fuel_consumption_fraction")
    val ENERGY_CURRENT = key("energy_current")
    val ENERGY_TOTAL_PRODUCED = key("energy_total_produced")
    val RAINBOW_COLOR_INDEX = key("rainbow_color_index")
    val RAINBOW_COLOR_TICK = key("rainbow_color_tick")

    /** Per-slot smelting progress of the factory slots, mirroring the mod's FactoryCookTime arrays. */
    val TASK_TICKS_TOTAL = key("task_ticks_total")
    val TASK_TICKS_REMAINING = key("task_ticks_remaining")

    /** The classic mod's redstone setting (ignored / stop on signal / run only on signal). */
    val REDSTONE_MODE = key("redstone_mode")

    /**
     * 放置这台熔炉的玩家（UUID 字符串）—— 彩虹发电要按玩家找名下的熔炉。
     *
     * 存在**方块**上（`AbstractIronFurnace.write`），和 [PLAYER_FURNACES]（存在玩家上）配对：
     * 方块这边回答"这是谁的"，玩家那边回答"我有哪些"。
     */
    val OWNER = key("owner")

    /** 玩家名下的熔炉坐标列表（`"<世界>:<x>:<y>:<z>"`），存在**玩家 PDC** 上。 */
    val PLAYER_FURNACES = key("player_furnaces")

    /**
     * **拆机保留设置** —— 拆掉熔炉时把它的配置写进**掉落物**的这个键里，
     * 放回去时读回来。复刻 26.3 的 `FURNACE_SETTINGS` 数据组件。
     *
     * 存的是一整段文本（每行 `键=值`）而不是嵌套容器 ——
     * PDC 的键必须匹配 `[a-z0-9_-./]`，而我们的逻辑键是 `NORTH.sidedio:item`
     * （有大写和冒号），塞进嵌套容器要额外编码。文本值没有这个限制。
     */
    val SAVED_SETTINGS = key("saved_settings")

    /**
     * **设置复制器** —— 右键一台熔炉复制它的配置，潜行右键另一台粘贴。
     * 复刻 26.3 的 `item_copy`。
     */
    val COPY_TOOL = key("copy_tool")

    /**
     * **autoSplit** —— 熔炉级开关：工厂模式下把进来的物品**摊平**到所有可用槽位。
     *
     * ⚠️ 这是**熔炉自己**的设置（熔炉 GUI 上的按钮），**不是面设置** ——
     * 所以存在方块 PDC 上，不走 Sided-IO 的面配置面板。
     */
    val AUTO_SPLIT = key("auto_split")

    // ------------------------------------------------------------ 等级升级
    //
    // 复刻 26.3 的 `upgrade_*`：右键一台熔炉把它升到下一级，**保留里面的东西和设置**。
    // 命名照抄 mod（`upgrade_iron2` 是"从铜升到铁"，`upgrade_iron` 是"从原版熔炉升到铁"）。
    // ⚠️ 26.3 的 `upgrade_gold2` 是"银 → 金"，我们**没有银**所以不做。

    /** 原版熔炉 → 铜熔炉。 */
    val UPGRADE_COPPER = key("upgrade_copper")

    /** 原版熔炉 → 铁熔炉。 */
    val UPGRADE_IRON = key("upgrade_iron")

    /** 铜 → 铁。 */
    val UPGRADE_IRON2 = key("upgrade_iron2")

    /** 铁 → 金。 */
    val UPGRADE_GOLD = key("upgrade_gold")

    /** 金 → 钻石。 */
    val UPGRADE_DIAMOND = key("upgrade_diamond")

    /** 钻石 → 水晶。 */
    val UPGRADE_CRYSTAL = key("upgrade_crystal")

    /** 钻石 → 绿宝石。 */
    val UPGRADE_EMERALD = key("upgrade_emerald")

    /** 绿宝石 → 黑曜石。 */
    val UPGRADE_OBSIDIAN = key("upgrade_obsidian")

    /** 水晶 → 黑曜石。 */
    val UPGRADE_OBSIDIAN2 = key("upgrade_obsidian2")

    /** 黑曜石 → 下界合金。 */
    val UPGRADE_NETHERITE = key("upgrade_netherite")

    // ------------------------------------------------------------ 无线供能

    /** **无线能量加热器**方块 —— 1,000,000 J 的缓冲，给绑定的加热棒供电。 */
    val WIRELESS_HEATER = key("wireless_heater")

    /**
     * **加热棒** —— 在加热器方块上右键会被"刻上"那个方块的坐标，
     * 之后放进熔炉的**燃料槽**就当燃料用（实际烧的是加热器里的电）。
     */
    val ITEM_HEATER = key("item_heater")

    /** 加热棒绑定的坐标（4 个键：世界 + xyz）。 */
    val HEATER_WORLD = key("heater_world")
    val HEATER_X = key("heater_x")
    val HEATER_Y = key("heater_y")
    val HEATER_Z = key("heater_z")

    /** 加热器方块缓冲里现在有多少电（J）。 */
    val HEATER_STORED = key("heater_stored")
}
