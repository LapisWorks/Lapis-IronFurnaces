package top.mc506lw.rebar.ironfurnaces.furnace

import io.github.pylonmc.rebar.logistics.LogisticGroupType
import org.bukkit.block.BlockFace
import top.mc506lw.rebar.sidedio.ChannelTypes
import top.mc506lw.rebar.sidedio.MachineSetting
import top.mc506lw.rebar.sidedio.SIDED_FACES
import top.mc506lw.rebar.sidedio.SideConfig
import top.mc506lw.rebar.sidedio.SideConfigBuilder
import top.mc506lw.rebar.sidedio.SideMode
import top.mc506lw.rebar.sidedio.SidedIo
import top.mc506lw.rebar.sidedio.SideModes
import xyz.xenondevs.invui.inventory.VirtualInventory

/**
 * 熔炉的面配置装配。
 *
 * ## 为什么单独一个文件，而不是塞进 [AbstractIronFurnace]
 *
 * 因为这里的**唯一难点是"槽位会变"**，而这个难点容易把机器逻辑淹掉：
 *
 * | 触发 | 变了什么 |
 * |---|---|
 * | 装工厂升级 | `activeSlots` 1 → 2/4/6，输入/输出槽变多 |
 * | 装能量升级 | `fuelSlotEnabled` false，燃料槽整体消失 |
 * | 拆升级、槽里还有残留 | 槽数收回去，但残留物品的格子还得让管道能碰到 |
 *
 * Sided-IO 的 [SideConfig] 是**建一次、槽位集合固定**的（Rebar 的 LogisticGroup
 * 的 `slots` 虽然可变，但组名里编着方向名，改槽位等于换组）。所以槽位变了就得
 * 用 [SidedIo.reattach] 重建一遍 —— 它会把玩家已改过的每个格子搬过去。
 *
 * ## 暴露什么通道
 *
 * 熔炉是**单类型机器**：进出全是物品。所以它只注册 `ChannelTypes.ITEM` 一条类型，
 * 但**分三条槽位集合喂给同一个类型**（输入 / 燃料 / 输出），各自带着自己的**组类型**：
 *
 * | 槽位集合 | 组类型（`slotType`） | 出厂方向（`default`） | 说明 |
 * |---|---|---|---|
 * | 输入 | `INPUT` | **所有面** INPUT | 最多 6 格（工厂模式） |
 * | 燃料 | `INPUT` | **所有面** INPUT | 1 格，带"只收燃料"过滤 |
 * | 输出 | `OUTPUT` | **只有底面** OUTPUT | 最多 6 格 |
 *
 * 合并后（见 [SideConfig.mergeDefault]）：**底面 = 双向、其余 4 个侧面 + 顶面 = 输入** ——
 * 也就是"**所有面都能进料，只有底面能出成品**"。
 *
 * ## 炉门那一面不参与 I/O
 *
 * **前面**（[attach] 的 `facing`，玩家放置时朝的那一面）被 [SideConfigBuilder.hideFace]
 * 屏蔽掉：它是炉门、是显示烧炼进度的地方，不该接管道也不该自动搬运。
 * 所以实际能用的面是 **5 个**（4 侧 + 顶 + 底，减掉前面）。
 *
 * ## 能量通道是**有条件**的
 *
 * 只有**用电**的熔炉（装了工厂 / 能量升级）才会出现 `能量` 这一行 ——
 * 烧燃料的普通熔炉顶上根本没有电口，列出来只会让玩家以为能接电。
 * 见 [attach] 的 `usesEnergy`。
 *
 * ⚠️ **`slotType` 和 `default` 是两件不同的事**（这是修复一个真实 bug 的关键）：
 *
 * - `slotType` = 这组槽**本身**是进料口还是产出口（**永远不变**）；
 * - `default`  = **面**出厂时朝哪个方向（玩家之后会改）。
 *
 * 两者**交叉**才是"这个面能不能碰这组槽"（见 `SideMode.covers`）。少了 `slotType`
 * 就会退化成"面是什么方向，就把所有组都套成那个类型" —— 于是侧面设成 INPUT 时
 * **输出槽也变成 INPUT**（成品再也抽不走）、底面设成 OUTPUT 时**输入槽也变成 OUTPUT**
 * （没烧的矿石能被抽走）。
 *
 * 面板上熔炉左侧只有**一个**"物品"按钮（类型是合并的），
 * 但玩家点开后会发现：底面出厂是输出、其余面是输入 —— 正是老实现
 * 那套 `DEFAULT_FACES` 的效果，只是现在它落在"物品"这一行里。
 *
 * ⚠️ **燃料槽用 filter 而不是不给它注册**：Rebar 的燃料校验在
 * `FurnaceFuelLogisticSlot.getMaxAmount` 里（非燃料返回 0），但那是**原版熔炉**的槽；
 * 我们是自建 `VirtualInventory`，没有那层校验。所以这里显式用
 * `filter = { it.type.isFuel }` 补上，否则管道能把石头当燃料塞进去。
 */
