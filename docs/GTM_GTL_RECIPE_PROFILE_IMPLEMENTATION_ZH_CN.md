# GTM / GTL 制作路线实装说明

日期：2026-10-24。根据对话 `01a16dd6-1ec6-7ec0-b7fa-9d52fed6f041` 的最终配方定案实施，工程 main 移植基线保持不变。

## 启用

启动过本模组后，编辑实例或专服目录下的 `config/neoecoae-recipes-common.toml`：

```toml
# 默认独立路线：
profile = "STANDALONE"
```

改为 `profile = "GTM"` 使用 GTM 路线，或 `profile = "GTL"` 使用 GTL 路线。修改后完整重启游戏或专服；这是实例级配置，不使用存档中的 serverconfig。远程连接时以服务端同步的实际配方为准。

GTM 要求安装 `gtceu`；GTL 要求同时安装 `gtceu`、`gtlcore`。配置与依赖不匹配会给出明确错误，避免意外回退到便宜的独立配方。

每个 GT 档案自动启用两个必选服务端数据包：`neoecoae:recipe_profile/gtm` 或 `neoecoae:recipe_profile/gtl`，加上与实际 GT API 对应的 `neoecoae:recipe_profile/gt_legacy` 或 `neoecoae:recipe_profile/gt_modern`。独立模式不注册这些数据包、不读取 GT 类。数据包重载沿用当前档案；切换档案需要重启。

## 已落实的配方

| 内容 | 数量与行为 |
| --- | --- |
| 原生工作站替换 | 42 条，保留原 AE 费用、100 tick、插件加载条件 |
| 类型升级卡 | 4 条，空白卡加原机/指定材料，16,000 AE、100 tick |
| GT 原机制造 | 16 条前置与结构件路线，包含 EV 首台工作站、洁净室处理器、4500 K 黑钨合金高炉 |
| Neo 矿石处理 | 4 条研磨路线；成品粉继续走原 GT 冶炼流程 |
| 成品归一 | 6 条 Neo 纯铝/钨同形态成品 1:1 转为 GT 成品 |
| 压块与拆块 | 4 条，严格保持 9:1，输出统一为 GT 成品 |
| 本地铝原料重绑 | 7 条保留配方，使用私有铝标签：5 种工具、物品元件壳体、压印制粉 |
| AdvancedAE 调整 | 2 条，保留原始 64 个产出、500,000 AE 与权限；改用 500 mB GT 蒸馏水 |
| 旧捷径屏蔽 | 27 个精确配方 ID；空 Forge 条件配方覆盖，独立模式完全保留原资源 |
| 私有材料标签 | 6 个；铝、硅、钻石基底与指定超导合金白名单，不修改公共材料/水标签 |

一套 GT 档案含 92 份配方文件（包括 27 份屏蔽覆盖）和 6 份标签；配合 20 份 GT 原机配方。GTM/GTL 两套工作站资源各自独立，GT 原机资源按旧/新 API 另行生成，运行时仅选择一套。

大型结构每批产出 4 个，结构与接口使用 16M 组件。GTM 使用 EV/LuV/UV 存储阶段、IV/LuV/UV 主机阶段；GTL 按定案提高相应后期部件，并保留 IV 大型结构。无限配方保留 256M 组件合计 128 个，不压低成本。

GT 原机配方使用其原生 EU/t、加工 tick、流体、`ebf_temp` 和洁净室条件。旧 API 使用 String 能力键，新 API 使用命名空间能力键及显式 1 A 电压输入；按已安装的 `GTRegistries.RECIPE_CAPABILITIES` 字段类型选择，不根据版本字符串猜测。没有把 GT 的 EU 或工艺温度换算成 ECO 的 AE/设备温度。

AE 压印模板、压印件、处理器、水晶矩阵、母岩雷击与晶簇生长路线继续保留。现有 EAE 联动与 `compat/megacells/` 下额外 14 条可选工作站配方保持原样；它们不属于原定案列出的 42 条替换。

## 维护与验证

定案输入为 `docs/GTM_GTL_WORKSTATION_RECIPE_PLAN.json`。实际游戏资源由生成器写入 `src/main/resources/recipe_profiles/`，没有把设计清单直接当作 Minecraft 配方加载。

```powershell
python scripts/generate-gt-recipe-packs.py
python scripts/generate-gt-recipe-packs.py --check
```

生成器核对 42 条指定配方与旧配方 ID，保留附属条件；额外的 MEGACells 家族明确保留。新增主体工作站配方或更改定案后，必须更新覆盖清单并重新生成。`--check` 只读核对全部生成文件和多余文件。

已通过 `build-local.ps1` 选择的 Java 17 全量主体编译。配方档案专项 13 项通过，包含普通 Forge 配方反序列化、内置数据包覆盖/重载、新旧 GT 格式、依赖缺失处理、档位、数量、私有标签、AAE 整批合同，以及不使用任何工作站的首台控制器前置依赖检查。

相关回归共 **314 项，314 通过，0 失败、0 错误、0 跳过**，覆盖 `recipe.*`、大型工作站实体、配方展示、JEI 与 Applied Flux 能源合同。`jar`、`reobfJar` 通过；已逐字节核对成品内全部 240 份配方包文件（236 份数据资源、4 份包元数据），确认新增代码为 Java 17 字节码，未打包 GT 实现类或测试类。

验证输出隔离在 `.gradle/gt-recipe-profiles-build`，没有并行运行本次 Gradle 验证：

- 测试报告：`.gradle/gt-recipe-profiles-build/reports/tests/test/index.html`。
- 构建日志：`.gradle/gt-recipe-profiles-verification/regression-and-jar.log`。
- 可测试 JAR：`.gradle/gt-recipe-profiles-build/libs/neoecoae-20.5.0.jar`。
- JAR SHA-256：`f833ee6e817e6bb9fd1718f95698a8c20470e48417a77a06e9dc937d84fe164b`。

复查命令（隔离目录由 `.gradle/gt-recipe-profiles-verification/isolated.init.gradle` 指定）：

```powershell
.\build-local.ps1 -GradleArguments @(
    '--init-script', '.gradle/gt-recipe-profiles-verification/isolated.init.gradle',
    'test',
    '--tests', 'cn.dancingsnow.neoecoae.recipe.*',
    '--tests', 'cn.dancingsnow.neoecoae.blocks.entity.LargeWorkstation*Test',
    '--tests', 'cn.dancingsnow.neoecoae.integration.recipe.*',
    '--tests', 'cn.dancingsnow.neoecoae.integration.jei.*',
    '--tests', 'cn.dancingsnow.neoecoae.integration.appflux.*',
    'jar', 'reobfJar', '--console=plain'
)
```

目前验证不包含真实 GT 实例中的机器加工、KubeJS/其他数据包最终覆写结果或游戏内平衡。没有自动启动游戏或改变本机实例配置。
