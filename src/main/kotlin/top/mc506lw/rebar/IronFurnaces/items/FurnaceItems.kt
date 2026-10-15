package top.mc506lw.rebar.ironfurnaces.items

import io.github.pylonmc.rebar.item.RebarItem
import io.github.pylonmc.rebar.item.interfaces.VanillaFurnaceFuel
import org.bukkit.inventory.ItemStack

class CopperFurnaceItem(stack: ItemStack) : RebarItem(stack)
class IronFurnaceItem(stack: ItemStack) : RebarItem(stack)
class GoldFurnaceItem(stack: ItemStack) : RebarItem(stack)
class DiamondFurnaceItem(stack: ItemStack) : RebarItem(stack)
class EmeraldFurnaceItem(stack: ItemStack) : RebarItem(stack)
class CrystalFurnaceItem(stack: ItemStack) : RebarItem(stack)
class ObsidianFurnaceItem(stack: ItemStack) : RebarItem(stack)
class NetheriteFurnaceItem(stack: ItemStack) : RebarItem(stack)
class RainbowFurnaceItem(stack: ItemStack) : RebarItem(stack)

class RainbowCoreItem(stack: ItemStack) : RebarItem(stack)

/** 彩虹镀层（对齐 26.3 的 `rainbow_plating`；以前叫 `rainbow_shell`）。 */
class RainbowPlatingItem(stack: ItemStack) : RebarItem(stack)

/**
 * 彩虹煤。
 *
 * ⚠️ **必须实现 [VanillaFurnaceFuel]** —— Rebar 的 `RecipeListener.onFuelBurn`
 * 在 `LOWEST` 优先级就把"是 Rebar 物品但不是 [VanillaFurnaceFuel]"的燃料**一律取消**：
 *
 * ```kotlin
 * if (e.fuel.isRebarAndIsNot<VanillaFurnaceFuel>()) { e.isCancelled = true; return }
 * ```
 *
 * 少了这个标记接口，它在**任何**熔炉（包括我们自己的）里都点不着 ——
 * 而且事件照样触发、只是 `isCancelled = true`，看日志会以为是别的问题（我们踩过）。
 */
class RainbowCoalItem(stack: ItemStack) : RebarItem(stack), VanillaFurnaceFuel

class BlastUpgradeItem(stack: ItemStack) : RebarItem(stack)
class SmokerUpgradeItem(stack: ItemStack) : RebarItem(stack)
class SpeedUpgradeItem(stack: ItemStack) : RebarItem(stack)
class FuelUpgradeItem(stack: ItemStack) : RebarItem(stack)
class IndustrialUpgradeItem(stack: ItemStack) : RebarItem(stack)
class GeneratorUpgradeItem(stack: ItemStack) : RebarItem(stack)

/**
 * **设置复制器** —— 右键复制一台熔炉的配置、潜行右键粘贴到另一台。
 *
 * 具体行为在熔炉那边（`AbstractIronFurnace.onInteractedWith`）——
 * 因为要读写的配置属于**目标机器**，由机器自己处理最直接。
 */
class FurnaceCopyItem(stack: ItemStack) : RebarItem(stack)

/** **无线能量加热器**的方块物品。 */
class WirelessHeaterItem(stack: ItemStack) : RebarItem(stack)

/**
 * **加热棒**。
 *
 * ⚠️ 同样**必须实现 [VanillaFurnaceFuel]**（原因见 [RainbowCoalItem]）——
 * 它的底块是烈焰棒，本来就该被原版当燃料认；少了这个接口 Rebar 会先把事件取消掉，
 * 我们那条"改成不消耗 + 扣电"的逻辑根本没机会跑。
 *
 * 具体行为在 [ItemHeater] / `VanillaFurnaceFuelListener` / `FurnaceFuelSystem` 里。
 */
class HeaterRodItem(stack: ItemStack) : RebarItem(stack), VanillaFurnaceFuel