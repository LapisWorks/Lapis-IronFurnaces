package top.mc506lw.rebar.ironfurnaces.furnace

import io.github.pylonmc.rebar.i18n.RebarArgument
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder
import io.github.pylonmc.rebar.util.gui.GuiItems
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
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
        // The factory layout is also kept while leftovers sit in the extra slots, so nothing gets
        // trapped when the upgrade is pulled back out.
        return if (!furnace.isFuelSlotEnabled || furnace.displayedSlots > 1) {
            createFactoryGui()
        } else {
            createFuelGui()
        }
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
                "# # # # # # # # U",
                row { "i" },
                row { index -> ('1' + index).toString() },
                row { "o" },
                "# # # # # # # # R"
            )
            .addIngredient('#', GuiItems.background())
            .addIngredient('U', upgradeButton)
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
            "# # # # # # # # U",
            "# # i # # # # # #",
            "# # > # S # o # #",
            "# # f # # # # # #",
            "# # # # # # # # R"
        )
        .addIngredient('#', GuiItems.background())
        .addIngredient('U', upgradeButton)
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
