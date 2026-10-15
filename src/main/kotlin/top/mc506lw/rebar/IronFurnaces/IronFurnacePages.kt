package top.mc506lw.rebar.ironfurnaces

import io.github.pylonmc.rebar.content.guide.RebarGuide
import io.github.pylonmc.rebar.guide.pages.base.SimpleStaticGuidePage

object IronFurnacePages {
    val PAGE = SimpleStaticGuidePage(IronFurnaceKeys.key("ironfurnaces"))

    private val pageItems by lazy(LazyThreadSafetyMode.NONE) {
        listOf(
            IronFurnaceItems.COPPER_FURNACE_ITEM,
            IronFurnaceItems.IRON_FURNACE_ITEM,
            IronFurnaceItems.GOLD_FURNACE_ITEM,
            IronFurnaceItems.DIAMOND_FURNACE_ITEM,
            IronFurnaceItems.EMERALD_FURNACE_ITEM,
            IronFurnaceItems.CRYSTAL_FURNACE_ITEM,
            IronFurnaceItems.OBSIDIAN_FURNACE_ITEM,
            IronFurnaceItems.NETHERITE_FURNACE_ITEM,
            IronFurnaceItems.RAINBOW_FURNACE_ITEM,
            IronFurnaceItems.BLAST_UPGRADE,
            IronFurnaceItems.SMOKER_UPGRADE,
            IronFurnaceItems.SPEED_UPGRADE,
            IronFurnaceItems.FUEL_UPGRADE,
            IronFurnaceItems.INDUSTRIAL_UPGRADE,
            IronFurnaceItems.GENERATOR_UPGRADE,
            IronFurnaceItems.COPY_TOOL,
            IronFurnaceItems.RAINBOW_CORE,
            IronFurnaceItems.RAINBOW_PLATING,
            IronFurnaceItems.RAINBOW_COAL
        ) + IronFurnaceItems.TIER_UPGRADES.values + listOf(
            IronFurnaceItems.WIRELESS_HEATER,
            IronFurnaceItems.ITEM_HEATER
        )
    }

    fun initialise() {
        pageItems.forEach(PAGE::addItem)
        RebarGuide.rootPage.addPage(IronFurnaceItems.IRON_FURNACE_ITEM, PAGE)
    }
}
