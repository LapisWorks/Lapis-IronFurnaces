package top.mc506lw.rebar.ironfurnaces

import io.github.pylonmc.rebar.recipe.RecipeType
import io.github.pylonmc.rebar.recipe.vanilla.ShapedRebarRecipe
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.inventory.recipe.CraftingBookCategory

object FurnaceRecipes {
    fun initialize() {
        register(
            IronFurnaceKeys.COPPER_FURNACE,
            IronFurnaceItems.COPPER_FURNACE_ITEM,
            arrayOf("CCC", "CFC", "CCC"),
            'C' to Material.COPPER_INGOT,
            'F' to Material.FURNACE
        )
        register(
            IronFurnaceKeys.IRON_FURNACE,
            IronFurnaceItems.IRON_FURNACE_ITEM,
            arrayOf("III", "ICI", "III"),
            'I' to Material.IRON_INGOT,
            'C' to IronFurnaceItems.COPPER_FURNACE_ITEM
        )
        register(
            IronFurnaceKeys.GOLD_FURNACE,
            IronFurnaceItems.GOLD_FURNACE_ITEM,
            arrayOf("GGG", "GIG", "GBG"),
            'G' to Material.GOLD_INGOT,
            'I' to IronFurnaceItems.IRON_FURNACE_ITEM,
            'B' to Material.GOLD_BLOCK
        )
        register(
            IronFurnaceKeys.DIAMOND_FURNACE,
            IronFurnaceItems.DIAMOND_FURNACE_ITEM,
            arrayOf("DDD", "GFG", "DDD"),
            'D' to Material.DIAMOND,
            'G' to Material.GLASS,
            'F' to IronFurnaceItems.GOLD_FURNACE_ITEM
        )
        register(
            IronFurnaceKeys.EMERALD_FURNACE,
            IronFurnaceItems.EMERALD_FURNACE_ITEM,
            arrayOf("EEE", "EDE", "EEE"),
            'E' to Material.EMERALD,
            'D' to IronFurnaceItems.DIAMOND_FURNACE_ITEM
        )
        register(
            IronFurnaceKeys.OBSIDIAN_FURNACE,
            IronFurnaceItems.OBSIDIAN_FURNACE_ITEM,
            arrayOf("OBO", "BFO", "OBO"),
            'O' to Material.OBSIDIAN,
            'B' to Material.BLAZE_ROD,
            'F' to IronFurnaceItems.DIAMOND_FURNACE_ITEM
        )
        register(
            IronFurnaceKeys.CRYSTAL_FURNACE,
            IronFurnaceItems.CRYSTAL_FURNACE_ITEM,
            arrayOf("GGG", "GDG", "GEG"),
            'G' to Material.GLASS,
            'D' to IronFurnaceItems.DIAMOND_FURNACE_ITEM,
            'E' to Material.ENDER_EYE
        )
        register(
            IronFurnaceKeys.NETHERITE_FURNACE,
            IronFurnaceItems.NETHERITE_FURNACE_ITEM,
            arrayOf("NMN", "MFM", "NSN"),
            'N' to Material.NETHERITE_INGOT,
            'M' to Material.MAGMA_CREAM,
            'F' to IronFurnaceItems.OBSIDIAN_FURNACE_ITEM,
            'S' to Material.SOUL_SAND
        )
        // 设置复制器：一本书 + 铁 + 红石 —— 便宜，因为它是**便利工具**，
        // 贵了玩家宁可手动配。
        register(
            IronFurnaceKeys.COPY_TOOL,
            IronFurnaceItems.COPY_TOOL,
            arrayOf("IRI", "RBR", "IRI"),
            'I' to Material.IRON_INGOT,
            'R' to Material.REDSTONE,
            'B' to Material.BOOK
        )
        // 等级升级：copper —— 一张升级 + 一圈材料围着"来源熔炉"
        register(
            IronFurnaceKeys.UPGRADE_COPPER,
            IronFurnaceItems.TIER_UPGRADES.getValue(
                top.mc506lw.rebar.ironfurnaces.furnace.TierUpgrade.COPPER
            ),
            arrayOf("MMM", "MFM", "MMM"),
            'M' to Material.COPPER_INGOT,
            'F' to Material.FURNACE
        )
        // 等级升级：iron —— 一张升级 + 一圈材料围着"来源熔炉"
        register(
            IronFurnaceKeys.UPGRADE_IRON,
            IronFurnaceItems.TIER_UPGRADES.getValue(
                top.mc506lw.rebar.ironfurnaces.furnace.TierUpgrade.IRON
            ),
            arrayOf("MMM", "MFM", "MMM"),
            'M' to Material.IRON_INGOT,
            'F' to Material.FURNACE
        )
        // 等级升级：iron2 —— 一张升级 + 一圈材料围着"来源熔炉"
        register(
            IronFurnaceKeys.UPGRADE_IRON2,
            IronFurnaceItems.TIER_UPGRADES.getValue(
                top.mc506lw.rebar.ironfurnaces.furnace.TierUpgrade.IRON2
            ),
            arrayOf("MMM", "MFM", "MMM"),
            'M' to Material.IRON_INGOT,
            'F' to IronFurnaceItems.COPPER_FURNACE_ITEM
        )
        // 等级升级：gold —— 一张升级 + 一圈材料围着"来源熔炉"
        register(
            IronFurnaceKeys.UPGRADE_GOLD,
            IronFurnaceItems.TIER_UPGRADES.getValue(
                top.mc506lw.rebar.ironfurnaces.furnace.TierUpgrade.GOLD
            ),
            arrayOf("MMM", "MFM", "MMM"),
            'M' to Material.GOLD_INGOT,
            'F' to IronFurnaceItems.IRON_FURNACE_ITEM
        )
        // 等级升级：diamond —— 一张升级 + 一圈材料围着"来源熔炉"
        register(
            IronFurnaceKeys.UPGRADE_DIAMOND,
            IronFurnaceItems.TIER_UPGRADES.getValue(
                top.mc506lw.rebar.ironfurnaces.furnace.TierUpgrade.DIAMOND
            ),
            arrayOf("MMM", "MFM", "MMM"),
            'M' to Material.DIAMOND,
            'F' to IronFurnaceItems.GOLD_FURNACE_ITEM
        )
        // 等级升级：crystal —— 一张升级 + 一圈材料围着"来源熔炉"
        register(
            IronFurnaceKeys.UPGRADE_CRYSTAL,
            IronFurnaceItems.TIER_UPGRADES.getValue(
                top.mc506lw.rebar.ironfurnaces.furnace.TierUpgrade.CRYSTAL
            ),
            arrayOf("MMM", "MFM", "MMM"),
            'M' to Material.PRISMARINE_SHARD,
            'F' to IronFurnaceItems.DIAMOND_FURNACE_ITEM
        )
        // 等级升级：emerald —— 一张升级 + 一圈材料围着"来源熔炉"
        register(
            IronFurnaceKeys.UPGRADE_EMERALD,
            IronFurnaceItems.TIER_UPGRADES.getValue(
                top.mc506lw.rebar.ironfurnaces.furnace.TierUpgrade.EMERALD
            ),
            arrayOf("MMM", "MFM", "MMM"),
            'M' to Material.EMERALD,
            'F' to IronFurnaceItems.DIAMOND_FURNACE_ITEM
        )
        // 等级升级：obsidian —— 一张升级 + 一圈材料围着"来源熔炉"
        register(
            IronFurnaceKeys.UPGRADE_OBSIDIAN,
            IronFurnaceItems.TIER_UPGRADES.getValue(
                top.mc506lw.rebar.ironfurnaces.furnace.TierUpgrade.OBSIDIAN
            ),
            arrayOf("MMM", "MFM", "MMM"),
            'M' to Material.OBSIDIAN,
            'F' to IronFurnaceItems.EMERALD_FURNACE_ITEM
        )
        // 等级升级：obsidian2 —— 一张升级 + 一圈材料围着"来源熔炉"
        register(
            IronFurnaceKeys.UPGRADE_OBSIDIAN2,
            IronFurnaceItems.TIER_UPGRADES.getValue(
                top.mc506lw.rebar.ironfurnaces.furnace.TierUpgrade.OBSIDIAN2
            ),
            arrayOf("MMM", "MFM", "MMM"),
            'M' to Material.OBSIDIAN,
            'F' to IronFurnaceItems.CRYSTAL_FURNACE_ITEM
        )
        // 等级升级：netherite —— 一张升级 + 一圈材料围着"来源熔炉"
        register(
            IronFurnaceKeys.UPGRADE_NETHERITE,
            IronFurnaceItems.TIER_UPGRADES.getValue(
                top.mc506lw.rebar.ironfurnaces.furnace.TierUpgrade.NETHERITE
            ),
            arrayOf("MMM", "MFM", "MMM"),
            'M' to Material.NETHERITE_INGOT,
            'F' to IronFurnaceItems.OBSIDIAN_FURNACE_ITEM
        )
        // 无线供能：加热器方块（1,000,000 J 的电池）+ 加热棒
        register(
            IronFurnaceKeys.WIRELESS_HEATER,
            IronFurnaceItems.WIRELESS_HEATER,
            arrayOf("IRI", "RDR", "IRI"),
            'I' to Material.IRON_INGOT,
            'R' to Material.REDSTONE_BLOCK,
            'D' to Material.DIAMOND_BLOCK
        )
        register(
            IronFurnaceKeys.ITEM_HEATER,
            IronFurnaceItems.ITEM_HEATER,
            arrayOf(" I ", "IRI", " I "),
            'I' to Material.IRON_INGOT,
            'R' to Material.BLAZE_ROD
        )
    }

    private fun register(
        key: NamespacedKey,
        result: ItemStack,
        shape: Array<String>,
        vararg ingredients: Pair<Char, Any>
    ) {
        val recipe = ShapedRecipe(key, result.clone())
        recipe.shape(*shape)
        for ((symbol, ingredient) in ingredients) {
            when (ingredient) {
                is Material -> recipe.setIngredient(symbol, ingredient)
                is ItemStack -> recipe.setIngredient(symbol, ingredient.clone())
                else -> error("Unsupported recipe ingredient: ${ingredient::class.qualifiedName}")
            }
        }
        recipe.setCategory(CraftingBookCategory.MISC)
        RecipeType.VANILLA_SHAPED.addRecipe(ShapedRebarRecipe.fromVanilla(recipe))
    }
}