internal object FurnaceSides {

    /**
     * 「燃料输入 / 弹出」这一档面状态的 id。
     *
     * ⚠️ 会写进 PDC，**一旦发布就不要改**（改了老存档的配置会读不回来）。
     */
    const val FUEL_INPUT_EJECT_ID = "ironfurnaces:fuel_input_eject"

    /**
     * 注册本附属自定义的面状态。
     *
     * ## 为什么要有这一档（对齐 Iron Furnaces 的第五档）
     *
     * Iron Furnaces 的面板除了 `NONE / Input / Output / Input-Output`，还有第五档
     * **`Fuel Input/Eject`**：**燃料从这一面进、成品从这一面弹出**。
     *
     * 它**表达不成"一个方向"** —— 同一个面上 `fuel` 组是"进"、`output` 组是"出"。
     * 所以靠 `SideMode.groupRules`（**按组名**给方向）描述：
     *
     * | 组 | 方向 |
     * |---|---|
     * | `fuel` | 进（塞燃料） |
     * | `output` | 出（抽成品） |
     * | 其余（`input` 等） | **碰不到** |
     *
     * 组名由 [configure] 里 `channel(..., name = "...")` 给。
     *
     * ## 为什么要先查一下
     *
     * `SideModes.register` 撞 id 会抛异常（刻意的，防两个附属抢同一个 id）。
     * 但**插件重载**（`/reload`、热重载）会再跑一次 `onEnable`，而 Sided-IO 的
     * 注册表是**进程级单例**、不会跟着重置 —— 所以这里必须先查后注册。
     */
    fun registerFaceModes() {
        if (SideModes.find(FUEL_INPUT_EJECT_ID) != null) return
        SideModes.register(
            id = FUEL_INPUT_EJECT_ID,
            direction = SideMode.Flow.BOTH,     // 整体既能进又能出
            groupRules = mapOf(
                "fuel" to SideMode.Flow.IN,
                "output" to SideMode.Flow.OUT,
            ),
        )
    }

