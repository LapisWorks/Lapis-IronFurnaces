package top.mc506lw.rebar.ironfurnaces.furnace

import io.github.pylonmc.rebar.recipe.vanilla.SmeltingRecipeType
import io.github.pylonmc.rebar.recipe.vanilla.SmeltingRebarRecipe
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import top.mc506lw.rebar.ironfurnaces.furnace.UpgradeEffectManager.RecipeCompatibility
import java.util.EnumMap

object RecipeDetector {
    private val compatibilityCache = EnumMap<Material, RecipeCompatibility>(Material::class.java)
    private val furnaceRecipeCache = EnumMap<Material, List<SmeltingRebarRecipe>>(Material::class.java)
    private var cachedRecipeCount = -1

    private val oreMaterials = setOf(
        Material.IRON_ORE,
        Material.GOLD_ORE,
        Material.DIAMOND_ORE,
        Material.EMERALD_ORE,
        Material.COAL_ORE,
        Material.LAPIS_ORE,
        Material.REDSTONE_ORE,
        Material.COPPER_ORE,
        Material.NETHER_GOLD_ORE,
        Material.NETHER_QUARTZ_ORE,
        Material.ANCIENT_DEBRIS,
        Material.RAW_IRON,
        Material.RAW_GOLD,
        Material.RAW_COPPER
    )

    private val foodMaterials = setOf(
        Material.BEEF,
        Material.PORKCHOP,
        Material.CHICKEN,
        Material.MUTTON,
        Material.RABBIT,
        Material.COD,
        Material.SALMON,
        Material.TROPICAL_FISH,
        Material.PUFFERFISH,
        Material.KELP,
        Material.POTATO
    )

    private val logMaterials = setOf(
        Material.OAK_LOG,
        Material.SPRUCE_LOG,
        Material.BIRCH_LOG,
        Material.JUNGLE_LOG,
        Material.ACACIA_LOG,
        Material.DARK_OAK_LOG,
        Material.MANGROVE_LOG,
        Material.CHERRY_LOG,
        Material.CRIMSON_STEM,
        Material.WARPED_STEM
    )

    fun detectRecipeCompatibility(stack: ItemStack?): RecipeCompatibility {
        if (stack == null || stack.isEmpty) return RecipeCompatibility.ANY
        return compatibilityCache.computeIfAbsent(stack.type, ::classifyMaterial)
    }

    fun matchingFurnaceRecipes(stack: ItemStack): List<SmeltingRebarRecipe> {
        val recipes = SmeltingRecipeType.recipes
        if (recipes.size != cachedRecipeCount) {
            furnaceRecipeCache.clear()
            cachedRecipeCount = recipes.size
        }

        return furnaceRecipeCache.computeIfAbsent(stack.type) { material ->
            recipes.filter { it.ingredient.matchesIgnoringAmount(stack) }
        }
    }

    fun canSmeltInBlastFurnace(stack: ItemStack): Boolean = when (detectRecipeCompatibility(stack)) {
        RecipeCompatibility.BLAST, RecipeCompatibility.ANY -> true
        else -> false
    }

    fun canSmeltInSmoker(stack: ItemStack): Boolean = when (detectRecipeCompatibility(stack)) {
        RecipeCompatibility.SMOKER, RecipeCompatibility.ANY -> true
        else -> false
    }

    fun clearCache() {
        compatibilityCache.clear()
        furnaceRecipeCache.clear()
        cachedRecipeCount = -1
    }

    private fun classifyMaterial(material: Material): RecipeCompatibility = when {
        material in oreMaterials -> RecipeCompatibility.BLAST
        material.name.endsWith("_ORE") -> RecipeCompatibility.BLAST
        material.name.startsWith("RAW_") -> RecipeCompatibility.BLAST
        material in foodMaterials || material.isEdible -> RecipeCompatibility.SMOKER
        material in logMaterials || isLogMaterial(material) -> RecipeCompatibility.SMOKER
        else -> RecipeCompatibility.NORMAL
    }

    private fun isLogMaterial(material: Material): Boolean =
        material.name.endsWith("_LOG") ||
            material.name.endsWith("_WOOD") ||
            material.name.endsWith("_STEM") ||
            material.name == "MUSHROOM_STEM"
}
