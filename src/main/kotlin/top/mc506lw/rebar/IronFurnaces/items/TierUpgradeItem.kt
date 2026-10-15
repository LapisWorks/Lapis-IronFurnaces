package top.mc506lw.rebar.ironfurnaces.items

import io.github.pylonmc.rebar.block.BlockStorage
import io.github.pylonmc.rebar.event.api.annotation.MultiHandler
import io.github.pylonmc.rebar.i18n.RebarArgument
import io.github.pylonmc.rebar.item.RebarItem
import io.github.pylonmc.rebar.item.interfaces.InteractRebarItemHandler
import net.kyori.adventure.text.Component
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.event.EventPriority
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import top.mc506lw.rebar.ironfurnaces.IronFurnaceBlocks
import top.mc506lw.rebar.ironfurnaces.furnace.AbstractIronFurnace
import top.mc506lw.rebar.ironfurnaces.furnace.TierUpgrade
import java.util.Locale

/**
 * **等级升级** —— 右键一台熔炉把它升到下一级，**保留里面的东西和设置**。
 * 复刻 26.3 的 `ItemUpgrade`。
 *
 * ## 两类目标
 *
 * | 手里的升级 | 目标 | 走哪条路 |
 * |---|---|---|
 * | 铜 / 铁（`from == null`）| **原版熔炉** | 直接 `placeBlock` 放我们的方块 |
 * | 其余 8 张 | **我们的某一级熔炉** | `existing.upgradeTo(...)`（拆旧放新 + 搬状态） |
 *
 * ⚠️ 一个类注册 10 次 —— 具体是哪一张由**物品自己的 Rebar 键**决定
 * （[TierUpgrade.of]），不需要写 10 个空壳类。
 */
class TierUpgradeItem(stack: ItemStack) : RebarItem(stack), InteractRebarItemHandler {

    @MultiHandler(priorities = [EventPriority.HIGH])
    override fun onInteract(event: PlayerInteractEvent, priority: EventPriority) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        if (event.hand != EquipmentSlot.HAND) return

        val upgrade = TierUpgrade.of(stack) ?: return
        val clicked = event.clickedBlock ?: return
        val player = event.player

        val existing = runCatching { BlockStorage.get(clicked) }.getOrNull() as? AbstractIronFurnace

        val ok = when {
            // ① 目标是我们的一台熔炉 —— 等级要对得上
            existing != null -> {
                if (upgrade.from == null || existing.tier != upgrade.from) {
                    // 等级对不上：给个提示，别默默什么都不发生
                    player.sendActionBar(Component.translatable("ironfurnaces.message.upgrade_wrong_tier"))
                    return
                }
                existing.upgradeTo(upgrade.to)
            }

            // ② 目标是**原版熔炉**（铜 / 铁那两张"入门券"）
            upgrade.from == null && clicked.type == Material.FURNACE ->
                IronFurnaceBlocks.keyFor(upgrade.to)
                    ?.let { BlockStorage.placeBlock(clicked, it) } != null

            else -> return
        }

        if (!ok) {
            player.sendActionBar(Component.translatable("ironfurnaces.message.upgrade_failed"))
            return
        }

        event.isCancelled = true
        // 创造模式不消耗
        if (player.gameMode != GameMode.CREATIVE) stack.amount -= 1

        player.sendActionBar(
            Component.translatable(
                "ironfurnaces.message.upgrade_success",
                // 占位符必须走 RebarArgument（Rebar 的翻译器只替换这个类型）
                RebarArgument.of(
                    "furnace",
                    Component.translatable(
                        "ironfurnaces.item.${upgrade.to.name.lowercase(Locale.ROOT)}_furnace.name"
                    )
                )
            )
        )
    }
}
