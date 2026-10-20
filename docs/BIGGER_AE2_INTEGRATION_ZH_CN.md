# Bigger AE2 存储矩阵联动

本次提供 12 个独立 ECO 矩阵。需要 Bigger AE2 21.2.3 或更新版本；未安装时不注册联动矩阵，也不加载其配方。

| 资源类型 | Quantum | Digital Singularity | 可存种类数 | 额外模组 |
|---|---|---|---|---|
| 物品 | L6 | L9 | 5 | 无 |
| 流体 | L6 | L9 | 5 | 无 |
| 化学品 | L6 | L9 | 5 | Applied Mekanistics |
| FE | L6 | L9 | 1 | Applied Flux |
| 经验 | L6 | L9 | 1 | Applied Experienced |
| 魔源 | L6 | L9 | 1 | Ars Énergistique |

Quantum 保留上游 128 MiB 的共享容量、每种资源 65,536 字节开销和 20 AE/t 待机耗能；扩大种类数不会将总容量乘以 5。L6 表示 ECO 主机要求和外观等级。

Digital Singularity 保留数量不受 long 上限限制的存储语义，待机耗能为 25 AE/t，使用 L9 等级。数量存入不可变的数据组件，普通 AE2 数量接口提供饱和到 `Long.MAX_VALUE` 的投影；终端通过 ECO 精确数量通道显示实际存量。这些盘只返还真正存入的资源，不生成资源。

物品、流体和化学品的两种盘都支持 5 个不同资源；FE、经验和魔源保持 1 个。化学品仍执行 Applied Mekanistics 的普通化学品校验，不接收其禁止的化学属性。

## AE2 终端超 long 数量显示

六种 Digital Singularity 联动盘与 ECO 无限存储使用同一套终端精确数量同步、格式化和排序链路。盘装在满足 L9 要求的 ECO 存储系统中，连接该网络的 AE2 终端会显示：

- 格子使用实际数量的缩写，例如 `12,345,678,901,234,567,890` 显示为 `12.3E`；悬停显示带千位分隔的完整数量。
- 同一种资源在多个数字奇点盘和普通盘中的存量按精确值汇总；普通 AE2 投影保持饱和，避免汇总后变成负数。
- 数量排序比较完整数值；两个盘的存量即使都超过 `Long.MAX_VALUE`，仍能区分大小。
- 存量持续增加、减少或清空时，独立精确通道更新显示；流体等资源沿用其资源键定义的显示单位。

这里的无限表示存储容量不设数量上限，终端仍显示已经存入的有限数量。Quantum 仍为 128 MiB 有限盘。

## 合成与拆解

全部成品均使用工作台无序合成，每次产出 1 个：

| 资源 | 外壳 | Quantum 原件 | Digital Singularity 原件 |
|---|---|---|---|
| 物品 | `neoecoae:eco_item_cell_housing` | `bigger_ae2:quantum_cell_component` | `bigger_ae2:digital_singularity_cell_component` |
| 流体 | `neoecoae:eco_fluid_cell_housing` | 同上 | 同上 |
| 化学品 | `neoecoae:eco_chemical_cell_housing` | 同上 | 同上 |
| FE | `neoecoae:eco_fe_cell_housing` | 同上 | 同上 |
| 经验 | `neoecoae:eco_experience_cell_housing` | 同上 | 同上 |
| 魔源 | `neoecoae:eco_source_cell_housing` | 同上 | 同上 |

外壳复用现有 ECO 对应类型外壳；外壳自身的获取方式不属于这次调整。上述配方与 Bigger AE2 自己的原盘配方使用不同外壳，因此不会出现同一组材料得到两种成品的冲突。

12 个空盘都提供 AE2 拆解配方，返还原外壳和对应 Bigger AE2 原件。带内容的盘沿用 ECO 的空盘拆解限制，不能借拆解丢弃内容。

配方位于 `src/main/resources/data/neoecoae/recipe/`，拆解配方位于其 `disassembly/` 子目录，每个配方都带有模组加载条件。化学品、FE、经验和魔源同时检查其各自模组。

## 保存与迁移

Quantum 使用 ECO 普通有限盘的存档方式。Digital Singularity 使用 `neoecoae:bigger_ae2_singularity_contents`，保存完整资源键及精确数量，并支持物品堆复制和网络同步。

Digital Singularity 不参加 ECO 无限存储域的有限盘迁移协议，以免将超过 long 的精确数量饱和后误迁移。盘可继续在普通 ECO 驱动中使用，原版 Bigger AE2 物品不被覆盖或转换。

物品模型使用 `textures/item/eco_cells/bigger_ae2/` 下已有外壳、公共等级灯和状态灯；数字奇点叠加其动画层。驱动器模型使用已有 Quantum L6 和 Digital Singularity L9 模型。
