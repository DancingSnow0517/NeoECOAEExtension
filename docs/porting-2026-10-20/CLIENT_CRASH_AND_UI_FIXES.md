# 客户端放置崩溃与 UI 绘制修复

日期：2026-10-21。工程：`E:\NeoECOAEExtension-1.20.1`。移植 main 基线仍为 `dc8344f48ee866e57e02c285fdda5e0fb5da9421`。

本轮从用户实际放置存储控制器的崩溃开始，随后按截图反馈修复公共 UI 绘制适配，并扩大到计算、合成控制器及四附属组合检查。此前只验证进入世界、小工作站菜单的结果保留为历史检查点。

## 崩溃原因与修复

1. **存储控制器、样板总线和机器接口的 AE2 库存字段。** `AppEngInternalInventoryMixin` 只实现 Forge 的 `INBTSerializable`，LDLib 1 的管理字段要求其自身的 `ITagSerializable`。补上接口后仍使用原来的 `inventory` NBT 编码、空槽清理和变更通知；无限组件库存继续只做持久化及菜单槽同步。
2. **合成控制器的冷却液字段。** LDLib 1 的内置流体 payload 接受其自己的流体包装类型，无法识别 Forge `FluidStack`。新增并注册 `NEFluidStackAccessor`，使用 Forge 原生 NBT 保存流体、mB 数量和标签，并复制可变标签。空流体和错误 payload 有专项测试。
3. **四附属组合中的 AE2LT CPU Mixin。** 实际锁定 JAR 的持久化注入要求 `(CompoundTag, HolderLookup.Provider)`，兼容门面被转换成单参数后，在计算控制器菜单首次加载 CPU 类型时失败。恢复门面的注册表参数签名，通过实际执行类的原生持久化回调完成读写；单参数 Forge 调用也进入这一条路径。开发客户端另行验证真实 Mixin 目标可加载、原生保存和读取各委派一次。此项不等同于完整合成任务或 AE2LT 超载状态恢复验收。

原始报告及中间失败记录保存在 `validation/inventory-crash-20261021/` 和 `validation/ui-rendering-20261021/`，包括最初的库存崩溃、冷却液崩溃及 AE2LT 签名崩溃。

## UI 绘制修复

- 恢复 `drawContents` 包住附加背景、子元素及裁剪的调用约定；自定义界面可同时变换或裁剪这部分内容。
- 文字绘制回到可覆写的附加背景阶段。适配字号的自定义 Label 不再额外绘制一次默认文字；文本按自己的区域裁剪。
- 落实 `zIndex` 的绘制顺序、位移和命中顺序，恢复侧栏图标。默认可见溢出与显式隐藏的视口分别处理，按钮边框不会被内容内边距截掉。
- 纹理资源及纹理组继续传递完整 `GUIContext`，延后绘制的槽位高亮保留原变换与裁剪范围。
- 仅为 ECO 保留式元素创建适配屏幕。固定布局超出窗口时统一缩放，点击、移动、拖动及滚动使用对应的坐标映射，裁剪也跟随变换。
- 开发探针在隔离世界运行，正常开发启动不自动创建世界。探针会移开指针、清理教程浮层，并处理失焦暂停，以便截图可读及避免测试超时。

对照图：[`before-after.png`](../../validation/ui-rendering-20261021/before-after.png)。十个菜单总览：[`after-base-gallery.png`](../../validation/ui-rendering-20261021/after-base-gallery.png)。单张原始游戏截图位于 `after-base/` 和 `after-combined/`，没有重绘菜单像素。

## 验证结果与边界

| 层次 | 本轮证据 |
| --- | --- |
| 完整主源码编译 | `build-local.ps1 compileJava jar` 通过；未排除业务源码 |
| JAR 静态检查 | `package-audit.json`：932 个主源码对应类、1,533 个 Java 17 类、59 个已登记 Mixin；缺失项及重复项均为空 |
| 专项 Java 测试 | `portingPlatformTest` 104 项、`portingContractsTest` 6 项，0 失败、0 跳过；后者为本轮已核验的未变化 Gradle 缓存结果 |
| 基础开发客户端 | `base-final.log`：10 个方块走真实客户端预测放置、服务端放置、菜单打开和关闭路径，正常保存退出 |
| 四附属开发客户端 | `combined-final.log`：AdvancedAE、ExtendedAE、ExtendedAE Plus、AE2LT 全部加载，相同 10 项菜单检查及保存退出通过 |
| 截图复核 | 两种环境各 10 张；覆盖存储 L4/L6/L9、存储/合成/计算接口、样板总线、小工作站、计算 L4、合成 L4 |
| 小窗口定位 | 854×480 窗口；计算接口适配比例约 `0.917`，最后一排过滤槽可通过真实屏幕输入入口命中 |
| 库存与持久化桥 | 物品数量、NBT、稀疏空槽清理、接收快照无回传、脏通知及注册表参数持久化委派均通过 |

上述菜单都是未成型状态；没有据此宣称完整机器运行、真实鼠标手势、多人同步或完整 CPU 故障恢复通过。本轮没有运行全量业务 `test`、独立业务专服或普通 Forge 安装；既有全量测试迁移问题仍单独记录。组合环境原有 AAE/AE2WTLib、EAE/ProjectE 可选数据错误仍保留在日志中，未阻断本轮检查。

机器记录为 `porting/client-crash-ui-validation.json`，包含日志、截图及当前 JAR 的哈希。本轮补充的中间探针失焦超时也保留在 `base-probe-focus-timeout.log`，不计为业务通过。

复测命令（两种 profile 串行运行）：

```powershell
.\build-local.ps1 compileJava jar portingPlatformTest portingContractsTest --offline --console=plain
.\build-local.ps1 runClient -PportingProfile=base -PclientSmoke=true -PclientSmokeInventories=true --offline --console=plain
.\build-local.ps1 runClient -PportingProfile=combined -PclientSmoke=true -PclientSmokeInventories=true --offline --console=plain
python tools/porting/audit_package.py --output validation/ui-rendering-20261021/package-audit.json
python tools/porting/record_client_ui_validation.py
```

普通开发启动仍可使用 `.\build-local.ps1 runClient --offline`；附属组合启动加 `-PportingProfile=combined`。
