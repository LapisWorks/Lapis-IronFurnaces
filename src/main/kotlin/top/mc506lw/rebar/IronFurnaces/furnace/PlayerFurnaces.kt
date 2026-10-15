package top.mc506lw.rebar.ironfurnaces.furnace

import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import top.mc506lw.rebar.ironfurnaces.IronFurnaceKeys
import java.util.UUID

/**
 * **玩家熔炉名册** —— 复刻 26.3 的 `PlayerFurnacesList`（NeoForge 的玩家附件）。
 *
 * ## 它解决什么问题
 *
 * 彩虹熔炉的发电门槛是"**你名下每一档都要有一台在发电**"（见 [RainbowFurnace]）。
 * 那就得能回答"**这个玩家有哪些熔炉**"。原版 mod 的做法是给玩家挂一个附件，
 * 放置时登记坐标、拆掉时移除；Paper 没有附件系统，所以这里用**玩家 PDC** 存同样一份东西。
 *
 * ## 为什么不在熔炉上存 owner、然后全图扫
 *
 * 因为"全图扫熔炉"没有便宜的实现 —— 只能遍历已加载区块里的所有方块实体。
 * 反过来（玩家 → 自己的熔炉）是一次 O(熔炉数) 的查表。
 *
 * ## ⚠️ 只认**已加载**的区块
 *
 * mod 那边是 `level.getChunkAt(pos).setLoaded(true)` —— **强制加载**。
 * 在服务端插件里每 tick 强制加载一堆区块是 TPS 灾难，所以这里只认已经加载的：
 * 熔炉所在区块没加载时，那台**这一 tick 不算数**（门槛可能因此不满足）。
 * 这是刻意的取舍，玩家把熔炉都放在同一个基地里就不会碰到。
 *
 * ## 存什么
 *
 * 玩家 PDC 里一个字符串列表，每项 `"<世界>:<x>:<y>:<z>"`。
 * 用字符串而不是打包成 long 数组，是为了**带上世界**（玩家的熔炉可能跨世界）。
 */
object PlayerFurnaces {

    /** 记下"这台熔炉是 [player] 放的"。重复登记会被去重。 */
    fun add(player: Player, block: Block) {
        val key = encode(block) ?: return
        val current = read(player)
        if (key in current) return
        write(player, current + key)
    }

    /** 这台熔炉被拆了 —— 从名册里划掉。 */
    fun remove(player: Player, block: Block) {
        val key = encode(block) ?: return
        val current = read(player)
        if (key !in current) return
        write(player, current - key)
    }

    /**
     * 按 owner 找名册 —— 只有**在线**玩家才有 PDC 可读（和 mod 的
     * `level.getPlayerByUUID(...)` 一样：离线玩家查不到，直接跳过）。
     */
    fun of(owner: UUID): Player? = Bukkit.getPlayer(owner)

    /**
     * 这个玩家名下**当前还在的**熔炉。
     *
     * 顺手做两件清理：
     * 1. 世界没了 / 区块没加载 / 那一格已经不是熔炉了 → 从名册里划掉（僵尸条目）；
     * 2. 区块没加载的**先留着**（不是僵尸，只是这会儿不在），但也不返回。
     *
     * @param onlyLoaded 只返回区块已加载的（默认 true）。传 false 会把没加载的也返回，
     *                   调用方自己去碰 `block.state` 时可能会强制加载 —— 想清楚再用。
     */
    fun furnacesOf(player: Player, onlyLoaded: Boolean = true): List<AbstractIronFurnace> {
        val entries = read(player)
        if (entries.isEmpty()) return emptyList()

        val result = ArrayList<AbstractIronFurnace>(entries.size)
        val stale = ArrayList<String>()

        for (entry in entries) {
            val decoded = decode(entry) ?: run { stale += entry; continue }
            val (world, x, y, z) = decoded

            if (!world.isChunkLoaded(x shr 4, z shr 4)) {
                // 区块没加载：**不是僵尸**，只是这会儿读不到 —— 留着，但不返回
                continue
            }
            val rebar = io.github.pylonmc.rebar.block.BlockStorage.get(
                io.github.pylonmc.rebar.util.position.BlockPosition(world, x, y, z)
            )
            if (rebar !is AbstractIronFurnace) {
                stale += entry     // 那一格已经不是我们的熔炉了（被拆 / 被换掉）
                continue
            }
            result += rebar
        }

        if (stale.isNotEmpty()) write(player, entries - stale.toSet())
        return result
    }

    /** 清空名册（调试 / 管理员用）。 */
    fun clear(player: Player) {
        player.persistentDataContainer.remove(IronFurnaceKeys.PLAYER_FURNACES)
    }

    // ------------------------------------------------------------ 编解码

    /** `"<世界>:<x>:<y>:<z>"`；拿不到世界返回 null。 */
    private fun encode(block: Block): String? =
        "${block.world.name}:${block.x}:${block.y}:${block.z}"

    private data class Decoded(
        val world: World,
        val x: Int,
        val y: Int,
        val z: Int
    )

    private fun decode(entry: String): Decoded? {
        val parts = entry.split(':')
        if (parts.size != 4) return null
        val world = Bukkit.getWorld(parts[0]) ?: return null
        val x = parts[1].toIntOrNull() ?: return null
        val y = parts[2].toIntOrNull() ?: return null
        val z = parts[3].toIntOrNull() ?: return null
        return Decoded(world, x, y, z)
    }

    // ------------------------------------------------------------ 持久化

    private fun read(player: Player): List<String> =
        player.persistentDataContainer
            .get(IronFurnaceKeys.PLAYER_FURNACES, PersistentDataType.LIST.strings())
            ?.toList()
            ?: emptyList()

    private fun write(player: Player, entries: List<String>) {
        if (entries.isEmpty()) {
            player.persistentDataContainer.remove(IronFurnaceKeys.PLAYER_FURNACES)
        } else {
            player.persistentDataContainer.set(
                IronFurnaceKeys.PLAYER_FURNACES,
                PersistentDataType.LIST.strings(),
                entries
            )
        }
    }
}
