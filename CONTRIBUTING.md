# Contributing to NeoECOAE Extension

## 架构速览 (Architecture at a Glance)

本项目是对 Applied Energistics 2 的扩展模组，运行于 Minecraft 1.20.1 Forge。
**不依赖 LDLib**——UI 和同步全部使用原生 Forge/Vanilla API。

### 数据同步三层模型

```
Layer 1: NBT 区块同步（初始加载）
  getUpdateTag() → writeUiSyncTag()     [服务端]
  handleUpdateTag() → readUiSyncTag()   [客户端]
  触发：区块加载、玩家进入视野

Layer 2: 网络通道 UI 推送（运行时，每秒1次）
  NEUiStateMachineMenu.broadcastChanges()
  → createState() → sendState()
  → NENetwork.CHANNEL.send(S2C Packet)
  触发：菜单打开后每 20 tick

Layer 3: C2S 用户操作（即时响应）
  按钮点击 → sendToServer(ActionPacket)
  → 服务端校验 → 执行逻辑 → sendStateNow()
```

### 关键文件索引

| 模块 | 位置 | 说明 |
|------|------|------|
| 网络通道 | `network/NENetwork.java` | 单一 SimpleChannel，14个包记录 |
| 菜单基类 | `gui/nativeui/menu/NEUiStateMachineMenu.java` | 20tick 周期推送，含重复抑制 |
| 菜单注册 | `gui/nativeui/NENativeMenus.java` | Forge MenuType 注册 |
| 屏幕基类 | `gui/nativeui/screen/NEBaseMachineScreen.java` | AE2 暗色面板风格 |
| 存储控制器 | `blocks/entity/storage/ECOStorageSystemBlockEntity.java` | 861行，含类型分组统计 |
| 合成控制器 | `blocks/entity/crafting/ECOCraftingSystemBlockEntity.java` | 1180行，含线程/冷却系统 |
| 计算控制器 | `blocks/entity/computation/ECOComputationSystemBlockEntity.java` | 650行 |
| 综合工作站 | `blocks/entity/ECOIntegratedWorkingStationBlockEntity.java` | 825行 |
| 多方块预览 | `multiblock/BuildPreviewState.java` | 预览状态共享记录 |
| 多方块构建接口 | `multiblock/INEMultiblockBuildHost.java` | 三方块控制器统一接口 |
| 维护文档 | `docs/maintenance/blockentity-update-semantics.md` | Dirty/Update 语义定义 |

### 添加新同步字段的步骤

以给 Crafting Controller 加一个 `craftCount` 字段为例：

```
1. BlockEntity 字段声明 + 计算逻辑
   ECOCraftingSystemBlockEntity.java

2. NBT 区块同步（Layer 1）
   writeUiSyncTag() → tag.putLong("craftCount", craftCount);
   readUiSyncTag()  → craftCount = tag.getLong("craftCount");

3. UI 状态记录（Layer 2）
   NECraftingUiState.java → 加字段

4. 网络包编解码
   NENetwork.java NECraftingUiStatePacket.encode() → buf.writeLong(...)
   NENetwork.java NECraftingUiStatePacket.decode() → buf.readLong(...)

5. 菜单创建状态
   NECraftingControllerMenu.createState() → 传入新字段值

6. 屏幕渲染
   NECraftingControllerScreen.renderAdditionalLabels() → 读取并绘制
```

> 💡 预览相关字段（previewMissingBlocks 等 11 个）已收敛到 `BuildPreviewState`。
> 新增预览字段只需修改 `BuildPreviewState.writeToTag()` / `readFromTag()` 一处。

### Dirty 标志语义

项目中所有 `*Dirty` 字段遵循统一的语义约定：

| 方法 | 含义 | 触发效果 |
|------|------|----------|
| `setChanged()` | 持久化数据变更 | 下次存档时保存 NBT |
| `markStorageStatsDirty()` | 存储统计缓存失效 | 下次 `ensureStorageStatsCurrent()` 重算 |
| `markStructureStatsDirty()` | 合成结构统计失效 | 重算 + `markUiStateDirty()` |
| `markComputationStatsDirty()` | 计算统计失效 | 重算 + `markUiStateDirty()` |
| `markUiStateDirty()` | UI 状态需重发 | `uiRevision++`，下一菜单 tick 推送 |
| `markForUpdate()` | 视觉/模型变更 | 客户端重新渲染方块 |

**不要混用**：纯数字 UI 变更只用 `markUiStateDirty()`，不要调 `markForUpdate()`。

### 多方块预览状态（BuildPreviewState）

三个控制器（Storage/Crafting/Computation）共享完全相同的预览字段。
`multiblock/BuildPreviewState.java` 是这些字段的唯一权威定义：

```java
// 每个 BlockEntity 中
private final BuildPreviewState preview = new BuildPreviewState();

// NBT 同步委托给 preview
preview.writeToTag(tag);   // 替代 10+ 行 tag.putXxx()
preview.readFromTag(tag);  // 替代 10+ 行 tag.getXxx()

// 预览状态更新
preview.syncPreview(missing, conflict, reused, required, statusKey);
preview.resetPreview("gui.neoecoae.multiblock.status.idle");
Component status = preview.buildStatusComponent();
```

### 编译与测试

```bash
# 编译
./gradlew compileJava

# 完整构建
./gradlew build

# 运行客户端
./gradlew runClient

# 性能分析（需安装 Spark mod）
/spark profiler start
/spark profiler stop
```

### 代码风格

- 使用 Spotless（Palantir Java Format 2.28.0）
- import 排序 + 移除未使用的导入
- 提交前运行 `./gradlew spotlessApply`

### 存档兼容性约束

- **不要**修改 `saveAdditional()`/`loadTag()` 中的 NBT key 名称
- **不要**修改 `NERegistries` 中的注册 ID
- **不要**修改 `NENetwork` 中的 packet 注册顺序
- **不要**重新引入 LDLib 依赖
