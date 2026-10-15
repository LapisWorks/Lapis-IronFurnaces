package top.mc506lw.rebar.ironfurnaces.furnace

import io.github.pylonmc.rebar.block.BlockStorage
import io.github.pylonmc.rebar.block.context.BlockBreakContext
import io.github.pylonmc.rebar.block.context.BlockCreateContext
import io.github.pylonmc.rebar.block.interfaces.BlockBreakRebarBlockHandler
import io.github.pylonmc.rebar.block.interfaces.DirectionalRebarBlock
import io.github.pylonmc.rebar.block.interfaces.EntityHolderRebarBlock
import io.github.pylonmc.rebar.block.interfaces.FurnaceRebarBlockHandler
import io.github.pylonmc.rebar.block.interfaces.InteractRebarBlockHandler
import io.github.pylonmc.rebar.block.interfaces.LogisticRebarBlock
import io.github.pylonmc.rebar.block.interfaces.SimpleElectricRebarBlock
import io.github.pylonmc.rebar.block.interfaces.TickingRebarBlock
import io.github.pylonmc.rebar.block.interfaces.VirtualInventoryRebarBlock
import io.github.pylonmc.rebar.electricity.WireConnectionService
import io.github.pylonmc.rebar.electricity.WireEntity
import io.github.pylonmc.rebar.config.RebarConfig
import io.github.pylonmc.rebar.event.api.annotation.MultiHandler
import io.github.pylonmc.rebar.i18n.RebarArgument
import io.github.pylonmc.rebar.item.RebarItem
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder
import io.github.pylonmc.rebar.item.interfaces.WireRebarItem
import io.github.pylonmc.rebar.recipe.vanilla.SmeltingRebarRecipe
import io.github.pylonmc.rebar.util.MachineUpdateReason
import io.github.pylonmc.rebar.util.Either
import io.github.pylonmc.rebar.util.gui.GuiItems
import io.github.pylonmc.rebar.util.gui.ProgressItem
import io.github.pylonmc.rebar.waila.WailaDisplay
import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.event.EventPriority
import org.bukkit.event.inventory.FurnaceSmeltEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import io.github.pylonmc.rebar.util.position.position
import top.mc506lw.rebar.ironfurnaces.IronFurnaceItems
import top.mc506lw.rebar.ironfurnaces.IronFurnaceKeys
import top.mc506lw.rebar.sidedio.ChannelTypes
import top.mc506lw.rebar.sidedio.RedstoneMode
import top.mc506lw.rebar.sidedio.SideConfig
import top.mc506lw.rebar.sidedio.SideMode
import top.mc506lw.rebar.sidedio.SidedIo
import top.mc506lw.rebar.sidedio.SidedIoRegistry
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.inventory.Inventory
import xyz.xenondevs.invui.inventory.VirtualInventory
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.invui.inventory.event.PlayerUpdateReason
import xyz.xenondevs.invui.window.Window
import java.util.Locale
import kotlin.math.min

/**
 * A furnace smelts one item per active input slot at a time. A normal furnace has a single input
 * slot, while the factory augment turns it into a machine with up to six input slots that all smelt
 * in parallel, each with its own progress bar — the same shape as the classic mod's factory mode.
 *
 * Rebar's [io.github.pylonmc.rebar.block.interfaces.RecipeProcessorRebarBlock] only tracks a single
 * recipe, so the smelting state is kept here as one task per slot instead.
 */
