package top.mc506lw.rebar.ironfurnaces.furnace

import org.bukkit.block.BlockFace
import top.mc506lw.rebar.sidedio.ChannelTypes
import top.mc506lw.rebar.sidedio.RedstoneMode
import top.mc506lw.rebar.sidedio.SideConfig
import top.mc506lw.rebar.sidedio.SideMode

/**
 * 熔炉配置的**文本编解码** —— 把一台熔炉的"面模式 + 机器级设置"拍平成一段文本、再读回来。
 *
 * ## 两个地方用它
 *
 * 1. **拆机保留设置**：拆掉时写进掉落物、放下来时读回来（复刻 26.3 的 `FURNACE_SETTINGS`）；
 * 2. **设置复制器**：右键一台机器复制、潜行右键另一台粘贴。
 *
 * 两边要的是同一份东西，所以抽在这里 —— 各写一份迟早会漂移。
 *
 * ## 为什么是**一整段文本**
 *
 * 每行 `键=值`，键就是 Sided-IO 的持久化键（`<面>.<类型 id>`），
 * 机器级的三项用固定名字（`auto_input` / `auto_output` / `redstone`）。
 *
 * 不塞进嵌套的 PDC 容器，是因为 **PDC 的键必须匹配 `[a-z0-9_-./]`**，
 * 而我们的逻辑键是 `NORTH.sidedio:item` —— 有大写、有冒号。
 * 塞进容器就得额外做一遍字符编解码（Sided-IO 自己那份 `PersistKeys` 就是干这个的），
 * 而**文本值**没有这个限制，直接存最省事。
 */
internal object FurnaceSettingsCodec {

    private const val AUTO_INPUT = "auto_input"
    private const val AUTO_OUTPUT = "auto_output"
    private const val REDSTONE = "redstone"

    /** 把一台机器的配置拍平成文本。读不到面配置时返回空串。 */
    fun snapshot(config: SideConfig): String {
        val lines = ArrayList<String>()
        for ((face, byType) in config.channelModes()) {
            for ((type, mode) in byType) {
                // 只展示的通道（能量）没有方向可存 —— 和 Sided-IO 自己的快照一致
                if (!type.configurable) continue
                lines += "${face.name}.${type.id.namespace}:${type.id.key}=${mode.id}"
            }
        }
        val settings = config.settings
        lines += "$AUTO_INPUT=${settings.autoInput}"
        lines += "$AUTO_OUTPUT=${settings.autoOutput}"
        lines += "$REDSTONE=${settings.redstoneMode.name}"
        return lines.joinToString("\n")
    }

    /**
     * 把 [snapshot] 出来的文本套回一台机器。
     *
     * **认不出来的键一律忽略**（面名 / 类型 / 模式都做容错）——
     * 文本可能来自老版本、被手改、或某个附属被拆了，让整台机器加载失败是不可接受的。
     *
     * @return 有没有真的改到东西（调用方据此决定要不要重算物流组）
     */
    fun apply(config: SideConfig, text: String): Boolean {
        if (text.isBlank()) return false
        var changed = false

        for (line in text.split('\n')) {
            val eq = line.indexOf('=')
            if (eq <= 0) continue
            val key = line.substring(0, eq)
            val value = line.substring(eq + 1)

            when (key) {
                AUTO_INPUT -> value.toBooleanStrictOrNull()?.let {
                    config.settings.autoInput = it
                    changed = true
                }

                AUTO_OUTPUT -> value.toBooleanStrictOrNull()?.let {
                    config.settings.autoOutput = it
                    changed = true
                }

                REDSTONE -> RedstoneMode.entries.firstOrNull { it.name == value }?.let {
                    config.settings.redstoneMode = it
                    changed = true
                }

                else -> {
                    val dot = key.indexOf('.')
                    if (dot <= 0) continue
                    val face = BlockFace.entries
                        .firstOrNull { it.name.equals(key.substring(0, dot), ignoreCase = true) }
                        ?: continue
                    val type = ChannelTypes.find(key.substring(dot + 1)) ?: continue
                    // ⚠️ 机器**能力可能变了**（例如拆掉工厂升级后没有燃料槽了）——
                    // 那一档 / 那个面可能已经不存在。`setMode` 只写进 `modes` 表，
                    // 不存在的格子由 `hasChannel` 那一层过滤，所以这里是安全的。
                    config.setMode(face, type, SideMode.fromName(value))
                    changed = true
                }
            }
        }
        return changed
    }
}
