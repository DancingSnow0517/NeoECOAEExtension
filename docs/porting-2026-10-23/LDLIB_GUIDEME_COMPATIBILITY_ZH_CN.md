# LDLib 1.0.33.b 与 GuideME 20.1.14 兼容

运行时最低版本降低为：

| Mod | 支持的运行版本范围 | 日常开发依赖 |
| --- | --- | --- |
| LDLib | `[1.0.33.b,1.1)` | `1.0.52.a` |
| GuideME | `[20.1.14,20.2.0)` | `20.1.15` |

`gradle.properties`、`porting/dependencies.lock.json` 和已批准的 main 移植基线保持原值。最低运行版本和日常开发版本分别验证。

## 实际 API 适配

旧版 LDLib 缺少或改变了以下入口，因此需要先适配调用，再降低 `mods.toml` 的版本范围：

1. `IUIHolder.BlockEntityUI` 在 1.0.33.b 不存在。新增本工程的 `NEBlockEntityUI`，通过公共 `IUIHolder` 提供相同的方块实体失效检查、逻辑侧判断和脏标记行为；四种流体仓改用这个接口。
2. `Widget.isMouseDown` 和原生界面的 `isButtonPressed` 在旧版不存在。保留 UI 层使用自身菜单收到的按下、释放事件维护状态，子控件共用这一状态，释放在菜单外同样结束按下状态。
3. `TankWidget` 在旧版没有同步缓存的 getter。流体槽内部子类读取两个版本共有的受保护缓存字段；同步包、流体处理器和容量仍由原生控件管理。
4. 旧版流体绘制只有带容量及整数坐标的重载。使用共有重载，将容量设为当前渲染量填满指定区域，并通过矩阵保留浮点位置和尺寸；空流体或空区域直接跳过。
5. 旧版 JEI 分类要求配方继承 `ModularWrapper`，新版支持工厂函数。适配层缓存 ECO 配方对应的原生 wrapper，通过两个版本共有的无参分类构造器委托槽位、事件、绘制和提示。调用经 JEI `IRecipeCategory` 接口发出，避免泛型上界变更造成方法描述符不一致。

GuideME 的 `GuidesCommon.openGuide` 和 `PageAnchor.parse` 在两个目标标签间一致。已更新官方参考仓库远端，再比较 `v20.1.14`（`d491f882972f4c437e77a0790562a7d424bbfec9`）与 `v20.1.15`（`c2bc0b0c1996cf11fb71c4de348a9cbb004fc0a3`）；这两版的差异是指南槽位显示和 Lucene 启动日志修复，不影响本工程的指南按钮调用。

LDLib 参考仓库已更新远端，`origin/1.20.1` 为 `1d4e4390ae58d16038845c651e122185c29f334b`；旧版接口另外核对官方 1.0.33.b 发布 JAR 及同版 sources JAR。

## 发布文件校验及复现

最低版本验证直接使用官方发布文件，先核对 Modrinth 版本记录中的 SHA-512；验证入口每次构建还会核对下列 SHA-256。

| 文件 | Modrinth 版本 ID | SHA-256 |
| --- | --- | --- |
| `ldlib-forge-1.20.1-1.0.33.b.jar` | `FX8CaaJ8` | `5b1c73e63cd665249c79bd28146eac32a9f0838e1643e75b031d7062c393b86a` |
| `guideme-20.1.14.jar` | `9YGnKYDF` | `a0ba5a0288dab6e413f1456de8dd162f9479146ee681569837f6d6b17930a0e2` |

首次验证前准备文件：

```powershell
New-Item -ItemType Directory -Force libs/ui-compatibility
Invoke-WebRequest 'https://cdn.modrinth.com/data/B1CBVXHX/versions/FX8CaaJ8/ldlib-forge-1.20.1-1.0.33.b.jar' -OutFile libs/ui-compatibility/ldlib-forge-1.20.1-1.0.33.b.jar
Invoke-WebRequest 'https://cdn.modrinth.com/data/Ck4E7v7R/versions/9YGnKYDF/guideme-20.1.14.jar' -OutFile libs/ui-compatibility/guideme-20.1.14.jar
```

使用 Java 17，串行运行：

```powershell
.\build-local.ps1 compileJava portingPlatformTest upstreamSyncTest jar --init-script tools/porting/ui_dependency_compatibility.init.gradle '-PuiDependencyCompatibility=minimum' --offline --console=plain
.\build-local.ps1 compileJava portingPlatformTest upstreamSyncTest jar --init-script tools/porting/ui_dependency_compatibility.init.gradle '-PuiDependencyCompatibility=recommended' --offline --console=plain
```

两个配置分别输出到 `.porting/ui-dependency-compatibility/minimum` 和 `.porting/ui-dependency-compatibility/recommended`。运行目录也分别隔离；普通构建不加载此入口。

新增回归覆盖菜单外释放按键、子控件共享按下状态、没有本地流体处理器的客户端从原生初始同步包读取流体和容量，以及普通 ECO 配方模型通过原生 JEI wrapper 生成事件控件和提示。

本轮验证结果记录在 `porting/ui-dependency-compatibility-validation.json`。编译、单元测试和 JAR 静态审计独立于游戏启动、真实交互以及用户整合包的普通安装验收。

## 2026-10-23 验证结果

| 依赖组合 | 完整主源码编译及打包 | 平台测试 | 上游专项回归 |
| --- | --- | --- | --- |
| LDLib 1.0.33.b / GuideME 20.1.14 | 通过 | 180 通过 | 324 通过、9 跳过 |
| LDLib 1.0.52.a / GuideME 20.1.15 | 通过 | 188 通过 | 324 通过、9 跳过 |

验证期间其他任务增加了平台测试，因此两次测试总数不同。两套环境中的三个新增兼容测试均通过。

以开发依赖构建的最终 JAR 另外对两套依赖的实际 classfile 检查了 75 个类和 503 个成员引用，未发现缺失的依赖类或成员；一个继承自 Minecraft 菜单的字段单独按原版类和映射核对。最终 JAR 包含 1,600 个 Java 17 类，源码、资源、Mixin、嵌套依赖及重复条目静态审计通过。

交付文件为 `validation/ui-dependency-compatibility/neoecoae-20.6.0-dev.jar`，避免与工作区其他任务清理或重建 `build/libs` 的操作冲突。本轮没有启动游戏、验证玩家操作或安装到截图中的整合包。
