package top.mc506lw.rebar.ironfurnaces.furnace

import io.github.pylonmc.rebar.block.context.BlockCreateContext
import io.github.pylonmc.rebar.recipe.vanilla.SmeltingRebarRecipe
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.block.Block
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import top.mc506lw.rebar.ironfurnaces.IronFurnaceKeys
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.max

class RainbowFurnace : AbstractIronFurnace {
    constructor(block: Block, context: BlockCreateContext) :
        super(block, context, FurnaceTier.RAINBOW, Material.WHITE_WOOL)

    constructor(block: Block, pdc: PersistentDataContainer) :
        super(block, pdc, FurnaceTier.RAINBOW, Material.WHITE_WOOL) {
        colorIndex = Math.floorMod(
            pdc.getOrDefault(IronFurnaceKeys.RAINBOW_COLOR_INDEX, PersistentDataType.INTEGER, 0),
            WOOL_COLORS.size
        )
        colorTickCounter = pdc.getOrDefault(
            IronFurnaceKeys.RAINBOW_COLOR_TICK,
            PersistentDataType.INTEGER,
            0
        ).coerceIn(0, COLOR_CHANGE_INTERVAL - 1)
    }

    companion object {
        private const val COLOR_CHANGE_INTERVAL = 10

        /**
         * 门槛过了（真的在发电）时，颜色**转得更快** —— 相当于 26.3 那个
         * `RAINBOW_GENERATING` 方块状态的"亮起来"。我们不用方块状态（颜色本身在转），
         * 所以用转速当信号：一眼就能看出"这台在发电 / 没在发电"。
         */
        private const val GENERATING_COLOR_INTERVAL = 2

        private val WOOL_COLORS = arrayOf(
            Material.WHITE_WOOL,
            Material.ORANGE_WOOL,
            Material.MAGENTA_WOOL,
            Material.LIGHT_BLUE_WOOL,
            Material.YELLOW_WOOL,
            Material.LIME_WOOL,
            Material.PINK_WOOL,
            Material.GRAY_WOOL,
            Material.LIGHT_GRAY_WOOL,
            Material.CYAN_WOOL,
            Material.PURPLE_WOOL,
            Material.BLUE_WOOL,
            Material.BROWN_WOOL,
            Material.GREEN_WOOL,
            Material.RED_WOOL,
            Material.BLACK_WOOL
        )

        private val DUST_OPTIONS = arrayOf(
            Color.WHITE,
            Color.ORANGE,
            Color.FUCHSIA,
            Color.AQUA,
            Color.YELLOW,
            Color.LIME,
            Color.fromRGB(255, 175, 175),
            Color.GRAY,
            Color.SILVER,
            Color.TEAL,
            Color.PURPLE,
            Color.BLUE,
            Color.MAROON,
            Color.GREEN,
            Color.RED,
            Color.BLACK
        ).map { Particle.DustOptions(it, 1.5f) }.toTypedArray()
    }

    override val rainbowSpeedLabel: String = "10x-640x"

    /**
     * 上一 tick 门槛过了没有 —— 只用来驱动外观（发电时颜色转得更快）。
     */
    private var generating = false

    private var colorIndex = 0
    private var colorTickCounter = 0
    private val rainbowParticleLocation by lazy(LazyThreadSafetyMode.NONE) {
        block.location.toCenterLocation().add(0.0, 1.0, 0.0)
    }

    override fun write(pdc: PersistentDataContainer) {
        super.write(pdc)
        pdc.set(IronFurnaceKeys.RAINBOW_COLOR_INDEX, PersistentDataType.INTEGER, colorIndex)
        pdc.set(IronFurnaceKeys.RAINBOW_COLOR_TICK, PersistentDataType.INTEGER, colorTickCounter)
    }

    override fun setupBlockType() {
        block.type = WOOL_COLORS[colorIndex]
    }

