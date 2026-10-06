package top.mc506lw.rebar.ironfurnaces.furnace

import io.github.pylonmc.rebar.block.context.BlockBreakContext
import io.github.pylonmc.rebar.block.context.BlockCreateContext
import io.github.pylonmc.rebar.block.interfaces.BlockBreakRebarBlockHandler
import io.github.pylonmc.rebar.block.interfaces.DirectionalRebarBlock
import io.github.pylonmc.rebar.block.interfaces.EntityHolderRebarBlock
import io.github.pylonmc.rebar.block.interfaces.FurnaceRebarBlockHandler
import io.github.pylonmc.rebar.block.interfaces.InteractRebarBlockHandler
import io.github.pylonmc.rebar.block.interfaces.LogisticRebarBlock
import io.github.pylonmc.rebar.block.interfaces.RecipeProcessorRebarBlock
import io.github.pylonmc.rebar.block.interfaces.TickingRebarBlock
import io.github.pylonmc.rebar.block.interfaces.VirtualInventoryRebarBlock
import io.github.pylonmc.rebar.event.api.annotation.MultiHandler
import io.github.pylonmc.rebar.i18n.RebarArgument
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder
import io.github.pylonmc.rebar.logistics.LogisticGroupType
import io.github.pylonmc.rebar.recipe.vanilla.SmeltingRecipeType
import io.github.pylonmc.rebar.recipe.vanilla.SmeltingRebarRecipe
import io.github.pylonmc.rebar.util.MachineUpdateReason
import io.github.pylonmc.rebar.util.gui.GuiItems
import io.github.pylonmc.rebar.util.gui.ProgressItem
import io.github.pylonmc.rebar.waila.WailaDisplay
import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.EventPriority
import org.bukkit.event.inventory.FurnaceSmeltEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataContainer
import top.mc506lw.rebar.ironfurnaces.IronFurnaceKeys
import xyz.xenondevs.invui.gui.Gui
import xyz.xenondevs.invui.inventory.Inventory
import xyz.xenondevs.invui.inventory.VirtualInventory
import xyz.xenondevs.invui.inventory.event.ItemPreUpdateEvent
import xyz.xenondevs.invui.inventory.event.PlayerUpdateReason
import xyz.xenondevs.invui.window.Window
import java.util.Locale
import kotlin.math.min

