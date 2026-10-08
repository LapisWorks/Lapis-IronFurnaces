package top.mc506lw.rebar.ironfurnaces.furnace

import io.github.pylonmc.rebar.block.BlockStorage
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPlaceEvent

/**
 * A furnace's body *is* the matching vanilla metal block (an iron furnace is an `iron_block`, a
 * copper furnace is a `copper_block`), so vanilla happily accepts them as golem components:
 *
 * - copper golem: the copper block is swapped for a copper chest, so the furnace disappears
 * - iron golem: every pattern block is cleared, so the furnace disappears
 *
 * Either way the machine and everything stored inside it would be destroyed. Vanilla only spawns a
 * golem from `CarvedPumpkinBlock.onPlace`, i.e. when the pumpkin itself is placed, so refusing that
 * single placement is enough to keep furnaces safe.
 */
class GolemStructureGuard : Listener {

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    fun onPumpkinPlaced(event: BlockPlaceEvent) {
        val pumpkin = event.block
        if (pumpkin.type !in PUMPKINS) return
        if (!completesGolemFromPluginBlock(pumpkin)) return

        event.isCancelled = true
    }

    private fun completesGolemFromPluginBlock(pumpkin: Block): Boolean {
        val below = pumpkin.getRelative(BlockFace.DOWN)
        val bottom = below.getRelative(BlockFace.DOWN)

        // Copper golem: one copper block with the pumpkin directly on top.
        if (below.type == Material.COPPER_BLOCK && BlockStorage.isRebarBlock(below)) return true

        // Snow golem: two snow blocks stacked below the pumpkin.
        if (below.type == Material.SNOW_BLOCK
            && bottom.type == Material.SNOW_BLOCK
            && (BlockStorage.isRebarBlock(below) || BlockStorage.isRebarBlock(bottom))
        ) {
            return true
        }

        // Iron golem: two body blocks with an arm on each side. The pattern is one block thick and
        // can face any horizontal direction, and it also requires the four corners of the "T" to be
        // empty, so only a fully buildable pattern is a problem.
        for (arm in HORIZONTAL_FACES) {
            val other = arm.oppositeFace
            val body = listOf(below, bottom, below.getRelative(arm), below.getRelative(other))
            if (body.any { it.type != Material.IRON_BLOCK }) continue

            val corners = listOf(
                pumpkin.getRelative(arm),
                pumpkin.getRelative(other),
                bottom.getRelative(arm),
                bottom.getRelative(other)
            )
            if (corners.any { !it.isEmpty }) continue

            if (body.any { BlockStorage.isRebarBlock(it) }) return true
        }

        return false
    }

    private companion object {
        private val PUMPKINS = setOf(Material.CARVED_PUMPKIN, Material.JACK_O_LANTERN)

        /** The four rotations the golem pattern can be built in. */
        private val HORIZONTAL_FACES = arrayOf(
            BlockFace.NORTH,
            BlockFace.EAST,
            BlockFace.SOUTH,
            BlockFace.WEST
        )
    }
}
