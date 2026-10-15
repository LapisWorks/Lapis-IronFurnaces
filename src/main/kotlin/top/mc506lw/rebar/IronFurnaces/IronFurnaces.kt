package top.mc506lw.rebar.ironfurnaces

import io.github.pylonmc.rebar.addon.RebarAddon
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.plugin.java.JavaPlugin
import top.mc506lw.rebar.ironfurnaces.furnace.FurnaceSides
import top.mc506lw.rebar.ironfurnaces.furnace.GolemStructureGuard
import top.mc506lw.rebar.ironfurnaces.furnace.RecipeDetector
import top.mc506lw.rebar.sidedio.SidedIo
import java.util.Locale

class IronFurnaces : JavaPlugin(), RebarAddon {

    companion object {
        lateinit var instance: IronFurnaces
            private set
    }

    override fun onEnable() {
        instance = this
        // 首次启动把自带的 config.yml（含中英注释）写到插件目录，之后读服务器上的那份
        saveDefaultConfig()
        reloadConfig()

        registerWithRebar()

        IronFurnaceItems.initialize()
        IronFurnaceBlocks.initialize()
        FurnaceRecipes.initialize()
        IronFurnacePages.initialise()

        // 注册本附属自定义的面状态（面板上的第五档「燃料输入 / 弹出」）。
        // ⚠️ 必须在任何熔炉被放下来**之前** —— 读盘还原时按 id 反查面状态，
        // 注册晚了那些机器会退回默认值。
        FurnaceSides.registerFaceModes()

        // 把九个熔炉类登记给 Sided-IO 的扳手桥 —— 玩家拿 RebarWrench 扳手右键
        // 熔炉的某个面就能切换那个面的进出料模式（装了扳手才生效，没装静默跳过）。
        // RebarWrench 按精确类查表，所以每个实现类都要单独登记。
        val furnaces = IronFurnaceBlocks.implementationClasses()
        val ok = furnaces.count { SidedIo.registerWrenchTarget(it) }
        if (SidedIo.isWrenchAvailable()) {
            if (ok == furnaces.size) {
                logger.info("已把 ${furnaces.size} 个熔炉类登记到 RebarWrench 扳手上")
            } else {
                logger.warning("只登记成功 $ok / ${furnaces.size} 个熔炉类（详见上方 Sided-IO 警告）")
            }
        }

        server.pluginManager.registerEvents(GolemStructureGuard(), this)
        // 原版熔炉 / 高炉 / 烟熏炉的燃料接管：彩虹煤的 10 倍时长 + 加热棒改吃电
        server.pluginManager.registerEvents(VanillaFurnaceFuelListener, this)
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