    /**
     * 出厂时哪些面默认是什么方向。
     *
     * ## 对齐 Iron Furnaces 的"**进料不分面**"，不照抄原版熔炉
     *
     * 原版熔炉的 `getSlotsForFace` 是**按面切槽**的：
     * `上={进料}` / `侧={燃料}` / `下={成品,燃料}` —— 于是**侧面塞不进矿石**，
     * 只有燃料能从侧面进。那是原版 2013 年的设计（限制漏斗从侧面加燃料）。
     *
     * Iron Furnaces 那类模组不是这样。它的抽象基类 `TileEntityInventory` 里：
     *
     * ```java
     * // ⚠️ direction 参数被完全忽略 —— "插入"根本不看方向
     * public boolean canPlaceItemThroughFace(int i, ItemStack stack, @Nullable Direction dir) {
     *     return IisItemValidForSlot(i, stack);
     * }
     * ```
     *
     * 也就是"任何面都能塞进料"，只有"抽取"才挑面。
     *
     * ## 于是这里的规则是
     *
     * - **进料**（[INPUT_DEFAULT]）：**所有 6 面**都能进 —— 输入槽 + 燃料槽都够得着；
     * - **产出**（[OUTPUT_DEFAULT]）：**只有底面**能抽成品
     *   （对齐原版 `getSlotsForFace(DOWN)`，底面是公认的取货面）。
     *
     * 两者经 [top.mc506lw.rebar.sidedio.SideConfig.mergeDefault] 合并后是
     * **底面 = 双向、其余 5 面 = 输入** —— 即"所有面都能进料，只有底面能出成品"。
     */
    private val INPUT_DEFAULT: Map<BlockFace, SideMode> =
        SIDED_FACES.associateWith { SideMode.INPUT }

    /**
     * 产出侧：**只有底面**能抽成品。其余面给 [SideMode.NONE] ——
     * 和 [INPUT_DEFAULT] 合并后它们仍然是"只能进"（`NONE` 不会覆盖 `INPUT`）。
     */
    private val OUTPUT_DEFAULT: Map<BlockFace, SideMode> =
        SIDED_FACES.associateWith { SideMode.NONE } + (BlockFace.DOWN to SideMode.OUTPUT)

    /**
     * 给一台熔炉装上面配置。
     *
     * @param rebar         熔炉本体（要被注册成 LogisticRebarBlock）
     * @param inputInv      输入库存
     * @param outputInv     输出库存
     * @param fuelInv       燃料库存
     * @param activeSlots   当前可用的输入/输出格数（1 / 2 / 4 / 6）
     * @param displayedSlots 当前该暴露的格数（含拆升级后的残留格）
     * @param fuelSlotEnabled 燃料槽还在不在
     * @param facing        熔炉朝向 —— 这一面是**炉门**，不参与任何输入输出
     * @param usesEnergy    这台熔炉用不用电（装了工厂 / 能量升级才用）
     */
    fun attach(
        rebar: io.github.pylonmc.rebar.block.RebarBlock,
        inputInv: VirtualInventory,
        outputInv: VirtualInventory,
        fuelInv: VirtualInventory,
        activeSlots: Int,
        displayedSlots: Int,
        fuelSlotEnabled: Boolean,
        facing: BlockFace,
        usesEnergy: Boolean,
    ): SideConfig = SidedIo.reattach(rebar) { builder ->
        configure(builder, inputInv, outputInv, fuelInv, activeSlots, displayedSlots, fuelSlotEnabled)

        // ⚠️ **炉门那一面不做任何输入输出**（用户定案）。
        //
        // 前面是机器的"正面" —— 玩家看到的是炉门和火焰，
        // 从那儿接一根管子进来说不通；而且那一面要留给机器的显示模型
        // （[FurnaceDisplayRenderer.ensureFrontFace] 把烧炼进度画在朝向前面的那层）。
        //
        // `hideFace` 的效果正好是"不参与 I/O"三件套：
        // 面板不画面格子、管道判定接不上（`allowsConnection` = false）、
        // 自动输入输出直接跳过（`AutoIo` 见 `isFaceHidden` 就 continue）。
        builder.hideFace(facing)

        // ⚠️ **只有用电的熔炉才有能量通道**。
        //
        // 烧燃料的普通熔炉**根本没有电口**（`FurnaceElectricSystem.ensurePorts`
        // 只在 `usesEnergy` 时开端口），面板却列一行"能量 · 无端口"是纯噪音 ——
        // 玩家会以为"这台炉子能接电"（用户实测报的"非工厂状态的熔炉怎么有能量输入口呢"）。
        //
        // 所以能量通道跟着 `usesEnergy` 走：工厂 / 能量升级装上才出现，
        // 拆掉就消失（`rebuildSides` 会带上这个变化重建）。
        if (usesEnergy) builder.displayChannel(ChannelTypes.ENERGY)

        // 熔炉支持的**机器级**设置（和面无关的那部分）。
        // 声明了才会出现在面板的第 0 行 / 第 4 行。
        builder.machineSettings(
            MachineSetting.REDSTONE,
            MachineSetting.AUTO_INPUT,
            MachineSetting.AUTO_OUTPUT,
        )
    }

