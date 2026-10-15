package top.mc506lw.rebar.ironfurnaces.furnace

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **彩虹熔炉「集齐各档」门槛** —— 纯函数测试。
 *
 * ## 为什么值得单测
 *
 * 这条规则是彩虹熔炉的全部意义所在（复刻 26.3 的 `rainbowCheckFurnaceTiers`），
 * 而且它**错了不会崩** —— 只会"该亮的时候不亮"或"不该亮的时候在发电"，
 * 玩家根本说不清是哪儿不对。所以把判定抽成 [RainbowGate.passes] 钉住。
 */
class RainbowGateTest {

    private val allTiers = FurnaceTier.entries.toList()

    /** 🔴 每档都有正在发电的 → 过。 */
    @Test
    fun `passes when every tier has a burning generator`() {
        assertTrue(
            RainbowGate.passes(allTiers, allTiers.toSet(), rainbowGenerators = 1)
        )
    }

    /** 🔴 **缺任何一档都不过** —— 这就是"集齐"的意思。 */
    @Test
    fun `a missing tier fails the gate`() {
        for (missing in allTiers) {
            assertFalse(
                RainbowGate.passes(allTiers, (allTiers - missing).toSet(), 1),
                "缺 $missing 这一档时不该过"
            )
        }
    }

    /** 名下每档都有、但一台都没在烧 → 不过（"有"不等于"在发电"）。 */
    @Test
    fun `owned but idle furnaces do not count`() {
        assertFalse(RainbowGate.passes(allTiers, emptySet(), 1))
    }

    /** 名下每档都有、只有一档在烧 → 还是不过。 */
    @Test
    fun `owning every tier but burning only one still fails`() {
        assertFalse(RainbowGate.passes(allTiers, setOf(FurnaceTier.IRON), 1))
    }

    /** 🔴 **两台彩虹同时在发电机模式 → 互相取消**（26.3 的 `rainbowGens > 1`）。 */
    @Test
    fun `two rainbow generators cancel each other`() {
        assertFalse(
            RainbowGate.passes(allTiers, allTiers.toSet(), rainbowGenerators = 2),
            "两台彩虹在发电机模式时不该过"
        )
    }

    /** 一台熔炉都没有 → 不过。 */
    @Test
    fun `no furnaces at all fails`() {
        assertFalse(RainbowGate.passes(emptyList(), emptySet(), 0))
    }

    /** 门槛要求的是**每一档**，不是"至少一台" —— 只有彩虹自己一台时不过。 */
    @Test
    fun `a single rainbow furnace alone fails`() {
        assertFalse(
            RainbowGate.passes(
                owned = listOf(FurnaceTier.RAINBOW),
                burning = setOf(FurnaceTier.RAINBOW),
                rainbowGenerators = 1,
            )
        )
    }
}
