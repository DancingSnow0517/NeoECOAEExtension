# 合成通讯接口首次打开：ExtendedAE 策略与双版本源码索引

日期：2026-10-24。范围：1.20.1 Forge 与 1.21.1 NeoForge。问题指首次打开等待样板加载、已显示的样板暂时不能拿取。

## ExtendedAE 的实现

ExtendedAE 的扩展样板管理终端继承 AE2 的 `PatternAccessTermMenu`。首次同步按供应器建立记录，发送供应器容量与非空槽位的物品。客户端收到供应器数据后即可显示和操作。它仍会读取库存和经过网络传输，并非完全没有扫描。

搜索框为空时，`refreshList()` 直接接纳供应器，不遍历样板计算搜索关键词。实际搜索时才解码搜索数据并缓存。点击通过供应器 ID 和真实槽位发往服务端，不依赖全网搜索索引完成。

| 参考入口 | 阅读位置 | 作用 |
| --- | --- | --- |
| [ContainerExPatternTerminal](https://github.com/GlodBlock/ExtendedAE/blob/827a5368dd48a43aab5a5d3c8c3d0914df3bf816/src/main/java/com/glodblock/github/extendedae/container/ContainerExPatternTerminal.java) | 类声明、构造器 | 沿用 AE2 库存同步与操作 |
| [PatternAccessTermMenu](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/1c2f96e70ca6b61e8120482923e9775d772fd9e7/src/main/java/appeng/menu/implementations/PatternAccessTermMenu.java) | `broadcastChanges`、`sendFullUpdate`、`ContainerTracker.createFullPacket`、`doAction` | 每个供应器发送容量和非空物品；后续增量与服务端拿取 |
| [GuiExPatternTerminal](https://github.com/GlodBlock/ExtendedAE/blob/827a5368dd48a43aab5a5d3c8c3d0914df3bf816/src/main/java/com/glodblock/github/extendedae/client/gui/GuiExPatternTerminal.java) | `postFullUpdate`、`postIncrementalUpdate`、`refreshList`、`getOrComputePatternSearchData`、`slotClicked` | 接收即显示、按需搜索、物理槽位操作 |

已先更新参考源码远端。ExtendedAE 1.20.1 Forge 对照提交为 `827a5368dd48a43aab5a5d3c8c3d0914df3bf816`；AE2 对照为 `forge/v15.4.11` 的 `1c2f96e70ca6b61e8120482923e9775d772fd9e7`。另核对 ExtendedAE 1.21.1 的 `1f86f927552f02451236465c0c96a12a3de8f66c`；新版改用单个搜索框及输入/输出模式，不能直接套用其 UI API 到 1.20.1。

## 原来的等待路径

1. `PatternPreviewSync.tick()` 首次把所有物理槽位置脏，包括空槽。每 tick 最多构建 256 条，并受约 2 ms 预算限制；`uncached > 0` 时还不创建首次发送快照。
2. 完成构建后，`drain()` 又逐次发送最多 256 条的页。空槽虽然会压缩，页数仍随物理容量增长，小页也要等待下一次发送。
3. 客户端能逐页显示，但普通拿取和快速转移要求完整快照的 `revision >= 0`；该版本号到尾页才写入，所以会出现“看见了还拿不出来”。
4. 收到每条记录时都会调用客户端 `localize()`，提前准备搜索关键词；分批显示还会多次重建整个容量数组对应的列表。

以上是源码确认的路径；本次没有在用户的八联存档里测量具体耗时。

## 两版本的修改

- 首次从现有 `PatternCatalog` 占用索引读取普通样板和辅助容器槽位，避免逐个编码空槽。
- 首包一次发送供应器布局及占用位置清单，内容页只携带非空记录。客户端可以区分真实空槽与尚未收到内容的已占用槽。
- 首次不再按物理槽位构建预算分摊到多个 tick。所有不可变内容页一起排队，小页可在同一 tick 发完；实际网络发送继续遵守每玩家每 tick 的共享字节预算。
- 每个通道只保留一个正在传输的页及后续队列，保证大页未完成时后页不会越过它，同时保留不同通道间的轮流发送。
- 收到的样板立即允许按其物理地址发出拿取请求。服务端继续验证菜单、网络归属、物品内容与变更锁；辅助磁盘的只读约束沿用原行为。
- 客户端仅在搜索条件非空时准备本地化关键词，保留服务端已有关键词作为兼容回退。后续普通页按变更位置更新列表，避免每页重建整个容量列表。
- 只在完整快照完成后保存可复用缓存，继续支持缓存确认、拓扑重置、多玩家及磁盘配方增量。

## 本工程源码索引

两版本使用相同相对路径；各自保留 Forge / NeoForge、LDLib / LDLib2 和物品序列化适配。

| 源文件 | 方法或查找词 | 职责 |
| --- | --- | --- |
| [PatternCatalog.java](../src/main/java/cn/dancingsnow/neoecoae/grid/PatternCatalog.java) | `occupiedPatternSlots` | 读取已索引普通样板与辅助容器的占用位 |
| [ECOMachineInterfaceBlockEntity.java](../src/main/java/cn/dancingsnow/neoecoae/blocks/entity/ECOMachineInterfaceBlockEntity.java) | `getPatternPreviewLayout`、`getOccupiedPatternPreviewSlots` | 按通讯接口可见域生成容量与占用清单 |
| [PatternPreviewSync.java](../src/main/java/cn/dancingsnow/neoecoae/gui/crafting/PatternPreviewSync.java) | `tick`、`drain`、`send` | 稀疏首次快照、连续页及后续增量 |
| [PatternPreviewCodec.java](../src/main/java/cn/dancingsnow/neoecoae/gui/crafting/PatternPreviewCodec.java) | `write`、`read`、`layout`、`occupied` | 首包布局与占用清单的紧凑编码和边界校验 |
| [MenuDataTransport.java](../src/main/java/cn/dancingsnow/neoecoae/network/MenuDataTransport.java) | `sendQueued`、`Transfer.following`、`tick` | 共享预算、通道公平性和页顺序 |
| [CraftingPatternPreviewState.java](../src/main/java/cn/dancingsnow/neoecoae/gui/crafting/CraftingPatternPreviewState.java) | `emptyBaseline`、`awaitingEntries`、`operationRevision`、`localizeForSearch`、`receive` | 空容量恢复、加载中插入保护、提前拿取与按需搜索 |
| [PatternPreviewRows.java](../src/main/java/cn/dancingsnow/neoecoae/gui/crafting/PatternPreviewRows.java) | `changed`、`physicalCells` | 复用已有物理位置增量更新能力 |

## 回归入口与验证

| 测试 | 覆盖内容 |
| --- | --- |
| [PatternPreviewSyncTest](../src/test/java/cn/dancingsnow/neoecoae/network/PatternPreviewSyncTest.java) | 65,536 个槽位仅 2 个占用时首 tick 只编码 2 条；全空网络只发布局；删除条目携带原地址；缓存、多人、拓扑及磁盘增量 |
| [PatternPreviewCodecTest](../src/test/java/cn/dancingsnow/neoecoae/network/PatternPreviewCodecTest.java) | 稀疏首包恢复布局与物品；65,536 槽位、2 个普通物品的测试包小于 512 字节；既有地址编码与容量边界 |
| [CraftingPatternPreviewStateTest](../src/test/java/cn/dancingsnow/neoecoae/gui/crafting/CraftingPatternPreviewStateTest.java) | 尾页前拿取已收到的样板；不搜索时不调用搜索解码；插入跳过尚未收到的已占用槽；缓存与滚动 |
| [MenuDataTransportTest](../src/test/java/cn/dancingsnow/neoecoae/network/MenuDataTransportTest.java) | 多个小页同 tick 发送；大页期间顺序正确；共享预算；关闭菜单清理 |

1.20.1：相关测试共 40 项，0 失败、0 错误、1 跳过；Java 17 编译和 JAR 构建通过。
1.21.1：相关测试共 44 项，0 失败、0 错误、0 跳过；Java 21 编译和 JAR 构建通过。

两版 JAR 已检查包含更新后的 6 个生产类。测试明细在各工程的 `build/test-results/test/`；本次未在八联存档中实测首次打开耗时或真实鼠标交互。
