package top.mc506lw.rebar.ironfurnaces

import io.github.pylonmc.rebar.addon.RebarAddon
import org.bukkit.Material
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

    override val defaultLanguage: Locale
        get() = Locale.ENGLISH

    override val material: Material
        get() = Material.FURNACE
}
