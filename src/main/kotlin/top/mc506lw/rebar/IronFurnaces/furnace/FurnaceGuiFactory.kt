package top.mc506lw.rebar.ironfurnaces.furnace

import io.github.pylonmc.rebar.i18n.RebarArgument
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder
import io.github.pylonmc.rebar.util.gui.GuiItems
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import top.mc506lw.rebar.sidedio.gui.SideConfigGui
import xyz.xenondevs.invui.Click
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.inventory.VirtualInventory
import xyz.xenondevs.invui.item.AbstractItem
import xyz.xenondevs.invui.item.Item
import xyz.xenondevs.invui.item.ItemProvider
import xyz.xenondevs.invui.window.Window

class FurnaceGuiFactory(
    private val furnace: AbstractIronFurnace,
    private val inputInv: VirtualInventory,
    private val outputInv: VirtualInventory,
    private val fuelInv: VirtualInventory,
    private val fuelSystem: FurnaceFuelSystem,
    private val upgradeRedSlot: VirtualInventory,
    private val upgradeGreenSlot: VirtualInventory,
    private val upgradeBlueSlot: VirtualInventory
) {
    private val upgradeButton = navigationItem(
        ItemStackBuilder.of(Material.ANVIL)
            .name(Component.translatable("ironfurnaces.gui.upgrade.name"))
            .lore(Component.translatable("ironfurnaces.gui.upgrade.lore"))
    ) { player -> openUpgradeGui(player) }

    private val backButton = navigationItem(
        ItemStackBuilder.of(Material.ARROW)
            .name(Component.translatable("ironfurnaces.gui.back_button.name"))
    ) { player -> openMainGui(player) }

    private val redInfo = ItemStackBuilder.of(Material.RED_STAINED_GLASS_PANE)
        .name(Component.translatable("ironfurnaces.gui.upgrade_red.name"))
        .lore(Component.translatable("ironfurnaces.gui.upgrade_red.lore"))

    private val greenInfo = ItemStackBuilder.of(Material.LIME_STAINED_GLASS_PANE)
        .name(Component.translatable("ironfurnaces.gui.upgrade_green.name"))
        .lore(Component.translatable("ironfurnaces.gui.upgrade_green.lore"))

    private val blueInfo = ItemStackBuilder.of(Material.BLUE_STAINED_GLASS_PANE)
        .name(Component.translatable("ironfurnaces.gui.upgrade_blue.name"))
        .lore(Component.translatable("ironfurnaces.gui.upgrade_blue.lore"))

    /**
     * **autoSplit**：工厂模式下把进来的物品**摊平**到所有可用槽位，
     * 让多个槽真正并行烧 —— 不然一摞矿石全挤在第 0 格，其它槽空转。
     *
     * ⚠️ 这是**熔炉级**开关，**不是面设置** —— 所以它是熔炉 GUI 上的按钮，
     * 值存在熔炉自己的 PDC 里（[AbstractIronFurnace.autoSplit]），
     * **不走** Sided-IO 的面配置面板。
     */
    private val autoSplitButton: Item = object : AbstractItem() {
        override fun getItemProvider(viewer: Player): ItemProvider =
            ItemStackBuilder.of(if (furnace.autoSplit) Material.HOPPER else Material.BARREL)
                .name(Component.translatable("ironfurnaces.gui.auto_split.name"))
                .lore(
                    Component.translatable(
                        "ironfurnaces.gui.auto_split.lore",
                        // 占位符必须用 RebarArgument 传（Rebar 的翻译器只替换这个类型的参数）
                        RebarArgument.of(
                            "state",
                            Component.translatable(
                                if (furnace.autoSplit) "ironfurnaces.gui.state_on"
                                else "ironfurnaces.gui.state_off"
                            )
                        )
                    ),
                    Component.translatable("ironfurnaces.gui.auto_split.hint")
                )

        override fun handleClick(clickType: ClickType, player: Player, click: Click) {
            if (!clickType.isLeftClick) return
            furnace.toggleAutoSplit()
            player.sendActionBar(
                Component.translatable(
                    "ironfurnaces.message.auto_split",
                    RebarArgument.of(
                        "state",
                        Component.translatable(
                            if (furnace.autoSplit) "ironfurnaces.gui.state_on"
                            else "ironfurnaces.gui.state_off"
                        )
                    )
                )
            )
        }
    }

    /** 底行按钮：打开 Sided-IO 的面配置面板（六个面并排，一眼看完还能挨个点）。 */
    private val sideConfigButton = navigationItem(
        ItemStackBuilder.of(Material.COMPARATOR)
            .name(Component.translatable("ironfurnaces.gui.side_config.name"))
            .lore(Component.translatable("ironfurnaces.gui.side_config.lore"))
    ) { player -> openSideConfigGui(player) }

    /** Bottom-right button that cycles the redstone gate, like the mod's redstone setting. */
    private val redstoneButton: Item = object : AbstractItem() {
        override fun getItemProvider(viewer: Player): ItemProvider {
            val mode = Component.translatable(furnace.currentRedstoneMode.translationKey)
            return ItemStackBuilder.of(Material.REDSTONE_TORCH)
                .name(Component.translatable("ironfurnaces.gui.redstone.name"))
                .lore(
                    // 占位符必须用 RebarArgument 传：Rebar 的翻译器只替换 RebarArgument 类型的参数，
                    // 直接塞 Component 的话 %mode% 会原样留在界面上
                    Component.translatable(
                        "ironfurnaces.gui.redstone.lore",
                        RebarArgument.of("mode", mode)
                    ),
                    Component.translatable("ironfurnaces.gui.redstone.hint")
                )
        }

        override fun handleClick(clickType: ClickType, player: Player, click: Click) {
            if (!clickType.isLeftClick) return

            val mode = furnace.cycleRedstoneMode()
            player.sendMessage(
                Component.translatable(
                    "ironfurnaces.message.redstone_mode",
                    RebarArgument.of("mode", Component.translatable(mode.translationKey))
                )
            )
            notifyWindows()
        }
    }

    fun createMainGui(): Gui {
        // 发电机模式：模组会把熔炼输入、输出、普通燃料槽全部禁用（isActive() = isFurnace()），
        // 只留一个专用的发电机燃料槽（模组里是 slot 6，位于界面中间）。
        // 工厂布局在拆掉升级、槽里还留着东西时也会保留，避免物品被"锁"在看不见的槽里。
        return when {
            furnace.inGeneratorMode -> createGeneratorGui()
            !furnace.isFuelSlotEnabled || furnace.displayedSlots > 1 -> createFactoryGui()
            else -> createFuelGui()
        }
    }

    /**
     * 发电机布局：中间一个燃料槽 + 燃料进度。
     *
     * 如果拆掉工厂升级时输入/输出槽里还留着东西，这里会把这两个槽也显示出来，方便取回；
     * 没有残留时它们是背景，和模组"直接隐藏"的效果一致。
     */
    private fun createGeneratorGui(): Gui {
        val leftovers = furnace.displayedInputSlots > 1 || furnace.displayedOutputSlots > 1

        return Gui.builder()
            .setStructure(
                "# # # # # # # A U",
                if (leftovers) "# # i # # # # # #" else "# # # # # # # # #",
                "# # # # > f # # #",
                if (leftovers) "# # # # # # o # #" else "# # # # # # # # #",
                "# # # # # # # S R"
            )
            .addIngredient('#', GuiItems.background())
            .addIngredient('U', upgradeButton)
            .addIngredient('A', autoSplitButton)
            .addIngredient('S', sideConfigButton)
            .addIngredient('R', redstoneButton)
            .addIngredient('>', fuelSystem.fuelProgressItem)
            .addIngredient('f', fuelInv)
            .addIngredient('i', inputInv)
            .addIngredient('o', outputInv)
            .build()
    }

    /**
     * Factory layout: a row of inputs, a row of progress bars and a row of outputs. A furnace only
     * unlocks 2, 4 or 6 of them depending on its tier, so the used columns are centred and the rest
     * stay as background.
     */
    private fun createFactoryGui(): Gui {
        val slots = furnace.displayedSlots.coerceIn(1, MAX_FACTORY_SLOTS)
        val start = 1 + (MAX_FACTORY_SLOTS - slots) / 2
        val end = start + slots

        fun row(token: (Int) -> String): String = (0 until 9).joinToString(" ") { column ->
            if (column in start until end) token(column - start) else "#"
        }

        val builder = Gui.builder()
            .setStructure(
                "# # # # # # # A U",
                row { "i" },
                row { index -> ('1' + index).toString() },
                row { "o" },
                "# # # # # # # S R"
            )
            .addIngredient('#', GuiItems.background())
            .addIngredient('U', upgradeButton)
            .addIngredient('A', autoSplitButton)
            .addIngredient('S', sideConfigButton)
            .addIngredient('R', redstoneButton)
            .addIngredient('i', inputInv)
            .addIngredient('o', outputInv)

        for (index in 0 until slots) {
            builder.addIngredient('1' + index, furnace.progressItem(index))
        }

        return builder.build()
    }

    private fun createFuelGui(): Gui = Gui.builder()
        .setStructure(
            "# # # # # # # A U",
            "# # i # # # # # #",
            "# # > # S # o # #",
            "# # f # # # # # #",
            "# # # # # # # s R"
        )
        .addIngredient('#', GuiItems.background())
        .addIngredient('U', upgradeButton)
            .addIngredient('A', autoSplitButton)
        .addIngredient('s', sideConfigButton)
        .addIngredient('R', redstoneButton)
        .addIngredient('i', inputInv)
        .addIngredient('>', fuelSystem.fuelProgressItem)
        .addIngredient('S', furnace.progressItem(0))
        .addIngredient('o', outputInv)
        .addIngredient('f', fuelInv)
        .build()

    fun createUpgradeGui(): Gui = Gui.builder()
        .setStructure(
            "# # # # # # # # <",
            "# # R # G # L # #",
            "# # r # g # b # #",
            "# # # # # # # # #"
        )
        .addIngredient('#', GuiItems.background())
        .addIngredient('<', backButton)
        .addIngredient('R', redInfo)
        .addIngredient('G', greenInfo)
        .addIngredient('L', blueInfo)
        .addIngredient('r', upgradeRedSlot)
        .addIngredient('g', upgradeGreenSlot)
        .addIngredient('b', upgradeBlueSlot)
        .build()

    private fun openMainGui(player: Player) {
        Window.builder()
            .setUpperGui(createMainGui())
            .setTitle(furnace.getGuiTitle())
            .setViewer(player)
            .build()
            .open()
    }

    private fun openUpgradeGui(player: Player) {
        Window.builder()
            .setUpperGui(createUpgradeGui())
            .setTitle(furnace.getGuiTitle())
            .setViewer(player)
            .build()
            .open()
    }

    /**
     * 打开 Sided-IO 的面配置面板。
     *
     * ⚠️ 直接开新窗口会**顶掉**当前的熔炉界面（InvUI 一个玩家同时只有一个窗口），
     * 所以要把"回主界面"这条路一起传进去 —— 面板会在右上角画一个「返回」，
     * 点了就回到 [openMainGui]（用户实测报过"页面上没有返回按钮"）。
     *
     * 面板改完面模式后，Sided-IO 自己会调 `refreshGroups` 重算物流组
     * （见 `SideConfigGui` 的点击处理），熔炉这边不用额外做什么。
     */
    private fun openSideConfigGui(player: Player) {
        SideConfigGui.open(player, furnace.block) { back -> openMainGui(back) }
    }

    private fun navigationItem(provider: ItemProvider, navigate: (Player) -> Unit): Item =
        object : AbstractItem() {
            override fun getItemProvider(viewer: Player): ItemProvider = provider

            override fun handleClick(clickType: ClickType, player: Player, click: Click) {
                if (clickType.isLeftClick) navigate(player)
            }
        }

    private companion object {
        /** Input/output slots a factory furnace can unlock; see [FurnaceTier.factorySlots]. */
        private const val MAX_FACTORY_SLOTS = 6
    }
}
