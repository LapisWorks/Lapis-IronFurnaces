package top.mc506lw.rebar.ironfurnaces.furnace

import io.github.pylonmc.rebar.block.interfaces.SimpleElectricRebarBlock
import io.github.pylonmc.rebar.electricity.nodes.ElectricNodeType
import org.bukkit.block.BlockFace

/**
 * Bridges an iron furnace to Rebar's electric network.
 *
 * Mirrors the classic Iron Furnaces mod, where a furnace block exposes a Forge energy
 * capability: a generator-upgraded furnace feeds power into the network, while an
 * industrial (energy-powered) furnace draws power from it.
 *
 * Both ports are created once and stay attached for the lifetime of the block; the
 * active upgrade configuration only changes how much power is produced or requested,
 * which maps directly onto [SimpleElectricRebarBlock.powerProduced] and
 * [SimpleElectricRebarBlock.requiredPower].
 */
class FurnaceElectricSystem(
    private val block: SimpleElectricRebarBlock
) {
    companion object {
        /**
         * FE/tick produced by a generator furnace at 1x, matching the classic mod's
         * 16000 FE heater charge lasting 1600 furnace ticks.
         */
        const val BASE_POWER_PER_TICK = 10.0

        /** FE/tick drawn by an industrial furnace smelting at 1x. */
        const val BASE_REQUIRED_POWER_PER_TICK = 20.0

        private const val PORT_RADIUS = 0.35
    }

    private var portsCreated = false

    /** Creates the producer/consumer ports exactly once, on opposite horizontal faces. */
    fun ensurePorts() {
        if (portsCreated) return
        if (block.electricNodes.any { it is io.github.pylonmc.rebar.electricity.nodes.ElectricProducerNode }) {
            portsCreated = true
            return
        }
        portsCreated = true

        block.createSimpleElectricPort(ElectricNodeType.PRODUCER, BlockFace.UP, PORT_RADIUS)
        block.createSimpleElectricPort(ElectricNodeType.CONSUMER, BlockFace.DOWN, PORT_RADIUS)
        block.createSimpleElectricPort(ElectricNodeType.CONNECTOR, BlockFace.NORTH, PORT_RADIUS)
        block.createSimpleElectricPort(ElectricNodeType.CONNECTOR, BlockFace.SOUTH, PORT_RADIUS)
        block.createSimpleElectricPort(ElectricNodeType.CONNECTOR, BlockFace.EAST, PORT_RADIUS)
        block.createSimpleElectricPort(ElectricNodeType.CONNECTOR, BlockFace.WEST, PORT_RADIUS)
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

    /** True when the network is supplying everything this furnace needs. */
    val isPowered: Boolean
        get() = portsCreated && block.isPowered

    /** Stops both producing and consuming, e.g. when idling or out of fuel. */
    fun idle() {
        powerProduced = 0.0
        requiredPower = 0.0
    }
}
