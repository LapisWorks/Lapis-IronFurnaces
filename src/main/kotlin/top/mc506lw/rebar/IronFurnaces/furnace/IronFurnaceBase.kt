package top.mc506lw.rebar.ironfurnaces.furnace

import io.github.pylonmc.rebar.block.RebarBlock
import io.github.pylonmc.rebar.block.context.BlockCreateContext
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.persistence.PersistentDataContainer

abstract class IronFurnaceBase : RebarBlock {

    protected val furnaceTier: FurnaceTier
    protected val baseMaterial: Material

    protected constructor(
        block: Block,
        context: BlockCreateContext,
        furnaceTier: FurnaceTier,
        baseMaterial: Material
    ) : super(block, context) {
        this.furnaceTier = furnaceTier
        this.baseMaterial = baseMaterial
    }

    protected constructor(
        block: Block,
        pdc: PersistentDataContainer,
        furnaceTier: FurnaceTier,
        baseMaterial: Material
    ) : super(block, pdc) {
        this.furnaceTier = furnaceTier
        this.baseMaterial = baseMaterial
    }

    fun getGuiMaterial(): Material = Material.FURNACE

    /**
     * 这台熔炉的等级。
     *
     * `furnaceTier` 是 `protected` 的（Kotlin 的 `protected` **只对子类可见、不跨包**），
     * 而"按等级分组"这类逻辑要在**别的实例**上读它（彩虹熔炉要数各档各有多少台），
     * 所以在基类这里开一个 `internal` 的口子。
     */
    internal val tier: FurnaceTier get() = furnaceTier
}