abstract class AbstractIronFurnace : IronFurnaceBase,
    VirtualInventoryRebarBlock,
    DirectionalRebarBlock,
    TickingRebarBlock,
    LogisticRebarBlock,
    FurnaceRebarBlockHandler,
    RecipeProcessorRebarBlock<SmeltingRebarRecipe>,
    EntityHolderRebarBlock,
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

    companion object {
        private val LOGGER = java.util.logging.Logger.getLogger("IronFurnace")
        private val MACHINE_UPDATE_REASON = MachineUpdateReason()
        private const val MAX_OUTPUT_SLOTS = 3
    }

    override var disableBlockTextureEntity = true

    protected val inputInv = VirtualInventory(1)
    protected val outputInv = VirtualInventory(MAX_OUTPUT_SLOTS)
    protected val fuelInv = VirtualInventory(1)

    protected val upgradeRedSlot = VirtualInventory(1)
    protected val upgradeGreenSlot = VirtualInventory(1)
    protected val upgradeBlueSlot = VirtualInventory(1)

    protected val upgradeManager by lazy(LazyThreadSafetyMode.NONE) {
        UpgradeEffectManager(upgradeRedSlot, upgradeGreenSlot, upgradeBlueSlot)
    }

    private val energySystemDelegate = lazy(LazyThreadSafetyMode.NONE) { FurnaceEnergySystem() }
    protected val energySystem by energySystemDelegate

    protected val fuelSystem by lazy(LazyThreadSafetyMode.NONE) {
        FurnaceFuelSystem(block, furnaceTier, fuelInv)
    }

    protected val displayRenderer by lazy(LazyThreadSafetyMode.NONE) { createDisplayRenderer() }

    private val guiFactory by lazy(LazyThreadSafetyMode.NONE) {
        FurnaceGuiFactory(
            furnace = this,
            inputInv = inputInv,
            outputInv = outputInv,
            fuelInv = fuelInv,
            fuelSystem = fuelSystem,
            recipeProgressItem = recipeProgressItem,
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
    private var activeOutputSlots = 1
    private var waitingForFuel = false
    private var cachedProgressLabel: String? = null
    private var cachedProgressStack: ItemStack? = null
    private val smokeParticleLocation by lazy(LazyThreadSafetyMode.NONE) {
        block.location.toCenterLocation().add(0.0, 0.8, 0.0)
    }

    init {
        setRecipeType(SmeltingRecipeType)
        recipeProgressItem = InvertedProgressItem(GuiItems.background())
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

    internal val displayedOutputSlots: Int
        get() {
            for (index in MAX_OUTPUT_SLOTS - 1 downTo activeOutputSlots) {
                if (outputInv.hasItem(index)) return index + 1
            }
            return activeOutputSlots
        }

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
        applyUpgradeEffects()

        val displayType = upgradeManager.getDisplayBlockType()
        displayRenderer.ensureFrontFace(displayType)
        displayRenderer.updateBurningState(fuelSystem.isBurning, displayType)
    }

    override fun postLoad() {
        super.postLoad()
        configureRuntime()
        upgradeManager.invalidateCache()
        applyUpgradeEffects()
        restoreProgressItemBehavior()
        fuelSystem.refreshDisplay()
        setupBlockType()

        val displayType = upgradeManager.getDisplayBlockType()
        displayRenderer.resetState()
        displayRenderer.ensureFrontFace(displayType)
        displayRenderer.updateFrontFace()
        displayRenderer.refreshState(fuelSystem.isBurning, displayType)

        reconcileRecipeWithInput()
        if (!isProcessingRecipe) resetRecipeProgressDisplay()
    }

    override fun write(pdc: PersistentDataContainer) {
        super.write(pdc)
        fuelSystem.write(pdc)
        if (energySystemDelegate.isInitialized()) energySystem.write(pdc)
    }

    override fun onPostBlockBreak(context: BlockBreakContext) {
        tryRemoveAllEntities()
    }

    override fun tick() {
        val effects = upgradeManager.calculateEffects()

        when (effects.mode) {
            FurnaceMode.GENERATOR_ONLY,
            FurnaceMode.GENERATOR_BLAST,
            FurnaceMode.GENERATOR_SMOKER -> handleGeneratorTick(effects)
            else -> handleNormalTick(effects)
        }

        displayRenderer.updateBurningState(fuelSystem.isBurning, upgradeManager.getDisplayBlockType())
    }

    open fun tryStartSmelting() {
        if (isProcessingRecipe) return

        val effects = upgradeManager.calculateEffects()
        if (!effects.canSmelt) return
        if (waitingForFuel && !effects.usesEnergy) return

        val stack = inputInv.getUnsafeItem(0) ?: return
        if (stack.isEmpty) return

        val compatibility = RecipeDetector.detectRecipeCompatibility(stack)
        if (!upgradeManager.isRecipeCompatible(compatibility)) return

        val previousRecipe = lastRecipe
        if (previousRecipe != null && tryStartSmelting(previousRecipe, stack, effects)) return

        for (recipe in RecipeDetector.matchingFurnaceRecipes(stack)) {
            if (recipe !== previousRecipe && tryStartSmelting(recipe, stack, effects)) return
        }
    }

    override fun onRecipeFinished(recipe: SmeltingRebarRecipe) {
        processRecipeBatch(recipe, 1)
        tryStartSmelting()
        if (!isProcessingRecipe) resetRecipeProgressDisplay()
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

    override fun getWaila(player: Player): WailaDisplay = WailaDisplay.of(this, player)

    override fun getVirtualInventories(): Map<String, VirtualInventory> = inventoryMap

    protected open fun setupBlockType() {
        block.type = baseMaterial
    }

    protected open fun spawnSmokeParticle() {
        block.world.spawnParticle(Particle.SMOKE, smokeParticleLocation, 1, 0.1, 0.2, 0.1, 0.01)
    }

    protected fun processRecipeBatch(recipe: SmeltingRebarRecipe, requestedItems: Int): Int {
        if (requestedItems <= 0) return 0

        val input = inputInv.getUnsafeItem(0) ?: return 0
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
        if (!inputInv.setItem(MACHINE_UPDATE_REASON, 0, updatedInput)) {
            outputInv.removeFirstSimilar(MACHINE_UPDATE_REASON, combinedResult.amount, result)
            LOGGER.warning("[${furnaceTier.name}] ${block.location} 输入库存更新失败，已回滚烧炼结果")
            return 0
        }

        return batchSize
    }

    private fun availableOutputBatches(result: ItemStack): Int {
        var availableItems = 0
        for (slot in 0 until activeOutputSlots) {
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
                reconcileRecipeWithInput()
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

        if (!upgradeManager.calculateEffects().canSmelt && isProcessingRecipe) {
            stopRecipe()
            resetRecipeProgressDisplay()
        } else {
            reconcileRecipeWithInput()
        }
    }

    private fun applyUpgradeEffects() {
        val effects = upgradeManager.calculateEffects()
        fuelSystem.fuelConsumptionRate = effects.fuelConsumptionRate
        fuelSystem.fuelEfficiency = effects.fuelEfficiencyBonus
        fuelSystem.speedMultiplier = effects.speedMultiplier

        activeOutputSlots = effects.outputSlots.coerceIn(1, MAX_OUTPUT_SLOTS)
        for (slot in 0 until MAX_OUTPUT_SLOTS) {
            outputInv.setMaxStackSize(
                slot,
                if (slot < activeOutputSlots) Inventory.DEFAULT_MAX_STACK_SIZE else 0
            )
        }
        outputInv.notifyWindows()
        cachedProgressLabel = null
        cachedProgressStack = null
    }

    private fun handleNormalTick(effects: UpgradeEffects) {
        if (!effects.canSmelt) return

        if (isProcessingRecipe) {
            val input = inputInv.getUnsafeItem(0)
            if (input == null || input.isEmpty) {
                stopRecipe()
                resetRecipeProgressDisplay()
            }
        }

        tryStartSmelting()

        var hadHeat = effects.usesEnergy
        if (!effects.usesEnergy) {
            hadHeat = fuelSystem.isBurning
            if (!hadHeat && isProcessingRecipe) {
                hadHeat = fuelSystem.consumeFuel()
                waitingForFuel = !hadHeat
            }
            fuelSystem.updateFuelState(tickInterval)
        }

        if (isProcessingRecipe && hadHeat) {
            progressRecipe(tickInterval)
            spawnSmokeParticle()
        } else if (isProcessingRecipe) {
            stopRecipe()
            resetRecipeProgressDisplay()
        }
    }

    private fun handleGeneratorTick(effects: UpgradeEffects) {
        if (!fuelSystem.isBurning) fuelSystem.consumeFuel()

        if (fuelSystem.isBurning) {
            energySystem.convertHeatToEnergy(tickInterval.toDouble(), effects.generatorPowerMultiplier)
            fuelSystem.updateFuelState(tickInterval, effects.generatorSpeedMultiplier)
        } else {
            fuelSystem.updateFuelState(tickInterval)
        }
    }

    private fun reconcileRecipeWithInput() {
        if (isProcessingRecipe) {
            val input = inputInv.getUnsafeItem(0)
            val recipe = currentRecipe
            val effects = upgradeManager.calculateEffects()
            val isValid = effects.canSmelt &&
                input != null &&
                !input.isEmpty &&
                recipe != null &&
                recipe.ingredient.matchesIgnoringAmount(input) &&
                upgradeManager.isRecipeCompatible(RecipeDetector.detectRecipeCompatibility(input))
            if (!isValid) {
                stopRecipe()
                resetRecipeProgressDisplay()
            }
        }
        tryStartSmelting()
    }

    private fun tryStartSmelting(
        recipe: SmeltingRebarRecipe,
        stack: ItemStack,
        effects: UpgradeEffects
    ): Boolean {
        if (!recipe.ingredient.matchesIgnoringAmount(stack) || !outputInv.canHold(recipe.result.item)) return false

        val actualTime = (furnaceTier.smeltTimePerItem * effects.smeltTimeModifier)
            .toInt()
            .coerceAtLeast(1)
        recipeProgressItem.setItem(progressDisplay(effects))
        startRecipe(recipe, actualTime)
        return true
    }

    private fun progressDisplay(effects: UpgradeEffects): ItemStack {
        val label = rainbowSpeedLabel ?: run {
            val effectiveTime = (furnaceTier.smeltTimePerItem * effects.smeltTimeModifier)
                .toInt()
                .coerceAtLeast(1)
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

    private fun resetRecipeProgressDisplay() {
        recipeProgressItem.setItem(GuiItems.background())
    }

    private fun restoreProgressItemBehavior() {
        val replacement = InvertedProgressItem(GuiItems.background())
        recipeProgressItem = replacement
        recipeTimeTicks?.let(replacement::setTotalTimeTicks)
        recipeTicksRemaining?.let(replacement::setRemainingTimeTicks)
        if (isProcessingRecipe) {
            replacement.setItem(progressDisplay(upgradeManager.calculateEffects()))
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
