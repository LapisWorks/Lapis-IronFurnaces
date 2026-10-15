package top.mc506lw.rebar.ironfurnaces.furnace

import io.github.pylonmc.rebar.block.RebarBlock
import io.github.pylonmc.rebar.block.context.BlockCreateContext
import io.github.pylonmc.rebar.block.interfaces.EntityHolderRebarBlock
import io.github.pylonmc.rebar.block.interfaces.InteractRebarBlockHandler
import io.github.pylonmc.rebar.block.interfaces.SimpleElectricRebarBlock
import io.github.pylonmc.rebar.block.interfaces.TickingRebarBlock
import io.github.pylonmc.rebar.electricity.nodes.ElectricConsumerNode
import io.github.pylonmc.rebar.entity.display.TextDisplayBuilder
import io.github.pylonmc.rebar.entity.display.transform.TransformBuilder
import io.github.pylonmc.rebar.electricity.nodes.ElectricNodeType
import io.github.pylonmc.rebar.event.api.annotation.MultiHandler
import io.github.pylonmc.rebar.i18n.RebarArgument
import io.github.pylonmc.rebar.util.position.position
import io.github.pylonmc.rebar.util.gui.unit.UnitFormat
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.event.EventPriority
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import top.mc506lw.rebar.ironfurnaces.IronFurnaceKeys
import top.mc506lw.rebar.ironfurnaces.items.ItemHeater
import java.util.Locale

/**
 * **无线能量加热器** —— 复刻 26.3 的 `BlockWirelessEnergyHeater`。
 *
 * ## 它解决什么问题
 *
 * 熔炉要烧东西就得有燃料。加热器是一块 **1,000,000 J 的电池**：
 * 接上电充满，然后用**加热棒**把"这台加热器的坐标"带在身上 ——
 * 把棒放进任意一台熔炉的**燃料槽**，那台熔炉就改吃电，不再烧煤。
 *
 * ```
 *   电网 ──▶ [无线能量加热器]  ──(加热棒刻上坐标)──▶  熔炉燃料槽
 *                     ▲                                      │
 *                     └────────── 每烧一次扣电 ◀─────────────┘
 * ```
 *
 * ## 和 26.3 的两处差异
 *
 * - **不需要 GUI**：那边要把棒放进加热器的槽里才会被刻坐标；我们改成
 *   **拿着棒右键加热器方块**即可 —— 少一个界面，操作更直接。
 * - **不强制加载区块**：熔炉要扣电时会 `BlockStorage.get` 一下加热器所在的位置，
 *   区块没加载就拿不到、这一格就不算通电（见 [drain]）。
 *
 * ## 电从哪来
 *
 * 走 Rebar 的电网：本方块挂一个**消费节点**，每 tick 按"还差多少"请求功率
 * （上限 [CHARGE_RATE]，免得把电网一次抽干）。和熔炉那边用的是同一套桥
 * （见 [FurnaceElectricSystem]）。
 */
