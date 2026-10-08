package top.mc506lw.rebar.ironfurnaces.furnace

import io.github.pylonmc.rebar.entity.display.BlockDisplayBuilder
import io.github.pylonmc.rebar.entity.display.transform.TransformBuilder
import org.bukkit.Bukkit
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import java.util.UUID
import java.util.Locale

open class FurnaceDisplayRenderer(
    protected val block: Block,
    private val getFacing: () -> BlockFace,
    private val getEntity: (String) -> UUID?,
    private val addEntity: (String, Display) -> Unit,
    private val removeEntity: (String) -> Unit = {}
) {
    companion object {
        private const val FRONT_FACE_ENTITY_NAME = "furnace_front"

        /**
         * The panel is a thin slab sitting on the block face. Its outer surface ends up at
         * `FRONT_OFFSET + FRONT_THICKNESS_SCALE / 2` = 0.502, i.e. 2mm proud of the block's own
         * surface at 0.5 — enough to stay visible and clear of z-fighting, while protruding less
         * than the original 0.499 offset did. Anything below 0.495 sinks the panel *into* the metal
         * block, where the opaque block hides it completely.
         */
        private const val FRONT_OFFSET = 0.497

        /** Panel thickness along the block's normal axis. */
        private const val FRONT_THICKNESS_SCALE = 0.01

        /**
         * The panel is also shrunk slightly in the two in-plane axes, so it reads as a recessed
         * furnace front framed by the metal block rather than a plate covering the whole face.
         */
        private const val FRONT_RADIAL_SCALE = 0.98

        fun getFrontFaceTransformation(facing: BlockFace): TransformBuilder = when (facing) {
            BlockFace.NORTH -> TransformBuilder()
                .translate(0.0, 0.0, -FRONT_OFFSET)
                .scale(FRONT_RADIAL_SCALE, FRONT_RADIAL_SCALE, FRONT_THICKNESS_SCALE)
            BlockFace.SOUTH -> TransformBuilder()
                .translate(0.0, 0.0, FRONT_OFFSET)
                .scale(FRONT_RADIAL_SCALE, FRONT_RADIAL_SCALE, FRONT_THICKNESS_SCALE)
            BlockFace.EAST -> TransformBuilder()
                .translate(FRONT_OFFSET, 0.0, 0.0)
                .scale(FRONT_THICKNESS_SCALE, FRONT_RADIAL_SCALE, FRONT_RADIAL_SCALE)
            BlockFace.WEST -> TransformBuilder()
                .translate(-FRONT_OFFSET, 0.0, 0.0)
                .scale(FRONT_THICKNESS_SCALE, FRONT_RADIAL_SCALE, FRONT_RADIAL_SCALE)
            else -> TransformBuilder()
                .translate(0.0, 0.0, -FRONT_OFFSET)
                .scale(FRONT_RADIAL_SCALE, FRONT_RADIAL_SCALE, FRONT_THICKNESS_SCALE)
        }

        private fun horizontalFacing(facing: BlockFace): BlockFace = when (facing) {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST -> facing
            else -> BlockFace.NORTH
        }
    }

    private var wasBurning = false
    private var currentBlockType = "furnace"

    open fun createFrontFace(blockType: String = "furnace") = ensureFrontFace(blockType)

    open fun ensureFrontFace(blockType: String = "furnace") {
        currentBlockType = blockType
        if (resolveFrontFace() != null) return
        if (getEntity(FRONT_FACE_ENTITY_NAME) != null) removeEntity(FRONT_FACE_ENTITY_NAME)

        val facing = horizontalFacing(getFacing())
        val display = BlockDisplayBuilder()
            .blockData(createBlockData(blockType, false, facing))
            .brightness(Display.Brightness(15, 15))
            .transformation(getFrontFaceTransformation(facing))
            .build(block.location.toCenterLocation())

        addEntity(FRONT_FACE_ENTITY_NAME, display)
    }

    open fun updateFrontFace() {
        val facing = horizontalFacing(getFacing())
        val entity = resolveFrontFace() ?: return
        entity.setTransformationMatrix(getFrontFaceTransformation(facing).buildForBlockDisplay())
    }

    open fun updateBurningState(isBurning: Boolean, blockType: String? = null) {
        val effectiveBlockType = blockType ?: currentBlockType
        if (isBurning == wasBurning && effectiveBlockType == currentBlockType) return

        wasBurning = isBurning
        currentBlockType = effectiveBlockType
        val entity = resolveFrontFace() ?: return
        entity.block = createBlockData(effectiveBlockType, isBurning, horizontalFacing(getFacing()))
    }

    open fun updateDisplayType(blockType: String) {
        if (blockType == currentBlockType) return
        currentBlockType = blockType

        val entity = resolveFrontFace() ?: return
        entity.block = createBlockData(blockType, wasBurning, horizontalFacing(getFacing()))
    }

    open fun refreshState(isBurning: Boolean, blockType: String) {
        wasBurning = isBurning
        currentBlockType = blockType
        val entity = resolveFrontFace() ?: return
        entity.block = createBlockData(blockType, isBurning, horizontalFacing(getFacing()))
    }

    open fun resetState() {
        wasBurning = false
        currentBlockType = "furnace"
    }

    open fun getCurrentBlockType(): String = currentBlockType

    open fun createAdditionalEffects() = Unit

    private fun resolveFrontFace(): BlockDisplay? {
        val uuid = getEntity(FRONT_FACE_ENTITY_NAME) ?: return null
        return block.world.getEntity(uuid) as? BlockDisplay
    }

    private fun createBlockData(blockType: String, lit: Boolean, facing: BlockFace) =
        Bukkit.createBlockData(
            "minecraft:$blockType[lit=$lit,facing=${facing.name.lowercase(Locale.ROOT)}]"
        )
}
