package top.mc506lw.rebar.ironfurnaces.furnace

import io.github.pylonmc.rebar.entity.display.BlockDisplayBuilder
import io.github.pylonmc.rebar.entity.display.transform.TransformBuilder
import org.bukkit.Bukkit
import org.bukkit.Location
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

        /**
         * 展示实体**自身位置**沿朝向轴往外挪多少（格）。
         *
         * ## 为什么要挪（用户实测）
         *
         * 展示实体的光照是按**实体自身所在的位置**采样的。原来实体建在
         * `block.location.toCenterLocation()`（方块中心），那是在**不透明的熔炉方块内部** ——
         * 采到的光照是 0。之所以一直没看出来，是因为同时设了
         * `.brightness(Display.Brightness(15, 15))` 这个"全亮覆盖"把黑盖住了。
         *
         * 而全亮覆盖本身是错的：夜里的炉子、地下的炉子面板会自己发光。
         * 把覆盖关掉之后，面板就变成**一块纯黑**。
         *
         * 所以正确做法是两件事一起做：
         * ① 去掉亮度覆盖；② 把实体位置挪到**方块外面**（正面那一格，正常是空气）。
         *
         * ⚠️ 必须 > 0.5 —— 0.5 是方块边界，只有越过它才算落进邻格。
         */
        private const val LIGHT_PROBE_OFFSET = 0.51

        /**
         * 实体已经沿朝向轴往外挪了 [LIGHT_PROBE_OFFSET]，所以**局部变换里要把这段减掉**，
         * 面板的视觉位置才和以前完全一致（仍然贴着方块表面、外沿 0.502）。
         */
        private const val PULL_BACK = LIGHT_PROBE_OFFSET - FRONT_OFFSET

        fun getFrontFaceTransformation(facing: BlockFace): TransformBuilder = when (facing) {
            BlockFace.NORTH -> TransformBuilder()
                .translate(0.0, 0.0, PULL_BACK)
                .scale(FRONT_RADIAL_SCALE, FRONT_RADIAL_SCALE, FRONT_THICKNESS_SCALE)
            BlockFace.SOUTH -> TransformBuilder()
                .translate(0.0, 0.0, -PULL_BACK)
                .scale(FRONT_RADIAL_SCALE, FRONT_RADIAL_SCALE, FRONT_THICKNESS_SCALE)
            BlockFace.EAST -> TransformBuilder()
                .translate(-PULL_BACK, 0.0, 0.0)
                .scale(FRONT_THICKNESS_SCALE, FRONT_RADIAL_SCALE, FRONT_RADIAL_SCALE)
            BlockFace.WEST -> TransformBuilder()
                .translate(PULL_BACK, 0.0, 0.0)
                .scale(FRONT_THICKNESS_SCALE, FRONT_RADIAL_SCALE, FRONT_RADIAL_SCALE)
            else -> TransformBuilder()
                .translate(0.0, 0.0, PULL_BACK)
                .scale(FRONT_RADIAL_SCALE, FRONT_RADIAL_SCALE, FRONT_THICKNESS_SCALE)
        }

        /**
         * 面板展示实体的生成位置：方块中心沿 [facing] **往外挪** [LIGHT_PROBE_OFFSET]，
         * 落到正面那一格里 —— 这样采到的是那一格的光照，而不是不透明方块内部的 0。
         *
         * [facing] 必须是**水平**朝向（调用方先过 `horizontalFacing`）。
         */
        fun frontFaceLocation(block: Block, facing: BlockFace): Location =
            block.location.toCenterLocation()
                .add(facing.direction.clone().multiply(LIGHT_PROBE_OFFSET))

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
            // ⚠️ **不要**设 `.brightness(...)`。全亮覆盖会让炉子在夜里 / 地下自己发光，
            // 而且它一直在掩盖"实体位置在不透明方块内部 → 采光为 0"这个真问题。
            // 现在靠 [frontFaceLocation] 把实体挪到方块外面来拿正常光照。
            .transformation(getFrontFaceTransformation(facing))
            .build(frontFaceLocation(block, facing))

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
