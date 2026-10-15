package top.mc506lw.rebar.ironfurnaces.furnace

/**
 * 彩虹熔炉的「**集齐各档**」发电门槛 —— 复刻 26.3 的 `rainbowCheckFurnaceTiers` 那套判定。
 *
 * ## 规则
 *
 * 装了发电机升级**不等于**会发电。要同时满足：
 *
 * 1. 名下有熔炉（且 [owned] 非空）；
 * 2. **每一档**（铜 / 铁 / 金 / 钻石 / 绿宝石 / 水晶 / 黑曜石 / 下界合金 / 彩虹）
 *    都要有**至少一台正在发电**的 —— 也就是"集齐各档，而且它们都在烧"；
 * 3. 彩虹熔炉自己**只能有一台**在发电机模式（多台会互相取消）。
 *
 * ## 为什么单独一个对象、而不是写在 [RainbowFurnace] 的 companion 里
 *
 * 因为要能**单测**。[RainbowFurnace] 的 companion 里放着 `Material` / `Particle` 这些
 * Bukkit 常量，引用它就要求测试运行时有一整套服务端 —— 而这个门槛是**纯逻辑**，
 * 不该被拖下水。所以放这里：只依赖 [FurnaceTier]，纯 Kotlin。
 *
 * 这条规则**错了不会崩** —— 只会"该亮的时候不亮"或"不该亮的时候在发电"，
 * 玩家根本说不清是哪儿不对。所以必须有测试钉住。
 */
object RainbowGate {

    /**
     * @param owned             名下所有熔炉的档位（含彩虹自己）
     * @param burning           其中**正在发电**的那些的档位集合
     * @param rainbowGenerators 其中**处在发电机模式**的彩虹熔炉台数
     */
    fun passes(
        owned: List<FurnaceTier>,
        burning: Set<FurnaceTier>,
        rainbowGenerators: Int,
    ): Boolean {
        if (owned.isEmpty()) return false
        // 彩虹自己只能有一台在发电机模式 —— 多台会互相取消（26.3 的 `rainbowGens > 1`）
        if (rainbowGenerators > 1) return false
        // 每一档都要有至少一台正在发电的
        return FurnaceTier.entries.all { it in burning }
    }
}
