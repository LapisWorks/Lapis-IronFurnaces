package top.mc506lw.rebar.ironfurnaces.furnace

import top.mc506lw.rebar.ironfurnaces.IronFurnaces
import java.util.Locale

/**
 * `config.yml` 的读取层。
 *
 * 规则（和配置文件里的注释一致）：**先取表里的基础值，再乘上对应的整体倍率**；
 * 倍率之间是相乘关系、互不影响：
 *  - `multiplier.power` 作用于发电机出力与工厂耗电（两边同乘，比例不变）
 *  - `multiplier.fuel`  作用于燃料热值
 *  - `multiplier.speed` 作用于烧炼时间
 *
 * 读不到的项一律回退到代码里的默认值（也就是模组原值），所以配置文件缺项也不会出问题。
 */
object FurnaceConfig {

    private val config
        get() = IronFurnaces.instance.config

    val powerMultiplier: Double
        get() = positive("multiplier.power", 1.0)

    val fuelMultiplier: Double
        get() = positive("multiplier.fuel", 1.0)

    val speedMultiplier: Double
        get() = positive("multiplier.speed", 1.0)

    /** 发电机出力（W/t）：等级基础值 × 电力倍率。 */
    fun generation(tier: FurnaceTier): Double =
        positive("generation.${tier.name.lowercase(Locale.ROOT)}", tier.generation) * powerMultiplier

    /** 工厂模式每件物品耗电（J）：基础值 × 电力倍率。 */
    fun energyPerItem(): Double =
        positive("consumption.energy-per-item", FurnaceElectricSystem.ENERGY_PER_ITEM) * powerMultiplier

    private fun positive(path: String, fallback: Double): Double =
        config.getDouble(path, fallback).takeIf { it.isFinite() && it > 0.0 } ?: fallback
}
