package top.mc506lw.rebar.ironfurnaces

import io.github.pylonmc.rebar.addon.RebarAddon
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.plugin.java.JavaPlugin
import top.mc506lw.rebar.ironfurnaces.furnace.RecipeDetector
import java.util.Locale

class IronFurnaces : JavaPlugin(), RebarAddon {

    companion object {
        lateinit var instance: IronFurnaces
            private set
    }

    override fun onEnable() {
        instance = this
        registerWithRebar()

        IronFurnaceItems.initialize()
        IronFurnaceBlocks.initialize()
        FurnaceRecipes.initialize()
        IronFurnacePages.initialise()
    }

    override fun onDisable() {
        RecipeDetector.clearCache()
    }

    override val javaPlugin: JavaPlugin
        get() = this

    /**
     * Rebar matches items/blocks to their addon by the key *namespace*, and its default
     * implementation would derive it from the plugin name ("lapis-ironfurnaces").
     * All of this addon's content keys live under the `ironfurnaces` namespace.
     */
    override fun getKey(): NamespacedKey = IronFurnaceKeys.key("ironfurnaces")

    override val defaultLanguage: Locale
        get() = Locale.ENGLISH

    override val material: Material
        get() = Material.FURNACE
}