class WirelessEnergyHeater :
    RebarBlock,
    TickingRebarBlock,
    SimpleElectricRebarBlock,
    InteractRebarBlockHandler,
    EntityHolderRebarBlock {

    companion object {
        /** 缓冲容量（J），对齐 26.3 的 `FEnergyStorage(1000000, ...)`。 */
        const val CAPACITY = 1_000_000.0

        /**
         * 每 tick 最多从电网拉多少（W）。
         *
         * 不设上限的话，一个刚放下的空加热器会把整张电网瞬间抽干，
         * 旁边的机器全部闪一下。
         */
        private const val CHARGE_RATE = 5_000.0

        private const val PORT_RADIUS = 0.5
        private val PORT_FACE = BlockFace.UP
        private const val PORT_NODE = "connector_0"
        private const val CONSUMER_NODE = "consumer_0"

        /**
         * 一根加热棒"一次"提供多少 tick 的燃烧 —— 对齐一块煤（200 tick）。
         *
         * 放在这里（而不是 `FurnaceFuelSystem` 的私有常量）是因为**原版熔炉**那条路
         * （见 `VanillaFurnaceFuelListener`）也要用同一个数。
         */
        internal const val FUEL_TICKS = 200

        /**
         * 一根加热棒"一次"要花加热器多少电（J）。
         *
         * 和工厂模式"每件物品耗电"（[FurnaceElectricSystem.ENERGY_PER_ITEM]，4000 J）
         * 同量级 —— 一次烧炼的能耗口径统一，玩家算账时不用记两套数字。
         */
        internal const val ENERGY_PER_USE = 4000.0
    }

    /** 缓冲里现在有多少（J）。 */
    private var stored = 0.0

    private var portsCreated = false

    constructor(block: Block, context: BlockCreateContext) : super(block, context)

    constructor(block: Block, pdc: PersistentDataContainer) : super(block, pdc) {
        stored = pdc.getOrDefault(IronFurnaceKeys.HEATER_STORED, PersistentDataType.DOUBLE, 0.0)
            .takeIf { it.isFinite() && it >= 0.0 }
            ?.coerceAtMost(CAPACITY)
            ?: 0.0
    }

    override fun write(pdc: PersistentDataContainer) {
        pdc.set(IronFurnaceKeys.HEATER_STORED, PersistentDataType.DOUBLE, stored)
    }

    override fun postInitialise() {
        ensurePort()
    }

    override fun tick() {
        ensurePort()

        // 充满就不再请求（`requiredPower = 0`）—— 不然会一直占着电网的额度
        val room = CAPACITY - stored
        if (room <= 0.0) {
            requiredPower = 0.0
            return
        }

        val request = minOf(room, CHARGE_RATE)
        requiredPower = request
        // `isPowered` = 电网把我们要的都给了（见 FurnaceElectricSystem 的注释：
        // 不能只看 `block.isPowered`，没接线时那个字段一直是初始值 true）
        if (isPowered) {
            stored += request
            refreshDisplays()
        }
    }

    /**
     * 给加热棒用：从缓冲里扣 [amount] 焦耳。够就扣掉返回 true。
     *
     * 加热器方块所在区块**没加载**时，调用方根本拿不到这个对象，所以这里不用再判。
     */
    fun drain(amount: Double): Boolean {
        if (!amount.isFinite() || amount <= 0.0) return false
        if (stored < amount) return false
        stored -= amount
        refreshDisplays()
        return true
    }

    fun storedEnergy(): Double = stored

    fun energyPercent(): Double = stored / CAPACITY * 100.0

    /**
     * 端口只建一次。Rebar 读盘时会自己把存下来的节点还原，
     * 再建一遍会留下一组**连不上、也删不掉**的重复节点。
     */
    private fun ensurePort() {
        if (portsCreated) return
        portsCreated = true

        // 端子要先建 —— 最后那个叫 `connector_0` 的就是它
        if (getElectricNode(PORT_NODE) == null) {
            createSimpleElectricPort(ElectricNodeType.CONNECTOR, PORT_FACE, PORT_RADIUS)
        }
        // 走简单 API 加节点会自动连到端子上，所以一个端子就够
        if (getElectricNode(CONSUMER_NODE) == null) {
            addElectricNode(ElectricConsumerNode(CONSUMER_NODE, block.position, 0.0))
        }
    }

    /**
     * 右键加热器：
     *
     * - **手里拿着加热棒** → 把坐标刻进棒里（见 [ItemHeater.bind]）
     * - **别的** → 在**点的那一面**挂一个浮空的储能显示，再点一下取下来
     *
     * ## 第二条是照抄 Pylon 的 `Capacitor`
     *
     * Pylon 的能量存储器就是这么干的：`TextDisplay` 挂在
     * `方块中心 + 面朝向 × 0.5001`（刚好贴在面外侧），`lookAlong(面朝向)` 让它正对玩家；
     * **同一个面再点一次就移除**。不是动作栏提示 —— 我第一版写成动作栏，
     * 那既看不见又不像 Pylon。
     *
     * 每个面一个独立的名字（`text_north` / `text_up`…），所以六个面可以同时挂。
     */
    @MultiHandler(priorities = [EventPriority.NORMAL])
    override fun onInteractedWith(event: PlayerInteractEvent, priority: EventPriority) {
        if (!event.action.isRightClick || event.hand != EquipmentSlot.HAND) return
        event.isCancelled = true

        val held = event.player.inventory.itemInMainHand
        if (ItemHeater.isHeater(held)) {
            ItemHeater.bind(held, block)
            event.player.sendActionBar(Component.translatable("ironfurnaces.message.heater_bound"))
            return
        }

        // 储能显示：在点的那一面挂 / 取
        val face = event.blockFace
        if (face == BlockFace.SELF) return
        val name = displayName(face)

        val existing = getHeldEntity(TextDisplay::class.java, name)
        if (existing != null) {
            existing.remove()
            return
        }

        val direction = face.direction
        addEntity(
            name,
            TextDisplayBuilder()
                .transformation(TransformBuilder().lookAlong(direction.toVector3f()))
                .build(block.location.toCenterLocation().add(direction.multiply(0.5001)))
        )
        refreshDisplays()
    }

    /** 所有已挂的显示都刷新成当前储能。 */
    private fun refreshDisplays() {
        val text = formatEnergy(stored)
        for (face in BlockFace.entries) {
            getHeldEntity(TextDisplay::class.java, displayName(face))?.text(text)
        }
    }

    override fun getWaila(player: Player): io.github.pylonmc.rebar.waila.WailaDisplay =
        io.github.pylonmc.rebar.waila.WailaDisplay.of(this, player).add(formatEnergy(stored))

    private fun displayName(face: BlockFace): String = "text_" + face.name.lowercase(Locale.ROOT)

    /** 和 Pylon 的 `Capacitor.formatEnergy` 一致：J 单位 + 自动换前缀 + 一位小数。 */
    private fun formatEnergy(energy: Double): Component =
        UnitFormat.JOULES.format(energy)
            .selectPrefixAndRescale()
            .decimalPlaces(1)
            .asComponent()
}
