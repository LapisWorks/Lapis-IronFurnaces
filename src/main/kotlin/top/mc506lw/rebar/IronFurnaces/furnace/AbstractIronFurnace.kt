package top.mc506lw.rebar.ironfurnaces.furnace

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
import io.github.pylonmc.rebar.electricity.WireEntity
import io.github.pylonmc.rebar.config.RebarConfig
import io.github.pylonmc.rebar.event.api.annotation.MultiHandler
import io.github.pylonmc.rebar.i18n.RebarArgument
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder
import io.github.pylonmc.rebar.logistics.LogisticGroupType
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
import org.bukkit.entity.Player
import org.bukkit.event.EventPriority
import org.bukkit.event.inventory.FurnaceSmeltEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import top.mc506lw.rebar.ironfurnaces.IronFurnaceKeys
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
        if (context is BlockCreateContext.PlayerPlace) facing = context.facing
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
    }

    override var disableBlockTextureEntity = true

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
    private var redstoneMode = RedstoneMode.IGNORED
    private var waitingForFuel = false

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
        electricSystem.ensurePorts()
        applyUpgradeEffects()

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
        electricSystem.ensurePorts()

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
        pdc.set(IronFurnaceKeys.REDSTONE_MODE, PersistentDataType.INTEGER, redstoneMode.ordinal)
    }

    override fun onPostBlockBreak(context: BlockBreakContext) {
        tryRemoveAllEntities()
        removeConnectedWires()
    }

    /**
     * Rebar 在方块被破坏时**不会**清理挂在它端口上的电线：`WireEntity` 只有在自己被移除时才会
     * `disconnectFrom`，所以方块没了线还留在原地，指向一个已经不存在的节点。悬空的线会让 Rebar
     * 之后任何一次节点移除都抛 NPE（`ElectricPortEntity.getConnectedWires()` 读 `port.node` 查到空），
     * 所以必须清掉。
     *
     * 已知限制：`loadedWires` 只包含已加载的电线，如果线的另一端在未加载的区块里，这条线清理不到。
     */
    private fun removeConnectedWires() {
        FurnaceWires.removeAt(block.location.toCenterLocation())
    }

    override fun tick() {
        val effects = upgradeManager.calculateEffects()

        if (isRedstoneBlocked()) {
            // The classic mod stops the whole machine; we pause it instead of voiding the progress
            // and the fuel that is already burning.
            electricSystem.idle()
        } else if (isGeneratorMode(effects.mode)) {
            handleGeneratorTick(effects)
        } else {
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

    private fun handleGeneratorTick(effects: UpgradeEffects) {
        if (!fuelSystem.isBurning) fuelSystem.consumeFuel()

        if (fuelSystem.isBurning) {
            val wattsPerTick = FurnaceConfig.generation(furnaceTier) * effects.generatorPowerMultiplier
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

    private fun isRedstoneBlocked(): Boolean = when (redstoneMode) {
        RedstoneMode.IGNORED -> false
        RedstoneMode.LOW -> block.isBlockPowered
        RedstoneMode.HIGH -> !block.isBlockPowered
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

        event.isCancelled = true
        Window.builder()
            .setUpperGui(createGui())
            .setTitle(guiTitle)
            .setViewer(event.player)
            .build()
            .open()
    }

    /** Current redstone gate, shown by the button in the bottom-right of the furnace GUI. */
    internal val currentRedstoneMode: RedstoneMode
        get() = redstoneMode

    internal fun cycleRedstoneMode(): RedstoneMode {
        redstoneMode = redstoneMode.next()
        return redstoneMode
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

        createLogisticGroup("input", LogisticGroupType.INPUT, inputInv)
        createLogisticGroup("output", LogisticGroupType.OUTPUT, outputInv)

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
        activeSlots = if (effects.usesEnergy) {
            min(effects.inputSlots, furnaceTier.factorySlots).coerceAtLeast(1)
        } else {
            1
        }
        fuelSlotEnabled = !effects.usesEnergy

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
        fuelSystem.restore(pdc)
        if (pdc.has(IronFurnaceKeys.ENERGY_CURRENT) || pdc.has(IronFurnaceKeys.ENERGY_TOTAL_PRODUCED)) {
            energySystem.restore(pdc)
        }
        redstoneMode = RedstoneMode.fromOrdinal(
            pdc.getOrDefault(IronFurnaceKeys.REDSTONE_MODE, PersistentDataType.INTEGER, 0)
        )

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
