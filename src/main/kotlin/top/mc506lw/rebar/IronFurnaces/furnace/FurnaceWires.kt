package top.mc506lw.rebar.ironfurnaces.furnace

import io.github.pylonmc.rebar.electricity.WireEntity
import io.github.pylonmc.rebar.util.Either
import org.bukkit.Location

/**
 * 电线与方块之间的判断工具。
 *
 * **为什么不能按方块坐标比**：Rebar 的端口实体是贴在方块表面上的（外表面 ≈ radius，也就是略微
 * 超出方块），顶部端口的实体位置其实落在上面那一格里。所以"这个端口属不属于这个方块"只能按
 * "距离方块中心足够近"判断，半径取 0.75（够覆盖 0.5 的表面偏移，又不会误伤相邻方块中心处的 1.0）。
 */
internal object FurnaceWires {

    private const val NEAR_RADIUS = 0.75

    /** 这条电线是否挂在以 [center] 为中心的这个方块上（两端都算）。 */
    fun attachesTo(wire: WireEntity, center: Location): Boolean {
        if (isNear(wire.port.location, center)) return true
        val other = wire.otherEnd
        return other is Either.Right && isNear(other.value.location, center)
    }

    /** 掉出并移除挂在 [center] 这个方块上的所有电线（方块被拆时调用）。 */
    fun removeAt(center: Location) {
        for (wire in WireEntity.loadedWires) {
            if (!attachesTo(wire, center)) continue
            wire.dropItemsAt(center)
            wire.entity.remove()
        }
    }

    private fun isNear(port: Location, center: Location): Boolean =
        port.world == center.world && port.distanceSquared(center) <= NEAR_RADIUS * NEAR_RADIUS
}
