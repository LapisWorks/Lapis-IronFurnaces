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