    override fun tick() {
        super.tick()

        // ⚠️ 顺序有讲究：`super.tick()` 里才会跑发电机逻辑、算出这一 tick 门槛过没过，
        // 所以外观要**在它之后**读 [generating]。
        colorTickCounter++
        val interval = if (generating) GENERATING_COLOR_INTERVAL else COLOR_CHANGE_INTERVAL
        if (colorTickCounter >= interval) {
            colorTickCounter = 0
            colorIndex = (colorIndex + 1) % WOOL_COLORS.size
            block.type = WOOL_COLORS[colorIndex]
        }
    }

    // ------------------------------------------------------------ 发电门槛

    /**
     * 彩虹熔炉的出力 —— 不是等级基础值，而是它自己那份配置
     * （对应 26.3 的 `Config.millionFurnacePowerToGenerate`）。
     */
    override fun generationWatts(effects: UpgradeEffects): Double =
        FurnaceConfig.rainbowGeneration() * effects.generatorPowerMultiplier

    /**
     * **彩虹熔炉的发电门槛** —— 复刻 26.3 的 `rainbowCheckFurnaceTiers` 那套判定。
     *
     * 装发电机升级**不等于**会发电。要同时满足：
     *
     * 1. 这台熔炉记着主人（`owner`），且主人在线；
     * 2. **每一档**（铜 / 铁 / 金 / 钻石 / 绿宝石 / 水晶 / 黑曜石 / 下界合金 / 彩虹）
     *    都要有**至少一台正在发电**的熔炉 —— 也就是"集齐各档，而且它们都在烧"；
     * 3. 彩虹熔炉自己**只能有一台**在发电机模式（多台会互相取消）。
     *
     * 没过门槛时燃料照烧，但**一点电都不出** —— 这正是那个机制的意义：
     * 彩虹熔炉是一台"成就机"，不是随便放一台就能用的发电机。
     *
     * ## 和 26.3 的两处差异（都是刻意的）
     *
     * - **去掉了"没充满"那一半**。26.3 要求每台 `getEnergy() < getCapacity()`，
     *   因为它的能量缓冲会被用电器抽走；我们这边的内部缓冲是一份**不被电网抽**的
     *   10k 副本，几 tick 就满了 —— 照抄会让门槛**永久不成立**。
     *   所以只要求"在发电（发电机模式 + 正在烧燃料）"。
     * - **只认区块已加载的熔炉**（见 [PlayerFurnaces]）：26.3 会强制加载区块，
     *   那在服务端是 TPS 灾难。熔炉所在区块没加载时那台这一 tick 不算数。
     */
    override fun canGeneratePower(): Boolean {
        val passes = rainbowGatePasses()
        generating = passes
        return passes
    }

    private fun rainbowGatePasses(): Boolean {
        val uuid = owner ?: return false
        val player = PlayerFurnaces.of(uuid) ?: return false
        val mine = PlayerFurnaces.furnacesOf(player)
        return RainbowGate.passes(
            owned = mine.map { it.tier },
            burning = mine.filter { it.isGeneratorBurning() }.mapTo(HashSet()) { it.tier },
            rainbowGenerators = mine.count { it is RainbowFurnace && it.isInGeneratorMode() },
        )
    }

    /** 门槛这会儿过了没有（给 GUI / 调试用）。 */
    internal val isGenerating: Boolean get() = generating

    override fun spawnSmokeParticle() {
        block.world.spawnParticle(
            Particle.DUST,
            rainbowParticleLocation,
            ThreadLocalRandom.current().nextInt(3, 6),
            0.3,
            0.5,
            0.3,
            DUST_OPTIONS[colorIndex]
        )
    }

    /**
     * The rainbow furnace spends its spare fuel the moment a task finishes and smelts a whole batch
     * at once, instead of one item per task.
     */
    override fun batchSizeFor(slot: Int, recipe: SmeltingRebarRecipe, effects: UpgradeEffects): Int =
        max(1, (fuelRemaining + tickInterval - 1) / tickInterval)

    override fun onBatchProcessed(
        slot: Int,
        recipe: SmeltingRebarRecipe,
        processed: Int,
        effects: UpgradeEffects
    ) {
        if (processed > 0) {
            fuelRemaining = (fuelRemaining - processed * tickInterval).coerceAtLeast(0)
        }
    }
}
