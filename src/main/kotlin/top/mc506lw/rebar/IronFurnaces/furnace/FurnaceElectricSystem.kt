package top.mc506lw.rebar.ironfurnaces.furnace

import io.github.pylonmc.rebar.block.interfaces.SimpleElectricRebarBlock
import io.github.pylonmc.rebar.electricity.nodes.ElectricConsumerNode
import io.github.pylonmc.rebar.electricity.nodes.ElectricNodeType
import io.github.pylonmc.rebar.electricity.nodes.ElectricProducerNode
import io.github.pylonmc.rebar.util.position.position
import org.bukkit.block.BlockFace

/**
 * Bridges an iron furnace to Rebar's electric network.
 *
 * Mirrors the classic Iron Furnaces mod, where a furnace block exposes a Forge energy capability:
 * a generator-upgraded furnace feeds power into the network, while an industrial (energy-powered)
 * furnace draws power from it.
 *
 * The furnace only ever exposes a *single* terminal, on the top face, exactly like Pylon's electric
 * furnace. The terminal itself is a plain connector; the producing and consuming nodes are attached
 * to it internally, so the same terminal can both push power out and pull power in depending on the
 * installed upgrade.
 */
class FurnaceElectricSystem(
    private val block: SimpleElectricRebarBlock
) {
    companion object {
        /**
         * Joules a factory furnace spends on one item of a 200-tick recipe, matching the classic
         * mod's `recipe.getCookingTime() * 20`. Because the draw per tick is this amount divided by
         * the furnace's cook time, a faster furnace pulls proportionally more power per tick while
         * still spending the same energy per item.
         */
        const val ENERGY_PER_ITEM = 4000.0

        /**
         * Rebar places the port's model at `radius * 1.02` from the block centre, so 0.5 (Rebar's
         * own default, and what Pylon's electric furnace uses) puts the plate just proud of the
         * block face. The previous 0.35 sank it inside the block, where the opaque metal body hid
         * it completely.
         */
        private const val PORT_RADIUS = 0.5
        private val PORT_FACE = BlockFace.UP

        private const val PORT_NODE = "connector_0"
        private const val PRODUCER_NODE = "producer_0"
        private const val CONSUMER_NODE = "consumer_0"
    }

    private var portsCreated = false

    /**
     * Creates the single terminal exactly once. Ports are never re-created for an existing block:
     * Rebar restores the stored nodes from the block data on load, and creating them again would
     * leave a duplicate, unreachable set of nodes behind.
     */
    fun ensurePorts() {
        if (portsCreated) return
        portsCreated = true

        // The port has to be created first: it is the node that ends up being named "connector_0".
        if (block.getElectricNode(PORT_NODE) == null) {
            block.createSimpleElectricPort(ElectricNodeType.CONNECTOR, PORT_FACE, PORT_RADIUS)
        }

        // Adding a node through the simple API also links it to the block's internal connector, so
        // both of these end up reachable through the single top terminal.
        val position = block.block.position
        if (block.getElectricNode(PRODUCER_NODE) == null) {
            block.addElectricNode(ElectricProducerNode(PRODUCER_NODE, position, 0.0))
        }
        if (block.getElectricNode(CONSUMER_NODE) == null) {
            block.addElectricNode(ElectricConsumerNode(CONSUMER_NODE, position, 0.0))
        }
    }

    val hasPorts: Boolean
        get() = portsCreated

    /** Power currently pushed onto the network, in watts. */
    var powerProduced: Double
        get() = if (portsCreated) block.powerProduced else 0.0
        set(value) {
            if (!portsCreated) return
            block.powerProduced = value.coerceAtLeast(0.0)
        }

    /** Power currently requested from the network, in watts. */
    var requiredPower: Double
        get() = if (portsCreated) block.requiredPower else 0.0
        set(value) {
            if (!portsCreated) return
            block.requiredPower = value.coerceAtLeast(0.0)
        }

    /**
     * True when the network is supplying everything this furnace needs.
     *
     * 注意：不能只看 `block.isPowered`。Rebar 的 `ElectricConsumerNode.isPowered` 只是一个普通
     * boolean 字段，由**网络 tick** 时写入；而没接线时这个节点根本不进任何网络，字段就会一直保持
     * 初始值 `true` —— 表现就是"一台电都没接的工厂炉照样在烧"。所以这里额外要求端子上真的挂着电线。
     */
    val isPowered: Boolean
        get() = portsCreated && FurnaceWires.hasAnyWire(block.block.location.toCenterLocation()) && block.isPowered

    /** Stops both producing and consuming, e.g. when idling or out of fuel. */
    fun idle() {
        powerProduced = 0.0
        requiredPower = 0.0
    }
}