abstract class AbstractIronFurnace : IronFurnaceBase,
    VirtualInventoryRebarBlock,
    DirectionalRebarBlock,
    TickingRebarBlock,
    LogisticRebarBlock,
    FurnaceRebarBlockHandler,
    EntityHolderRebarBlock,
    SimpleElectricRebarBlock,
    BlockBreakRebarBlockHandler,
    InteractRebarBlockHandler {

    protected constructor(
        block: Block,
        context: BlockCreateContext,
        furnaceTier: FurnaceTier,
        baseMaterial: Material
    ) : super(block, context, furnaceTier, baseMaterial) {
        // ⚠️ **无条件**设朝向。以前只对 `PlayerPlace` 设 —— 于是插件生成的方块
        // （升级换方块、`placeBlock`）会停在"没设过方向"的非法状态，
        // 之后任何一次读 `facing` 都抛 `IllegalStateException: No direction was set for block ...`，
        // 而那次读取通常在 `postInitialise` 里 → **整个初始化中断** →
        // 方块停在注册材质（`Material.FURNACE`）上、"外壳"没了（用户实测报的）。
        // `context.facing` 在接口上永远有值（默认 NORTH），所以这里可以无条件用。
        facing = context.facing
        // 记下"谁放的" —— 彩虹熔炉的发电门槛要按玩家找他名下的熔炉
        // （见 [PlayerFurnaces] 与 [RainbowFurnace]）。
        owner = context.player?.uniqueId
        // 放这台机器用的那件物品 —— 拆机保留设置就存在它上面（见 [restoreSavedSettings]）
        placedFromItem = context.item
    }

    protected constructor(
        block: Block,
        pdc: PersistentDataContainer,
        furnaceTier: FurnaceTier,
        baseMaterial: Material
    ) : super(block, pdc, furnaceTier, baseMaterial) {
        restorePersistentState(pdc)
    }

    @Suppress("UNUSED_PARAMETER")
    protected constructor(
        block: Block,
        context: BlockCreateContext,
        furnaceTier: FurnaceTier,
        baseMaterial: Material,
        guiMaterial: Material
    ) : this(block, context, furnaceTier, baseMaterial)

    @Suppress("UNUSED_PARAMETER")
    protected constructor(
        block: Block,
        pdc: PersistentDataContainer,
        furnaceTier: FurnaceTier,
        baseMaterial: Material,
        guiMaterial: Material
    ) : this(block, pdc, furnaceTier, baseMaterial)

    private class SmeltTask {
        var recipe: SmeltingRebarRecipe? = null
        var totalTicks = 0
        var remainingTicks = 0

        val isActive: Boolean
            get() = recipe != null && remainingTicks > 0

        fun start(recipe: SmeltingRebarRecipe, ticks: Int) {
            this.recipe = recipe
            totalTicks = ticks
            remainingTicks = ticks
        }

        fun clear() {
            recipe = null
            totalTicks = 0
            remainingTicks = 0
        }
    }

    companion object {
        private val LOGGER = java.util.logging.Logger.getLogger("IronFurnace")
        private val MACHINE_UPDATE_REASON = MachineUpdateReason()

        /** Input/output slots a factory furnace can have, i.e. the classic mod's six factory slots. */
        private const val MAX_FACTORY_SLOTS = 6

        /**
         * 脉冲档位下，一次红石脉冲让机器运行多久（tick）。
         *
         * 5 秒 —— 够跑完几个物品，又不至于"按一下开关跑一整天"。
         */
        private const val PULSE_DURATION_TICKS = 100
    }

    override var disableBlockTextureEntity = true

    /**
     * 放置这台熔炉的玩家。
     *
     * 存在的理由只有一个：**彩虹熔炉的发电门槛**要问"这个玩家名下有哪些熔炉"
     * （见 [PlayerFurnaces]）。`null` = 不是玩家放的（插件生成 / 老存档没记），
     * 那种情况下彩虹那套门槛不成立 —— 和其它机器一样正常烧东西，只是不参与彩虹发电。
     */
    internal var owner: java.util.UUID? = null

    /**
     * 放下这台机器用的那件物品 —— 只在 [postInitialise] 期间有用。
     *
     * 拆机保留设置（见 [onBlockBreak]）把配置写进掉落物，玩家再放下来时
     * 从这件物品读回来。用完就置空，别一直抱着那个 `ItemStack`。
     */
    private var placedFromItem: org.bukkit.inventory.ItemStack? = null

    /**
     * **autoSplit** —— 熔炉级开关：工厂模式下把进来的物品**摊平**到所有可用槽位。
     *
     * 关着的时候一摞矿石全挤在第 0 格，其它槽空转（一次只烧一件）；
     * 打开之后多槽才真正并行。复刻 26.3 的 `FurnaceSettings.autoSplit`。
     *
     * ⚠️ 它是**熔炉自己**的设置（熔炉 GUI 上的按钮），**不是面设置** ——
     * 所以不走 Sided-IO 的面配置面板，直接存在方块 PDC 里。
     */
    internal var autoSplit: Boolean = false
        private set

    /** GUI 按钮用：翻转 [autoSplit]。 */
    internal fun toggleAutoSplit() {
        autoSplit = !autoSplit
    }

    protected val inputInv = VirtualInventory(MAX_FACTORY_SLOTS)
    protected val outputInv = VirtualInventory(MAX_FACTORY_SLOTS)
    protected val fuelInv = VirtualInventory(1)

    protected val upgradeRedSlot = VirtualInventory(1)
    protected val upgradeGreenSlot = VirtualInventory(1)
    protected val upgradeBlueSlot = VirtualInventory(1)

    protected val upgradeManager by lazy(LazyThreadSafetyMode.NONE) {
        UpgradeEffectManager(upgradeRedSlot, upgradeGreenSlot, upgradeBlueSlot)
    }

    private val energySystemDelegate = lazy(LazyThreadSafetyMode.NONE) { FurnaceEnergySystem() }
    protected val energySystem by energySystemDelegate

    protected val electricSystem by lazy(LazyThreadSafetyMode.NONE) { FurnaceElectricSystem(this) }

    protected val fuelSystem by lazy(LazyThreadSafetyMode.NONE) {
        FurnaceFuelSystem(block, furnaceTier, fuelInv)
    }

    protected val displayRenderer by lazy(LazyThreadSafetyMode.NONE) { createDisplayRenderer() }

    private val tasks = Array(MAX_FACTORY_SLOTS) { SmeltTask() }
    private val progressItems = Array(MAX_FACTORY_SLOTS) { InvertedProgressItem(GuiItems.background()) }

    private val guiFactory by lazy(LazyThreadSafetyMode.NONE) {
        FurnaceGuiFactory(
            furnace = this,
            inputInv = inputInv,
            outputInv = outputInv,
            fuelInv = fuelInv,
            fuelSystem = fuelSystem,
            upgradeRedSlot = upgradeRedSlot,
            upgradeGreenSlot = upgradeGreenSlot,
            upgradeBlueSlot = upgradeBlueSlot
        )
    }

    private val inventoryMap by lazy(LazyThreadSafetyMode.NONE) {
        mapOf(
            "input" to inputInv,
            "output" to outputInv,
            "fuel" to fuelInv,
            "upgrade_red" to upgradeRedSlot,
            "upgrade_green" to upgradeGreenSlot,
            "upgrade_blue" to upgradeBlueSlot
        )
    }

    override val tickInterval: Int = (furnaceTier.smeltTimePerItem / 10).coerceAtLeast(1)

    private val guiTitle = Component.translatable(
        "ironfurnaces.item.${furnaceTier.name.lowercase(Locale.ROOT)}_furnace.name"
    )
    private var runtimeConfigured = false
    private var activeSlots = 1
    private var fuelSlotEnabled = true
    private var waitingForFuel = false

    /**
     * 这台熔炉的面配置 + **机器级设置**（红石 / 自动输入 / 自动输出）。
     *
     * [rebuildSides] 时刷新。红石档位、自动开关的**真相**都在这里，
     * 而不是熔炉自己存一份 —— 面板改的和机器读的必须是同一个值。
     */
    private var sides: top.mc506lw.rebar.sidedio.SideConfig? = null

    /**
     * 上一次 [rebuildSides] 时这台熔炉**用不用电**。
     *
     * 存在的理由：面配置里"能量"这一行只有用电的熔炉才该出现
     * （见 [FurnaceSides.attach] 的 `usesEnergy`）。而 [rebuildSides] 是在
     * `configureRuntime()` 里先跑一次的 —— 那时升级还没结算，`calculateEffects()`
     * 未必准。所以这里存一份"上次用的值"，由 [applyUpgradeEffects] 在算完
     * `effects` 之后更新，并把它算进"要不要重建"的判据里。
     */
    private var sidesUsesEnergy = false

    /** 当前红石档位。真相在 Sided-IO 的 `MachineSettings` 里（面板改的就是它）。 */
    private val redstoneMode: top.mc506lw.rebar.sidedio.RedstoneMode
        get() = sides?.settings?.redstoneMode ?: top.mc506lw.rebar.sidedio.RedstoneMode.DEFAULT

    /** 脉冲模式的剩余运行 tick（>0 表示窗口还开着）。 */
    private var pulseTicksRemaining = 0

    /** 上一 tick 有没有红石信号 —— 脉冲档靠它做**边沿检测**。 */
    private var lastPowered = false

    /** 刚提出用电需求时要等一个电网周期，网络才会把 isPowered 写回来（见 handleSmeltTick）。 */
    private var powerGraceTicks = 0
    private var cachedProgressLabel: String? = null
    private var cachedProgressStack: ItemStack? = null
    private val smokeParticleLocation by lazy(LazyThreadSafetyMode.NONE) {
        block.location.toCenterLocation().add(0.0, 0.8, 0.0)
    }

    init {
        setTickInterval(tickInterval)
        upgradeRedSlot.setMaxStackSize(0, 1)
        upgradeGreenSlot.setMaxStackSize(0, 1)
        upgradeBlueSlot.setMaxStackSize(0, 1)
    }

    var fuelEfficiency: Double
        get() = fuelSystem.fuelEfficiency
        set(value) {
            fuelSystem.fuelEfficiency = value
        }

    var speedMultiplier: Double
        get() = upgradeManager.calculateEffects().speedMultiplier
        protected set(@Suppress("UNUSED_PARAMETER") value) = Unit

    var currentFuelTime: Int
        get() = fuelSystem.currentFuelTime
        protected set(value) {
            fuelSystem.currentFuelTime = value
        }

    var fuelRemaining: Int
        get() = fuelSystem.fuelRemaining
        protected set(value) {
            fuelSystem.fuelRemaining = value
        }

    protected open val rainbowSpeedLabel: String?
        get() = null

    /** Number of input (and output) slots that are currently usable. */
    internal val activeSlotCount: Int
        get() = activeSlots

    /** Slots the GUI has to show, i.e. active slots plus any leftovers from a removed upgrade. */
    internal val displayedSlots: Int
        get() = maxOf(activeSlots, displayedInputSlots, displayedOutputSlots)

    internal val displayedOutputSlots: Int
        get() {
            for (index in MAX_FACTORY_SLOTS - 1 downTo activeSlots) {
                if (outputInv.hasItem(index)) return index + 1
            }
            return activeSlots
        }

    internal val displayedInputSlots: Int
        get() {
            for (index in MAX_FACTORY_SLOTS - 1 downTo activeSlots) {
                if (inputInv.hasItem(index)) return index + 1
            }
            return activeSlots
        }

    /** False while an energy upgrade is installed, because that mode never burns fuel. */
    internal val isFuelSlotEnabled: Boolean
        get() = fuelSlotEnabled

    /** True while the generator augment is installed: smelting slots are hidden in that mode. */
    internal val inGeneratorMode: Boolean
        get() = isGeneratorMode(upgradeManager.calculateEffects().mode)

    internal fun progressItem(slot: Int): ProgressItem = progressItems[slot.coerceIn(0, MAX_FACTORY_SLOTS - 1)]

    /** True while at least one slot is smelting; drives the "lit" look of an energy furnace. */
    internal val isSmelting: Boolean
        get() = (0 until activeSlots).any { tasks[it].isActive }

    protected open fun createDisplayRenderer(): FurnaceDisplayRenderer = FurnaceDisplayRenderer(
        block = block,
        getFacing = { facing },
        getEntity = { name -> heldEntities[name] },
        addEntity = { name, entity -> addEntity(name, entity) },
        removeEntity = ::tryRemoveEntity
    )

    override fun postInitialise() {
        configureRuntime()
        setupBlockType()
        // 电口不在这里开 —— 只有**用电**的熔炉才该有电口，
        // 由 applyUpgradeEffects() 按 effects.usesEnergy 决定（见那里）。
        applyUpgradeEffects()

        // 拆机保留设置：如果这件物品是从别的熔炉上拆下来的，把它的配置读回来。
        // ⚠️ 必须在 `applyUpgradeEffects()` **之后** —— 那里可能重建过面配置，
        // 我们的设置要盖在最终那份上。
        restoreSavedSettings()

        // 登记进"玩家熔炉名册"（彩虹发电的门槛要按玩家找他名下的熔炉）
        owner?.let { PlayerFurnaces.of(it) }?.let { PlayerFurnaces.add(it, block) }

        val displayType = upgradeManager.getDisplayBlockType()
        displayRenderer.ensureFrontFace(displayType)
        displayRenderer.updateBurningState(isLit(), displayType)
    }

    override fun postLoad() {
        super.postLoad()
        configureRuntime()
        upgradeManager.invalidateCache()
        applyUpgradeEffects()
        fuelSystem.refreshDisplay()
        setupBlockType()

        val displayType = upgradeManager.getDisplayBlockType()
        displayRenderer.resetState()
        displayRenderer.ensureFrontFace(displayType)
        displayRenderer.updateFrontFace()
        displayRenderer.refreshState(isLit(), displayType)

        restoreTasks()
        reconcileTasks()
    }

    override fun write(pdc: PersistentDataContainer) {
        super.write(pdc)
        fuelSystem.write(pdc)
        if (energySystemDelegate.isInitialized()) energySystem.write(pdc)

        // Same idea as the classic mod's FactoryCookTime/FactoryTotalCookTime arrays: only the tick
        // counters are stored, the recipe itself is resolved from the input slot again on load.
        pdc.set(
            IronFurnaceKeys.TASK_TICKS_TOTAL,
            PersistentDataType.INTEGER_ARRAY,
            IntArray(MAX_FACTORY_SLOTS) { tasks[it].totalTicks }
        )
        pdc.set(
            IronFurnaceKeys.TASK_TICKS_REMAINING,
            PersistentDataType.INTEGER_ARRAY,
            IntArray(MAX_FACTORY_SLOTS) { tasks[it].remainingTicks }
        )
        // ⚠️ 红石档位**不在这里存** —— 它归 Sided-IO 的 `sidedio:machine_settings`
        // 容器管（面板改的就是那份）。这里再存一份会出现"两个真相"。

        // 谁是主人 —— 彩虹发电要按玩家找他名下的熔炉
        owner?.let { pdc.set(IronFurnaceKeys.OWNER, PersistentDataType.STRING, it.toString()) }

        // autoSplit 是**熔炉自己**的开关（不是面设置），所以存在这里
        pdc.set(IronFurnaceKeys.AUTO_SPLIT, PersistentDataType.BOOLEAN, autoSplit)
    }

    /**
     * **拆机保留设置** —— 复刻 26.3 的 `FURNACE_SETTINGS` 数据组件。
     *
     * 拆掉熔炉时把它的**面模式 + 机器级设置**写进掉落物；玩家再放下来时读回来
     * （见 [restoreSavedSettings]）。这样"把炉子挪个位置"不用重新配一遍。
     *
     * ⚠️ 用的是 [onBlockBreak]（`drops` 可改），**不是** [onPostBlockBreak] ——
     * 后者跑的时候掉落物早就掉出去了。
     */
    override fun onBlockBreak(drops: MutableList<ItemStack>, context: BlockBreakContext) {
        val config = sides ?: return
        val drop = drops.firstOrNull { IronFurnaceItems.isFurnaceItem(it) } ?: return
        drop.editMeta { meta ->
            meta.persistentDataContainer.set(
                IronFurnaceKeys.SAVED_SETTINGS,
                PersistentDataType.STRING,
                FurnaceSettingsCodec.snapshot(config)
            )
        }
    }

    /**
     * 从**放置用的那件物品**上把配置读回来。
     *
     * 新方块没有自己的 PDC，所以物品是唯一的来源。
     * 读不到（普通合成的新炉子）就什么都不做 —— 走出厂默认。
     */
    private fun restoreSavedSettings() {
        val item = placedFromItem
        placedFromItem = null
        if (item == null) return
        val config = sides ?: return

        val text = item.itemMeta?.persistentDataContainer
            ?.get(IronFurnaceKeys.SAVED_SETTINGS, PersistentDataType.STRING)
            ?: return
        if (FurnaceSettingsCodec.apply(config, text)) {
            // 方向变了 → 物流组要按新方向重算
            SidedIoRegistry.refreshGroups(block)
        }
    }

    // ------------------------------------------------------------ 设置复制器

    /**
     * 把当前配置写进手里的**设置复制器**。
     *
     * @return 复制成功没有
     */
    internal fun copySettingsTo(tool: ItemStack): Boolean {
        val config = sides ?: return false
        tool.editMeta { meta ->
            meta.persistentDataContainer.set(
                IronFurnaceKeys.SAVED_SETTINGS,
                PersistentDataType.STRING,
                FurnaceSettingsCodec.snapshot(config)
            )
        }
        return true
    }

    /**
     * 把**设置复制器**里存的配置套到这台机器上。
     *
     * @return 套用成功没有（复制器里没东西时返回 false）
     */
    internal fun applySettingsFrom(tool: ItemStack): Boolean {
        val config = sides ?: return false
        val text = tool.itemMeta?.persistentDataContainer
            ?.get(IronFurnaceKeys.SAVED_SETTINGS, PersistentDataType.STRING)
            ?: return false
        if (!FurnaceSettingsCodec.apply(config, text)) return false
        SidedIoRegistry.refreshGroups(block)
        return true
    }

    override fun onPostBlockBreak(context: BlockBreakContext) {
        // 这里**不要**自己删实体。`onPostBlockBreak` 跑在 `RebarBlockBreakEvent` 之前，而端口实体
        // （ElectricPortEntity）被移除时会注销自己的 RebarElectricNodeRemoveEvent 监听器；一旦它先没了，
        // Rebar 的 `ElectricRebarBlock.onBreak` → 端口实体 `onNodeRemove` → `dropConnectedWires`
        // 就整个被跳过，挂在熔炉端子上的电线会留在原地指向已消失的节点（悬空线）。
        //
        // 实体交给 Rebar 的 `EntityHolderRebarBlock.onBreak`（MONITOR 优先级，在 RebarBlockBreakEvent
        // 里、所有 break handler 之后）去删，和以前的效果一样，只是晚几步。
        removeConnectedWires()

        // 从"玩家熔炉名册"里划掉 —— 彩虹发电的门槛会实时问名册，
        // 留着僵尸条目会让玩家"拆了熔炉结果彩虹还在发电"。
        owner?.let { PlayerFurnaces.of(it) }?.let { PlayerFurnaces.remove(it, block) }
    }

    /**
     * 兜底清理：把落在这个方块上的电线掉出来。
     *
     * Rebar 自己在 `ElectricRebarBlock.onBreak` 里会调 `removeNode`，端口实体的 `onNodeRemove` →
     * `dropConnectedWires` 会清线 —— **前提是端口实体还在**。只要端口实体在 `RebarBlockBreakEvent`
     * 之前被移除（例如某个附属在 `onPostBlockBreak` 里删实体），它的监听器就注销了，Rebar 那套清理
     * 会被整个跳过。所以这里再兜一层。
     *
     * 已知限制：`loadedWires` 只包含已加载的电线，如果线的另一端在未加载的区块里，这条线清理不到。
     */
    private fun removeConnectedWires() {
        FurnaceWires.removeAt(block.location.toCenterLocation())
    }

    override fun tick() {
        val effects = upgradeManager.calculateEffects()

        // 红石：先做脉冲的边沿检测，再判"允不允许跑"
        tickRedstonePulse()

        // 自动输入 / 自动输出：机器自己跟相邻容器搬东西（不靠管道）
        sides?.let { SidedIo.autoTick(block, it) }

        if (isRedstoneBlocked()) {
            // The classic mod stops the whole machine; we pause it instead of voiding the progress
            // and the fuel that is already burning.
            electricSystem.idle()
        } else if (isGeneratorMode(effects.mode)) {
            handleGeneratorTick(effects)
        } else {
            // autoSplit：先把进料摊平，再烧 —— 顺序反了的话这一 tick 还是只有一格在干活
            rebalanceInputs()
            handleSmeltTick(effects)
        }

        displayRenderer.updateBurningState(isLit(), upgradeManager.getDisplayBlockType())
    }

    /**
     * Fills every idle active slot that has a matching input, then advances all of them together, so
     * a factory furnace smelts up to six items at once — each with its own progress bar.
     */
    private fun handleSmeltTick(effects: UpgradeEffects) {
        if (!effects.canSmelt) {
            clearAllTasks(effects)
            return
        }

        if (!waitingForFuel || effects.usesEnergy) {
            for (slot in 0 until activeSlots) {
                if (!tasks[slot].isActive) tryStartTask(slot, effects)
            }
        }

        val running = (0 until activeSlots).filter { tasks[it].isActive }
        if (running.isEmpty()) {
            if (effects.usesEnergy) electricSystem.idle() else fuelSystem.updateFuelState(tickInterval)
            return
        }

        val hadHeat = if (effects.usesEnergy) {
            // Every running slot draws its own share, so six parallel smelts ask the grid for six
            // times the power a single smelt would.
            val hadDemand = electricSystem.requiredPower > 0.0
            electricSystem.requiredPower = running.size * powerDraw(effects)

            // Rebar 的 consumer 节点只在"电网 tick"（默认 5 tick）时被写 isPowered，刚提出需求时
            // 字段还是初始值 true。而高等级熔炉配速度/高炉后烧一件只要 1 tick，这几 tick 里能白烧
            // 好几件，所以刚上电时先等一个电网周期再开始推进。
            if (!hadDemand) powerGraceTicks = RebarConfig.WIRING_TICK_INTERVAL
            if (powerGraceTicks > 0) {
                powerGraceTicks -= tickInterval
                false
            } else {
                electricSystem.isPowered
            }
        } else {
            var burning = fuelSystem.isBurning
            if (!burning) {
                burning = fuelSystem.consumeFuel()
                waitingForFuel = !burning
            }
            fuelSystem.updateFuelState(tickInterval)
            burning
        }

        if (hadHeat) {
            for (slot in running) progressTask(slot, effects)
            spawnSmokeParticle()
        } else if (!effects.usesEnergy) {
            // Out of fuel: the classic mod winds the progress back instead of voiding it at once.
            for (slot in running) decayTask(slot, effects)
        }
        // An energy furnace simply pauses until the grid supplies the requested power again.
    }

    /**
     * **autoSplit**：把进料槽里的物品**摊平**到所有可用槽位。
     *
     * 工厂模式有 2 / 4 / 6 个进料槽，但玩家或管道一次塞进来的是一整摞 ——
     * 不开这个开关，那一摞全躺在第 0 格，**一次只烧一件**，其它槽空转。
     *
     * ## 做法：填空槽，不动正在烧的
     *
     * 每次找一个空槽，再从"**没在烧**、且货最多"的槽里挪**一件**过去。
     * 只挪一件是刻意的：
     * - 当供体的槽要求 `!isActive` —— 不会打断正在烧的那一格；
     * - 每 tick 挪一件，几 tick 就摊平了，不需要"精确均分"那套算术，
     *   也不会因为反复搬运把 TPS 吃掉。
     */
    private fun rebalanceInputs() {
        if (!autoSplit) return
        val slots = activeSlots
        if (slots <= 1) return       // 非工厂模式只有一个进料槽，没什么可摊的

        for (target in 0 until slots) {
            if (inputInv.getUnsafeItem(target) != null) continue

            val donor = (0 until slots)
                .filter { it != target && !tasks[it].isActive }
                .maxByOrNull { inputInv.getUnsafeItem(it)?.amount ?: 0 }
                ?: continue

            val stack = inputInv.getUnsafeItem(donor) ?: continue
            if (stack.amount < 2) continue

            inputInv.setItem(MACHINE_UPDATE_REASON, target, stack.clone().apply { amount = 1 })
            inputInv.setItem(
                MACHINE_UPDATE_REASON, donor,
                stack.clone().apply { amount = stack.amount - 1 }
            )
        }
    }

    // ------------------------------------------------------------ 等级升级

    /**
     * **升级到 [target]** —— 复刻 26.3 的 `ItemUpgrade`：在同一个位置换成下一级的方块，
     * 并且**保留里面的东西和设置**。
     *
     * ## 怎么做到"保留"
     *
     * Rebar 不允许在已有 Rebar 方块的位置直接放新的，所以只能
     * **拆掉 → 放新的 → 把状态灌回去**：
     *
     * 1. 把六个库存的每一格、面配置文本、autoSplit、owner 拍下来；
     * 2. [BlockStorage.breakBlock] + `normallyDrops = false` —— ⚠️ **不掉落**，
     *    里面的东西我们手动搬。
     *    （原本想用 `deleteBlock`，但它是 Rebar 的 `internal`，插件调不到。）
     * 3. [BlockStorage.placeBlock] 放下一级；
     * 4. 灌回去。
     *
     * ⚠️ **不搬**正在烧的那点燃料进度和内部能量缓冲 —— 燃料**物品**在燃料槽里，
     * 跟着库存一起搬过去了，下一 tick 会重新点着。为这点东西做快照不划算。
     *
     * @return 成功没有
     */
    internal fun upgradeTo(target: FurnaceTier): Boolean {
        val targetKey = top.mc506lw.rebar.ironfurnaces.IronFurnaceBlocks.keyFor(target) ?: return false
        val position = block.position

        // ① 拍状态
        val inventories = listOf(
            inputInv, outputInv, fuelInv,
            upgradeRedSlot, upgradeGreenSlot, upgradeBlueSlot
        )
        val contents = inventories.map { inv -> (0 until inv.size).map { inv.getItem(it) } }
        val settings = sides?.let { FurnaceSettingsCodec.snapshot(it) }
        val wasAutoSplit = autoSplit
        val previousOwner = owner
        // ⚠️ **朝向也要保住**：`placeBlock` 用的是 `BlockCreateContext.Default`，
        // 它的 `facing` 是默认值，`postInitialise` 里那句"从玩家朝向读 facing"根本不会跑 ——
        // 不补的话升完级熔炉会自己转向（用户实测报的"熔炉方向还变了"）。
        val previousFacing = facing

        // ② 拆掉旧的（**不掉落** —— 里面的东西我们手动搬）
        //    `deleteBlock` 是 Rebar 的 internal，插件只能用公开的 `breakBlock`；
        //    把 `normallyDrops` 关掉效果一样。
        BlockStorage.breakBlock(
            block,
            BlockBreakContext.PluginBreak(block, normallyDrops = false)
        )

        // ③ 放新的
        // ⚠️ **必须自己带上下文**：`placeBlock` 默认用 `BlockCreateContext.Default`，
        // 它的 `facing` 是默认值 —— 那样新方块会丢掉原来的朝向
        // （用户实测报的"熔炉方向还变了"）。
        val context = object : BlockCreateContext {
            override val player: Player? = null
            override val facing: BlockFace = previousFacing
            override val facingVertical: BlockFace = previousFacing
            override val block: Block = position.block
            override val item: ItemStack? = null
        }
        val placed = BlockStorage.placeBlock(position.block, targetKey, context) as? AbstractIronFurnace
            ?: return false

        // ⚠️ **重新刷一遍外观**：`placeBlock` 用的是 `BlockCreateContext.Default`，
        // 它的 `shouldSetType` 是 `true` —— Rebar 会在 `postInitialise` **之后**
        // 把方块类型设成**注册材质**（我们的熔炉都注册成 `Material.FURNACE`），
        // 于是 `setupBlockType()` 刚设好的"铜块 / 铁块 / 铁砧…"外壳被冲掉，
        // 玩家看到的就是"升级完只剩一个光秃秃的熔炉"。
        placed.setupBlockType()

        // ④ 灌回去
        inventories.forEachIndexed { index, inv ->
            contents[index].forEachIndexed { slot, stack ->
                if (stack != null && !stack.isEmpty) inv.setItem(MACHINE_UPDATE_REASON, slot, stack)
            }
        }
        placed.autoSplit = wasAutoSplit
        placed.owner = previousOwner
        // 朝向已经在构造期设好了（见上面那个 context），面配置也是按它建的，不用再补
        // 位置没变，所以名册里那条其实还是对的；但 `placeBlock` 走的是
        // `BlockCreateContext.Default`（没有玩家），登记那一步被跳过了 —— 补一下。
        previousOwner?.let { PlayerFurnaces.of(it) }?.let { PlayerFurnaces.add(it, placed.block) }

        settings?.let { text ->
            placed.sides?.let { config ->
                if (FurnaceSettingsCodec.apply(config, text)) {
                    SidedIoRegistry.refreshGroups(placed.block)
                }
            }
        }

        // 等级变了 → 工厂槽数 / 发电量都变了，重算一遍
        placed.applyUpgradeEffects()
        // ⚠️ **外壳最后再刷一遍**。`placeBlock` → `setBlock` 会先把方块类型设成
        // **注册材质**（我们的熔炉都注册成 `Material.FURNACE`）再跑 `postInitialise`，
        // 而后面还有 `applyUpgradeEffects` / 显示实体等一串动作 ——
        // 放在最后设才不会被谁盖掉。
        placed.setupBlockType()
        return true
    }

    private fun handleGeneratorTick(effects: UpgradeEffects) {        if (!fuelSystem.isBurning) fuelSystem.consumeFuel()

        if (fuelSystem.isBurning) {
            // ⚠️ **门槛**：默认恒 true；彩虹熔炉覆写成"集齐各档"的判定
            // （见 [RainbowFurnace]）。没过门槛时燃料照烧（和 mod 一致），
            // 但**一点电都不出** —— 这正是那个机制的意义。
            if (!canGeneratePower()) {
                electricSystem.idle()
                fuelSystem.updateFuelState(tickInterval, effects.generatorSpeedMultiplier)
                return
            }
            val wattsPerTick = generationWatts(effects)
            energySystem.convertHeatToEnergy(tickInterval.toDouble(), wattsPerTick)
            // Push the generated power onto Rebar's electric network so nearby machines
            // can actually draw from this furnace, like the classic mod's energy output.
            electricSystem.requiredPower = 0.0
            electricSystem.powerProduced = wattsPerTick
            fuelSystem.updateFuelState(tickInterval, effects.generatorSpeedMultiplier)
        } else {
            electricSystem.idle()
            fuelSystem.updateFuelState(tickInterval)
        }
    }

    /**
     * 发电机模式下**这一 tick 出多少电**（W/t）。
     *
     * 默认是等级基础值 × 发电机升级倍率；彩虹熔炉覆写成它自己那份配置。
     */
    protected open fun generationWatts(effects: UpgradeEffects): Double =
        FurnaceConfig.generation(furnaceTier) * effects.generatorPowerMultiplier

    /**
     * 发电机模式下**允不允许真的发电**。
     *
     * 默认恒 `true`（装了发电机升级就发电）。彩虹熔炉覆写成 26.3 那套
     * "**名下每一档都要有一台在发电**"的门槛 —— 见 [RainbowFurnace]。
     */
    protected open fun canGeneratePower(): Boolean = true

    /** 这台熔炉**此刻正在发电**（发电机模式 + 正在烧燃料）。彩虹门槛按这个数各档的台数。 */
    internal fun isGeneratorBurning(): Boolean =
        isInGeneratorMode() && fuelSystem.isBurning

    /** 装了发电机升级、处在发电机模式（不要求正在烧）。 */
    internal fun isInGeneratorMode(): Boolean =
        isGeneratorMode(upgradeManager.calculateEffects().mode)

    /** 内部能量缓冲满了没有 —— 彩虹门槛要求"没充满"（和 26.3 的 `getEnergy() < getCapacity()` 一致）。 */
    internal fun isEnergyBufferFull(): Boolean =
        energySystem.retrieveCurrentEnergy() >= energySystem.getMaxEnergy()

    /**
     * A factory furnace spends a fixed amount of energy per item, so the draw per tick is that
     * amount spread over the furnace's own cook time: a faster furnace pulls more power per tick
     * but spends the same energy per item, exactly like the classic mod.
     */
    private fun powerDraw(effects: UpgradeEffects): Double =
        FurnaceConfig.energyPerItem() * effects.powerDrawMultiplier / furnaceTier.smeltTimePerItem

    private fun isGeneratorMode(mode: FurnaceMode): Boolean = when (mode) {
        FurnaceMode.GENERATOR_ONLY,
        FurnaceMode.GENERATOR_BLAST,
        FurnaceMode.GENERATOR_SMOKER -> true
        else -> false
    }

    /** A factory furnace lights up while smelting; a fuel furnace lights up while burning fuel. */
    private fun isLit(): Boolean {
        if (isRedstoneBlocked()) return false
        return if (fuelSlotEnabled) fuelSystem.isBurning else isSmelting
    }

    /**
     * 红石档位是否"禁止运行"。
     *
     * 四态语义（[top.mc506lw.rebar.sidedio.RedstoneMode]）：
     * - `IGNORED` 红石无关
     * - `LOW`     有信号 → 停
     * - `HIGH`    没信号 → 停
     * - `PULSE`   只在"收到信号后的那一小段窗口"里运行，窗口过了就停
     */
    private fun isRedstoneBlocked(): Boolean = when (redstoneMode) {
        RedstoneMode.IGNORED -> false
        RedstoneMode.LOW -> block.isBlockPowered
        RedstoneMode.HIGH -> !block.isBlockPowered
        RedstoneMode.PULSE -> pulseTicksRemaining <= 0
    }

    /**
     * 脉冲模式的边沿检测。
     *
     * ⚠️ 轮询 `isBlockPowered` **看不出"刚刚收到信号"**，得自己记上一 tick 的状态
     * 比对。这就是"脉冲"这个档位比另外三档麻烦的地方 ——
     * 也是 Rebar 只给事件钩子、不给现成语义的原因之一。
     */
    private fun tickRedstonePulse() {
        if (redstoneMode != RedstoneMode.PULSE) {
            lastPowered = false
            pulseTicksRemaining = 0
            return
        }
        val powered = block.isBlockPowered
        if (powered && !lastPowered) {
            pulseTicksRemaining = PULSE_DURATION_TICKS
        }
        lastPowered = powered
        if (pulseTicksRemaining > 0) pulseTicksRemaining--
    }

    /** Starts smelting in every idle slot that has a matching input. */
    open fun tryStartSmelting() {
        val effects = upgradeManager.calculateEffects()
        if (!effects.canSmelt) return
        if (waitingForFuel && !effects.usesEnergy) return

        for (slot in 0 until activeSlots) {
            if (!tasks[slot].isActive) tryStartTask(slot, effects)
        }
    }

    private fun tryStartTask(slot: Int, effects: UpgradeEffects) {
        val stack = inputInv.getUnsafeItem(slot) ?: return
        if (stack.isEmpty) return

        val compatibility = RecipeDetector.detectRecipeCompatibility(stack)
        if (!upgradeManager.isRecipeCompatible(compatibility)) return

        for (recipe in RecipeDetector.matchingFurnaceRecipes(stack)) {
            if (!recipe.ingredient.matchesIgnoringAmount(stack)) continue
            if (!outputInv.canHold(recipe.result.item)) continue

            tasks[slot].start(recipe, cookTimeTicks(effects))
            refreshProgress(slot, effects)
            return
        }
    }

    private fun cookTimeTicks(effects: UpgradeEffects): Int =
        (furnaceTier.smeltTimePerItem * effects.smeltTimeModifier * FurnaceConfig.speedMultiplier)
            .toInt()
            .coerceAtLeast(1)

    private fun progressTask(slot: Int, effects: UpgradeEffects) {
        val task = tasks[slot]

        // Upgrades can change the cook time mid-smelt; the classic mod re-reads it every tick too.
        val cookTime = cookTimeTicks(effects)
        if (task.totalTicks != cookTime) task.totalTicks = cookTime

        task.remainingTicks -= tickInterval
        if (task.remainingTicks <= 0) {
            finishTask(slot, effects)
        } else {
            refreshProgress(slot, effects)
        }
    }

    private fun finishTask(slot: Int, effects: UpgradeEffects) {
        val task = tasks[slot]
        val recipe = task.recipe
        if (recipe != null) {
            val processed = processRecipeBatch(recipe, slot, batchSizeFor(slot, recipe, effects))
            if (processed > 0) onBatchProcessed(slot, recipe, processed, effects)
        }
        task.clear()
        refreshProgress(slot, effects)
        // Pick up the next item straight away so the progress bar does not flash empty in between.
        tryStartTask(slot, effects)
    }

    /** Winds a stalled fuel furnace back; the classic mod drains two ticks of progress per tick. */
    private fun decayTask(slot: Int, effects: UpgradeEffects) {
        val task = tasks[slot]
        task.remainingTicks = (task.remainingTicks + tickInterval * 2).coerceAtMost(task.totalTicks)
        if (task.remainingTicks >= task.totalTicks) {
            task.clear()
        }
        refreshProgress(slot, effects)
    }

    /** How many items a finished task smelts at once; the rainbow furnace batches with spare fuel. */
    protected open fun batchSizeFor(
        slot: Int,
        recipe: SmeltingRebarRecipe,
        effects: UpgradeEffects
    ): Int = 1

    /** Called after a finished task produced [processed] items, so subclasses can charge for them. */
    protected open fun onBatchProcessed(
        slot: Int,
        recipe: SmeltingRebarRecipe,
        processed: Int,
        effects: UpgradeEffects
    ) = Unit

    private fun refreshProgress(slot: Int, effects: UpgradeEffects) {
        val item = progressItems[slot]
        val task = tasks[slot]
        if (task.isActive) {
            item.setItem(progressDisplay(effects))
            item.setTotalTimeTicks(task.totalTicks)
            item.setRemainingTimeTicks(task.remainingTicks)
        } else {
            item.setTotalTimeTicks(null)
            item.setItem(GuiItems.background())
        }
    }

    private fun clearAllTasks(effects: UpgradeEffects) {
        for (slot in 0 until MAX_FACTORY_SLOTS) {
            if (tasks[slot].isActive) {
                tasks[slot].clear()
                refreshProgress(slot, effects)
            }
        }
    }

    /** Drops tasks whose input no longer matches, then starts whatever can be started. */
    private fun reconcileTasks() {
        val effects = upgradeManager.calculateEffects()
        if (!effects.canSmelt) {
            clearAllTasks(effects)
            return
        }

        for (slot in 0 until activeSlots) {
            val task = tasks[slot]
            if (!task.isActive) continue

            val stack = inputInv.getUnsafeItem(slot)
            val recipe = task.recipe
            val valid = stack != null &&
                !stack.isEmpty &&
                recipe != null &&
                recipe.ingredient.matchesIgnoringAmount(stack) &&
                upgradeManager.isRecipeCompatible(RecipeDetector.detectRecipeCompatibility(stack))
            if (!valid) {
                task.clear()
                refreshProgress(slot, effects)
            }
        }

        tryStartSmelting()
    }

    private fun restoreTasks() {
        val effects = upgradeManager.calculateEffects()
        for (slot in 0 until MAX_FACTORY_SLOTS) {
            val task = tasks[slot]
            if (slot >= activeSlots || task.totalTicks <= 0) {
                task.clear()
                refreshProgress(slot, effects)
                continue
            }

            val stack = inputInv.getUnsafeItem(slot)
            val recipe = if (stack == null || stack.isEmpty) {
                null
            } else {
                RecipeDetector.matchingFurnaceRecipes(stack)
                    .firstOrNull { it.ingredient.matchesIgnoringAmount(stack) }
            }
            if (recipe == null) task.clear() else task.recipe = recipe
            refreshProgress(slot, effects)
        }
    }

    @MultiHandler(priorities = [EventPriority.LOWEST])
    override fun onFurnaceSmelt(event: FurnaceSmeltEvent, priority: EventPriority) {
        event.isCancelled = true
    }

    fun createGui(): Gui = guiFactory.createMainGui()

    fun getGuiTitle(): Component = guiTitle

    override fun onInteractedWith(event: PlayerInteractEvent, priority: EventPriority) {
        if (priority != EventPriority.NORMAL) return
        if (!event.action.isRightClick || event.hand != EquipmentSlot.HAND) return

        // 接线时不要弹界面：手里拿着线、或正在拉线（线的一端已经连上）时，把事件完整留给 Rebar 的
        // 端口实体处理。注意这里必须**在取消事件之前**返回，否则会连累 Rebar 的接线交互。
        if (isWiring(event.player)) return

        // ⚠️ 拿 RebarWrench 扳手右键是**配面**手势（切这个面的进出料模式），
        // 不该把熔炉界面弹出来。让路给扳手 —— 它自己的 item handler 会把事件取消掉。
        if (SidedIo.isWrench(event.player.inventory.itemInMainHand)) return

        // ⚠️ **设置复制器**：右键复制、潜行右键粘贴。
        //
        // 这一支必须在下面那句 `isSneaking` **之前** —— 否则潜行右键会被当成
        // "放置方块"手势让过去，粘贴就没机会执行了。
        val held = event.player.inventory.itemInMainHand

        // ⚠️ **等级升级**：右键是"升级这台熔炉"手势，**不该顺手把界面弹开**。
        // 升级自己的 handler 在 HIGH 优先级、也会取消事件，但它跑在**后面** ——
        // 这里不挡的话玩家每升一次级都会被界面糊一脸。
        if (TierUpgrade.of(held) != null) return

        if (IronFurnaceItems.isCopyTool(held)) {
            event.isCancelled = true
            val key = if (event.player.isSneaking) {
                if (applySettingsFrom(held)) "ironfurnaces.message.copy_applied"
                else "ironfurnaces.message.copy_empty"
            } else {
                copySettingsTo(held)
                "ironfurnaces.message.copy_copied"
            }
            event.player.sendActionBar(Component.translatable(key))
            return
        }

        // ⚠️ **Shift + 右键在原版里是"放置方块"手势** —— 即使对着可交互方块
        // （箱子、熔炉）也是这样：Shift 让路，方块贴上去。
        //
        // 少了这一句，玩家拿着方块 Shift+右键会被这里弹开界面，
        // 方块**放不下去**（用户实测报的"期望是放置方块"）。
        //
        // 注意这一句要在 [isWrench] **之后** —— 扳手的 Shift+右键是"反向切面"，
        // 上面已经让路给扳手了。
        if (event.player.isSneaking) return

        event.isCancelled = true
        Window.builder()
            .setUpperGui(createGui())
            .setTitle(guiTitle)
            .setViewer(event.player)
            .build()
            .open()
    }

    /** 玩家是不是在接电线（手里拿着线物品，或者已经拉着一条线）。 */
    private fun isWiring(player: Player): Boolean =
        WireConnectionService.getWirePlayerIsConnecting(player) != null ||
            RebarItem.isRebarItem<WireRebarItem>(player.inventory.itemInMainHand)

    /** 当前红石档位，面板和 WAILA 都读它。 */
    internal val currentRedstoneMode: RedstoneMode
        get() = redstoneMode

    internal fun cycleRedstoneMode(): RedstoneMode {
        val settings = sides?.settings ?: return RedstoneMode.DEFAULT
        settings.redstoneMode = settings.redstoneMode.next()
        return settings.redstoneMode
    }

    override fun getWaila(player: Player): WailaDisplay {
        val display = WailaDisplay.of(this, player)
        if (redstoneMode != RedstoneMode.IGNORED) {
            display.add(Component.translatable(redstoneMode.translationKey))
        }
        return display
    }

    override fun getVirtualInventories(): Map<String, VirtualInventory> = inventoryMap

    protected open fun setupBlockType() {
        block.type = baseMaterial
    }

    protected open fun spawnSmokeParticle() {
        block.world.spawnParticle(Particle.SMOKE, smokeParticleLocation, 1, 0.1, 0.2, 0.1, 0.01)
    }

    protected fun processRecipeBatch(recipe: SmeltingRebarRecipe, slot: Int, requestedItems: Int): Int {
        if (requestedItems <= 0) return 0

        val input = inputInv.getUnsafeItem(slot) ?: return 0
        if (input.isEmpty || !recipe.ingredient.matchesIgnoringAmount(input)) return 0

        val result = recipe.result.item
        val batchSize = min(
            min(requestedItems, input.amount),
            availableOutputBatches(result)
        )
        if (batchSize <= 0) return 0

        val combinedResult = result.clone().apply { amount = result.amount * batchSize }
        val remainder = outputInv.addItem(MACHINE_UPDATE_REASON, combinedResult)
        if (remainder != 0) {
            val inserted = combinedResult.amount - remainder
            if (inserted > 0) {
                outputInv.removeFirstSimilar(MACHINE_UPDATE_REASON, inserted, result)
            }
            LOGGER.warning("[${furnaceTier.name}] ${block.location} 输出库存状态发生竞争，已回滚烧炼结果")
            return 0
        }

        val remainingInput = input.amount - batchSize
        val updatedInput = if (remainingInput == 0) null else input.clone().apply { amount = remainingInput }
        if (!inputInv.setItem(MACHINE_UPDATE_REASON, slot, updatedInput)) {
            outputInv.removeFirstSimilar(MACHINE_UPDATE_REASON, combinedResult.amount, result)
            LOGGER.warning("[${furnaceTier.name}] ${block.location} 输入库存更新失败，已回滚烧炼结果")
            return 0
        }

        return batchSize
    }

    private fun availableOutputBatches(result: ItemStack): Int {
        var availableItems = 0
        for (slot in 0 until activeSlots) {
            val current = outputInv.getUnsafeItem(slot)
            val maxStackSize = outputInv.getMaxStackSize(slot, result)
            availableItems += when {
                current == null || current.isEmpty -> maxStackSize
                current.isSimilar(result) -> (maxStackSize - current.amount).coerceAtLeast(0)
                else -> 0
            }
        }
        return availableItems / result.amount.coerceAtLeast(1)
    }

    private fun configureRuntime() {
        if (runtimeConfigured) return
        runtimeConfigured = true

        // 面配置：熔炉是**单类型机器**（进出全是物品），所以只注册 `物品` 一条类型，
        // 但喂进去三组槽位（输入 / 燃料 / 输出）—— 面板上仍然只有一行"物品"。
        // 出厂是**底面**输出成品、**其余 5 面都能进料**
        // （见 FurnaceSides.INPUT_DEFAULT / OUTPUT_DEFAULT —— 对齐 Iron Furnaces
        // 的"进料不分面"：任何面都能塞矿石，只有底面能取成品），
        // 玩家拿 RebarWrench 扳手右键、或在 GUI 的面配置面板里点，都能改。
        //
        // ⚠️ 这里必须在 activeSlots / fuelSlotEnabled 算出来**之后**调
        // —— 组的槽位列表依赖这两个值。首次进来时它们还是初始值（1 / true），
        // 真正的值由紧接着的 applyUpgradeEffects() 落地；那里会在值变化时重建组。
        rebuildSides()

        outputInv.addPreUpdateHandler { event ->
            if (!event.isRemove && event.updateReason is PlayerUpdateReason) event.isCancelled = true
        }
        outputInv.addPostUpdateHandler { event ->
            if (event.updateReason !is MachineUpdateReason) tryStartSmelting()
        }
        inputInv.addPostUpdateHandler { event ->
            if (event.updateReason !is MachineUpdateReason) {
                waitingForFuel = false
                reconcileTasks()
            }
        }
        fuelInv.addPostUpdateHandler { event ->
            if (event.updateReason !is MachineUpdateReason) {
                waitingForFuel = false
                tryStartSmelting()
            }
        }

        setupUpgradeSlots()
    }

    /**
     * 按当前的槽位情况重建面配置的物流组。
     *
     * 什么时候要重建：
     * - 首次配置（[configureRuntime]）
     * - [applyUpgradeEffects] 里 `activeSlots` / `fuelSlotEnabled` 变了
     *   （装/拆工厂升级 → 1↔2↔4↔6 格；装/拆能量升级 → 燃料槽消失/回来）
     *
     * [top.mc506lw.rebar.sidedio.SidedIo.reattach] 会把玩家已改过的**每个格子**
     * （面 × 类型）搬过去，所以重建不会把玩家的配置冲掉。
     */
    internal fun rebuildSides() {
        sides = FurnaceSides.attach(
            rebar = this,
            inputInv = inputInv,
            outputInv = outputInv,
            fuelInv = fuelInv,
            activeSlots = activeSlots,
            displayedSlots = displayedSlots,
            fuelSlotEnabled = fuelSlotEnabled,
            facing = facing,
            usesEnergy = sidesUsesEnergy,
        )
    }

    /**
     * 把燃料槽里剩下的燃料**掉到地上**。
     *
     * ## 什么时候调
     *
     * 只有一种：`fuelSlotEnabled` 从 `true` 变成 `false` —— 也就是玩家装了
     * **工厂升级**或**能量升级**，熔炉改用能量供热，燃料槽整个消失。
     *
     * ## 为什么必须掉出来（而不是留着）
     *
     * `fuelInv` 是个**虚拟库存**，它不在世界里 —— 燃料槽一旦从 GUI 和面配置里
     * 消失，那几块煤就**没有任何界面能碰到了**：玩家看不见、拿不出、
     * 拆机器也只会得到一个空熔炉。等于凭空吞掉玩家的东西。
     *
     * （用户实测报的："工厂升级加入后应该是熔炉里的燃料直接爆出来而不是在里面"。）
     *
     * ## 为什么是"掉在脚下"而不是"塞回背包"
     *
     * 玩家可能不在旁边（升级可以是自动化设备装的），塞背包会失败。
     * `dropItemNaturally` 无论谁在附近都不会丢东西。
     */
    private fun ejectFuel() {
        val stack = fuelInv.getUnsafeItem(0) ?: return
        if (stack.isEmpty) return
        // 先清空槽位再掉 —— 顺序反了的话物品会有一瞬间同时存在于两处
        fuelInv.setItem(MACHINE_UPDATE_REASON, 0, null)
        block.world.dropItemNaturally(block.location.toCenterLocation(), stack)
    }

    private fun setupUpgradeSlots() {
        upgradeRedSlot.addPreUpdateHandler { event -> validateUpgradePlacement(event, 0) }
        upgradeGreenSlot.addPreUpdateHandler { event -> validateUpgradePlacement(event, 1) }
        upgradeBlueSlot.addPreUpdateHandler { event -> validateUpgradePlacement(event, 2) }

        val postUpdate: (xyz.xenondevs.invui.inventory.event.ItemPostUpdateEvent) -> Unit = {
            onUpgradesChanged()
        }
        upgradeRedSlot.addPostUpdateHandler(postUpdate)
        upgradeGreenSlot.addPostUpdateHandler(postUpdate)
        upgradeBlueSlot.addPostUpdateHandler(postUpdate)
    }

    private fun onUpgradesChanged() {
        waitingForFuel = false
        upgradeManager.invalidateCache()
        applyUpgradeEffects()
        displayRenderer.updateDisplayType(upgradeManager.getDisplayBlockType())
        reconcileTasks()
    }

    private fun applyUpgradeEffects() {
        val effects = upgradeManager.calculateEffects()
        fuelSystem.fuelConsumptionRate = effects.fuelConsumptionRate
        // 燃料倍率作用于"一单位燃料能烧多久"；速度倍率同时进 totalSpeedMultiplier，
        // 这样全局变快时每单位燃料烧的物品数不变（和模组的模型一致）
        fuelSystem.fuelEfficiency = effects.fuelEfficiencyBonus * FurnaceConfig.fuelMultiplier
        fuelSystem.speedMultiplier = effects.speedMultiplier * FurnaceConfig.speedMultiplier
        // Generator furnaces burn a fuel for its full burn time regardless of how fast the furnace
        // is, so that a higher tier really does yield more energy per fuel.
        fuelSystem.tierSpeedScaling = !isGeneratorMode(effects.mode)

        // A factory furnace gets as many slots as its tier unlocks (2, 4 or 6); anything else
        // smelts a single item at a time.
        val previousSlots = activeSlots
        val previousFuelEnabled = fuelSlotEnabled
        val previousDisplayed = displayedSlots
        val previousUsesEnergy = sidesUsesEnergy
        activeSlots = if (effects.usesEnergy) {
            min(effects.inputSlots, furnaceTier.factorySlots).coerceAtLeast(1)
        } else {
            1
        }
        fuelSlotEnabled = !effects.usesEnergy

        // 电口**只在用电的时候**才开。烧燃料的普通熔炉顶上不该挂一块电板 ——
        // 那是用户实测报的"非工厂状态的熔炉怎么有能量输入口"。
        // `ensurePorts()` 是幂等的（`portsCreated` + 节点存在性双重判断），
        // 所以这里每次结算都喊一声没关系。
        if (effects.usesEnergy) electricSystem.ensurePorts()

        // 燃料槽**刚被升级吃掉**（装工厂 / 能量升级 → 改用能量供热）：
        // 把里面剩的燃料**掉出来**。
        //
        // 不能让它留在 `fuelInv` 里 —— 那个槽已经从 GUI 和面配置里消失了，
        // 玩家**再也看不到、拿不到**那几块煤（用户实测报的"应该爆出来而不是在里面"）。
        if (previousFuelEnabled && !fuelSlotEnabled) ejectFuel()

        // 槽位集合 / 用电与否变了 → 面配置的组要跟着重建，否则管道按老槽位列表搬东西。
        // （比的是"最终会暴露给管道的格数"，不是裸的 activeSlots —— 见 FurnaceSides 的注释）
        sidesUsesEnergy = effects.usesEnergy
        val sidesChanged = activeSlots != previousSlots ||
            fuelSlotEnabled != previousFuelEnabled ||
            displayedSlots != previousDisplayed ||
            sidesUsesEnergy != previousUsesEnergy
        if (sidesChanged && runtimeConfigured) rebuildSides()

        for (slot in 0 until MAX_FACTORY_SLOTS) {
            outputInv.setMaxStackSize(
                slot,
                if (slot < activeSlots) Inventory.DEFAULT_MAX_STACK_SIZE else 0
            )
        }
        outputInv.notifyWindows()

        if (activeSlots < previousSlots) {
            // Slots the furnace can no longer use lose their progress; their items stay put and are
            // shown again by the GUI so nothing gets trapped.
            for (slot in activeSlots until previousSlots) {
                tasks[slot].clear()
                refreshProgress(slot, effects)
            }
        }

        cachedProgressLabel = null
        cachedProgressStack = null

        // Upgrade layout changed: drop any stale grid demand/production so the furnace
        // does not keep requesting power it no longer needs.
        if (electricSystem.hasPorts) electricSystem.idle()
    }

    private fun progressDisplay(effects: UpgradeEffects): ItemStack {
        val label = rainbowSpeedLabel ?: run {
            val effectiveTime = cookTimeTicks(effects)
            val multiplier = 200.0 / effectiveTime
            if (multiplier == multiplier.toInt().toDouble()) {
                "${multiplier.toInt()}x"
            } else {
                "%.1fx".format(Locale.ROOT, multiplier)
            }
        }

        val cached = cachedProgressStack
        if (cached != null && cachedProgressLabel == label) return cached

        return ItemStackBuilder.of(Material.FLINT_AND_STEEL)
            .name(Component.translatable("ironfurnaces.gui.smelt_progress.name"))
            .lore(
                Component.translatable(
                    "ironfurnaces.gui.smelt_progress.lore",
                    RebarArgument.of("speed", label)
                )
            )
            .build()
            .also {
                cachedProgressLabel = label
                cachedProgressStack = it
            }
    }

    private fun validateUpgradePlacement(event: ItemPreUpdateEvent, slotIndex: Int) {
        val newItem = event.newItem ?: return
        if (newItem.isEmpty) return

        val upgradeType = UpgradeEffectManager.getUpgradeType(newItem)
        if (upgradeType == null || !UpgradeEffectManager.canPlaceInSlot(upgradeType, slotIndex)) {
            event.isCancelled = true
        }
    }

    private fun restorePersistentState(pdc: PersistentDataContainer) {
        // ⚠️ 这一段必须在下面那个 `return` **之前** —— 老存档没有 TASK_TICKS 数组时
        // 会提前返回，放在后面就读不到了。
        owner = pdc.get(IronFurnaceKeys.OWNER, PersistentDataType.STRING)
            ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }

        // autoSplit 是熔炉自己的开关（不是面设置），所以存在方块 PDC 上
        autoSplit = pdc.getOrDefault(IronFurnaceKeys.AUTO_SPLIT, PersistentDataType.BOOLEAN, false)

        fuelSystem.restore(pdc)
        if (pdc.has(IronFurnaceKeys.ENERGY_CURRENT) || pdc.has(IronFurnaceKeys.ENERGY_TOTAL_PRODUCED)) {
            energySystem.restore(pdc)
        }
        // ⚠️ 红石档位**不从这里恢复** —— Sided-IO 在自己的 `sidedio:machine_settings`
        // 容器里读，见 `SidedIoRegistry.onDeserialize`。这里的老键
        // （`IronFurnaceKeys.REDSTONE_MODE`）留着不读，兼容老存档也不会出错
        // （只是那台机器的红石会退回"忽略"，玩家在面板上重设一次即可）。

        val totals = pdc.get(IronFurnaceKeys.TASK_TICKS_TOTAL, PersistentDataType.INTEGER_ARRAY)
        val remaining = pdc.get(IronFurnaceKeys.TASK_TICKS_REMAINING, PersistentDataType.INTEGER_ARRAY)
        if (totals == null || remaining == null) return

        for (slot in 0 until minOf(MAX_FACTORY_SLOTS, totals.size, remaining.size)) {
            if (totals[slot] > 0 && remaining[slot] > 0) {
                tasks[slot].totalTicks = totals[slot]
                tasks[slot].remainingTicks = remaining[slot]
            }
        }
    }
}

class InvertedProgressItem(item: xyz.xenondevs.invui.item.Item) : ProgressItem(item, true) {
    @Suppress("UnstableApiUsage")
    override fun getItemProvider(viewer: Player): xyz.xenondevs.invui.item.ItemProvider {
        if (totalTime == null) return super.getItemProvider(viewer)

        val stack = super.getItemProvider(viewer).get()
        val builder = ItemStackBuilder.of(stack.clone())
        val currentDamage = builder.get(DataComponentTypes.DAMAGE) ?: 0
        val maxDamage = builder.get(DataComponentTypes.MAX_DAMAGE) ?: 1000

        var invertedDamage = (maxDamage - currentDamage).coerceIn(0, maxDamage)
        if (invertedDamage <= 1) invertedDamage = maxDamage
        builder.set(DataComponentTypes.DAMAGE, invertedDamage)
        return builder
    }
}