    /**
     * 往构建器上挂通道。抽出来是为了 [attach] 和测试共用。
     */
    private fun configure(
        builder: SideConfigBuilder,
        inputInv: VirtualInventory,
        outputInv: VirtualInventory,
        fuelInv: VirtualInventory,
        activeSlots: Int,
        displayedSlots: Int,
        fuelSlotEnabled: Boolean,
    ) {
        // ⚠️ 用 displayedSlots 而不是 activeSlots：拆掉工厂升级后，高出 activeSlots 的格子
        // 里可能还留着物品（见 AbstractIronFurnace.displayedSlots 的注释），
        // 那些格子必须还能被管道碰到，否则物品就"锁死"在玩家看得见但搬不走的槽里。
        val exposed = displayedSlots.coerceAtLeast(activeSlots).coerceAtMost(MAX_FACTORY_SLOTS)
        val indices = IntArray(exposed) { it }

        // 输入 + 输出：同一个类型（物品），两条方向相反的通道。
        // channel() 会在同类型多条通道间**合并槽位**（面板上是同一行"物品"），
        // 但**组类型各算各的** —— 这就是新模型相对老"面模式"的关键收益。
        //
        // ⚠️ `slotType` 必须显式给：它说的是"这组槽**本身**是进料口还是产出口"，
        // 和"面朝哪个方向"是两件正交的事。不给的话两组都默认 BOTH，
        // 于是"侧面能进料但不能抽产出、底面能抽产出"就表达不出来
        // （会退化成"哪个面都能碰全部槽"）。
        builder.channel(
            ChannelTypes.ITEM,
            inputInv,
            indices,
            default = INPUT_DEFAULT,
            filter = { true },   // 输入槽什么都收
            slotType = LogisticGroupType.INPUT,
            // ⚠️ 名字给面状态用：`fuel_input_eject` 就是"只碰 fuel 组（进）+ output 组（出）"
            name = "input",
        )
        builder.channel(
            ChannelTypes.ITEM,
            outputInv,
            indices,
            default = OUTPUT_DEFAULT,
            slotType = LogisticGroupType.OUTPUT,
            name = "output",
        )

        if (fuelSlotEnabled) {
            builder.channel(
                ChannelTypes.ITEM,
                fuelInv,
                // 只收燃料 —— 见类注释，自建 VirtualInventory 没有 Rebar 那层校验
                filter = { it.type.isFuel },
                default = INPUT_DEFAULT,
                slotType = LogisticGroupType.INPUT,
                name = "fuel",
            )

            // 「燃料输入/弹出」这一档**只在有燃料槽的时候**才提供。
            //
            // ⚠️ 自定义面状态是**全局注册**的，但"哪台机器用得上"只有机器知道 ——
            // 所以 Sided-IO 默认只给内置四档，自定义档要机器自己声明（`extraFaceModes`）。
            // 不声明的话，这一档会出现在**每一台机器**的循环里，
            // 包括**原版箱子**的扳手循环。
            //
            // 反过来，装了工厂 / 能量升级之后燃料槽整个消失，
            // 这一档的"燃料进"半边就不存在了（只剩"只出成品"）——
            // 那时就不再声明它，玩家切不到一个名字对不上行为的档。
            SideModes.find(FUEL_INPUT_EJECT_ID)?.let { builder.extraFaceModes(it) }
        }
    }

    private const val MAX_FACTORY_SLOTS = 6
}
