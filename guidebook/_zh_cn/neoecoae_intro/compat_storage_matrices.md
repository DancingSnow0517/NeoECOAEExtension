---
navigation:
  title: 兼容存储矩阵
  icon: neoecoae:eco_drive
  position: 10
  parent: neoecoae_intro/storage_system.md
item_ids:
  - neoecoae:eco_omni_cell_housing
  - neoecoae:eco_omni_cell_16m
  - neoecoae:eco_omni_cell_64m
  - neoecoae:eco_omni_cell_256m
  - neoecoae:eco_complex_omni_cell_housing
  - neoecoae:eco_complex_omni_cell_16m
  - neoecoae:eco_complex_omni_cell_64m
  - neoecoae:eco_complex_omni_cell_256m
  - neoecoae:eco_quantum_omni_cell_housing
  - neoecoae:eco_quantum_omni_cell_16m
  - neoecoae:eco_quantum_omni_cell_64m
  - neoecoae:eco_quantum_omni_cell_256m
  - neoecoae:eco_lightning_cell_housing
  - neoecoae:eco_lightning_cell_16m
  - neoecoae:eco_lightning_cell_64m
  - neoecoae:eco_lightning_cell_256m
  - neoecoae:eco_fe_storage_cell_16m
  - neoecoae:eco_fe_storage_cell_64m
  - neoecoae:eco_fe_storage_cell_256m
  - neoecoae:eco_source_storage_cell_16m
  - neoecoae:eco_source_storage_cell_64m
  - neoecoae:eco_source_storage_cell_256m
  - neoecoae:eco_mana_storage_cell_16m
  - neoecoae:eco_mana_storage_cell_64m
  - neoecoae:eco_mana_storage_cell_256m
  - neoecoae:eco_chemical_storage_cell_16m
  - neoecoae:eco_chemical_storage_cell_64m
  - neoecoae:eco_chemical_storage_cell_256m
---

# 兼容存储矩阵

兼容存储矩阵让 <ItemLink id="neoecoae:eco_drive" /> 能够存储其他模组提供的资源类型。存储子系统控制器可以驱动与自身同级或更低等级的矩阵。

这些矩阵只会在对应模组存在时注册：**AE2 Omni Cells** 提供全能系列，**AE2 闪电科技**提供闪电系列，**AppFlux** 提供 FE 系列，**Ars Energistique** 提供魔源系列，**AppBot** 提供魔力系列，**AppMek** 提供化学品系列。

矩阵名称中的 `16M`、`64M` 和 `256M` 标识配方组件等级，并不总是实际存储字节容量。下列表格均以矩阵实际的 AE 存储字节容量为准。

## 全能存储矩阵

全能存储矩阵可以接收当前游戏中注册的全部 AE 资源类型，让物品、流体、能量、化学品及其他受支持资源共用一个矩阵。

### 全能

<ItemGrid>
  <ItemIcon id="neoecoae:eco_omni_cell_16m" />
  <ItemIcon id="neoecoae:eco_omni_cell_64m" />
  <ItemIcon id="neoecoae:eco_omni_cell_256m" />
</ItemGrid>

标准全能系列适合紧凑的混合存储，三个等级均可容纳最多 63 种资源。

| 矩阵 | 容量 | 类型上限 | 待机耗电 |
|------|------|----------|----------|
| <ItemLink id="neoecoae:eco_omni_cell_16m" /> | 256 MiB | 63 | 8 AE/t |
| <ItemLink id="neoecoae:eco_omni_cell_64m" /> | 1 GiB | 63 | 9 AE/t |
| <ItemLink id="neoecoae:eco_omni_cell_256m" /> | 4 GiB | 63 | 10 AE/t |

<ItemLink id="neoecoae:eco_omni_cell_housing" /> 使用末影锭制造，再与对应等级的 ECO 存储组件组合。

<RecipeFor id="neoecoae:eco_omni_cell_housing" />

### 复合全能

<ItemGrid>
  <ItemIcon id="neoecoae:eco_complex_omni_cell_16m" />
  <ItemIcon id="neoecoae:eco_complex_omni_cell_64m" />
  <ItemIcon id="neoecoae:eco_complex_omni_cell_256m" />
</ItemGrid>

复合全能矩阵以更高的待机耗电换取大幅提升的类型上限。

| 矩阵 | 容量 | 类型上限 | 待机耗电 |
|------|------|----------|----------|
| <ItemLink id="neoecoae:eco_complex_omni_cell_16m" /> | 256 MiB | 1,600 | 256 AE/t |
| <ItemLink id="neoecoae:eco_complex_omni_cell_64m" /> | 1 GiB | 3,200 | 512 AE/t |
| <ItemLink id="neoecoae:eco_complex_omni_cell_256m" /> | 4 GiB | 6,400 | 1,024 AE/t |

<ItemLink id="neoecoae:eco_complex_omni_cell_housing" /> 需要充能末影锭和复合链接处理器。

<RecipeFor id="neoecoae:eco_complex_omni_cell_housing" />

### 量子全能

<ItemGrid>
  <ItemIcon id="neoecoae:eco_quantum_omni_cell_16m" />
  <ItemIcon id="neoecoae:eco_quantum_omni_cell_64m" />
  <ItemIcon id="neoecoae:eco_quantum_omni_cell_256m" />
</ItemGrid>

量子全能矩阵没有类型数量上限，且与同级标准全能矩阵拥有相同容量。极高的灵活性也会带来显著的待机耗电。

| 矩阵 | 容量 | 类型上限 | 待机耗电 |
|------|------|----------|----------|
| <ItemLink id="neoecoae:eco_quantum_omni_cell_16m" /> | 256 MiB | 无限 | 6,561 AE/t |
| <ItemLink id="neoecoae:eco_quantum_omni_cell_64m" /> | 1 GiB | 无限 | 19,683 AE/t |
| <ItemLink id="neoecoae:eco_quantum_omni_cell_256m" /> | 4 GiB | 无限 | 59,049 AE/t |

<ItemLink id="neoecoae:eco_quantum_omni_cell_housing" /> 需要多维扩展处理器。量子全能矩阵需要在集成工作站中使用一个外壳、三个对应等级的量子全能存储元件、16 个红石粉、8 个下界石英和 4 个福鲁伊克斯水晶合成；拆解时返还外壳和三个存储元件。

<RecipeFor id="neoecoae:eco_quantum_omni_cell_housing" />

空的全能系列矩阵可以通过交替使用拆解，返还外壳和 ECO 存储组件。

## 其他兼容存储矩阵

### FE 存储矩阵

<ItemGrid>
  <ItemIcon id="neoecoae:eco_fe_storage_cell_16m" />
  <ItemIcon id="neoecoae:eco_fe_storage_cell_64m" />
  <ItemIcon id="neoecoae:eco_fe_storage_cell_256m" />
</ItemGrid>

FE 存储矩阵需要 **AppFlux**，用于在 ME 网络中存储 Forge Energy（FE）。它们在集成工作站中由对应等级的 AppFlux 能量核心、能量处理器、奇点和 FE 外壳合成。

| 矩阵 | 容量 | 类型上限 | 待机耗电 |
|------|------|----------|----------|
| <ItemLink id="neoecoae:eco_fe_storage_cell_16m" /> | 256 MiB | 1 | 256 AE/t |
| <ItemLink id="neoecoae:eco_fe_storage_cell_64m" /> | 1 GiB | 1 | 1,024 AE/t |
| <ItemLink id="neoecoae:eco_fe_storage_cell_256m" /> | 4 GiB | 1 | 4,096 AE/t |

### 魔源存储矩阵

<ItemGrid>
  <ItemIcon id="neoecoae:eco_source_storage_cell_16m" />
  <ItemIcon id="neoecoae:eco_source_storage_cell_64m" />
  <ItemIcon id="neoecoae:eco_source_storage_cell_256m" />
</ItemGrid>

魔源存储矩阵需要 **Ars Energistique**，用于存储 Ars Nouveau 的魔源。魔源外壳在附魔装置中制作，随后与对应等级的 ECO 存储组件合成矩阵。

| 矩阵 | 容量 | 类型上限 | 待机耗电 |
|------|------|----------|----------|
| <ItemLink id="neoecoae:eco_source_storage_cell_16m" /> | 16 MiB | 1 | 16 AE/t |
| <ItemLink id="neoecoae:eco_source_storage_cell_64m" /> | 64 MiB | 1 | 64 AE/t |
| <ItemLink id="neoecoae:eco_source_storage_cell_256m" /> | 256 MiB | 1 | 256 AE/t |

### 魔力存储矩阵

<ItemGrid>
  <ItemIcon id="neoecoae:eco_mana_storage_cell_16m" />
  <ItemIcon id="neoecoae:eco_mana_storage_cell_64m" />
  <ItemIcon id="neoecoae:eco_mana_storage_cell_256m" />
</ItemGrid>

魔力存储矩阵需要 **AppBot**，用于存储 Botania 魔力。魔力外壳由物品矩阵外壳注入 100,000 魔力制成，随后与对应等级的 ECO 存储组件合成矩阵。

| 矩阵 | 容量 | 类型上限 | 待机耗电 |
|------|------|----------|----------|
| <ItemLink id="neoecoae:eco_mana_storage_cell_16m" /> | 16 MiB | 1 | 16 AE/t |
| <ItemLink id="neoecoae:eco_mana_storage_cell_64m" /> | 64 MiB | 1 | 64 AE/t |
| <ItemLink id="neoecoae:eco_mana_storage_cell_256m" /> | 256 MiB | 1 | 256 AE/t |

### 化学品存储矩阵

<ItemGrid>
  <ItemIcon id="neoecoae:eco_chemical_storage_cell_16m" />
  <ItemIcon id="neoecoae:eco_chemical_storage_cell_64m" />
  <ItemIcon id="neoecoae:eco_chemical_storage_cell_256m" />
</ItemGrid>

化学品存储矩阵需要 **AppMek**，用于存储有效的 Mekanism 化学品。它们由化学品外壳和对应等级的 ECO 存储组件合成；不符合 Mekanism 属性要求的化学品不能存入。

| 矩阵 | 容量 | 类型上限 | 待机耗电 |
|------|------|----------|----------|
| <ItemLink id="neoecoae:eco_chemical_storage_cell_16m" /> | 16 MiB | 25 | 16 AE/t |
| <ItemLink id="neoecoae:eco_chemical_storage_cell_64m" /> | 64 MiB | 25 | 64 AE/t |
| <ItemLink id="neoecoae:eco_chemical_storage_cell_256m" /> | 256 MiB | 25 | 256 AE/t |

## 闪电存储矩阵

<ItemGrid>
  <ItemIcon id="neoecoae:eco_lightning_cell_16m" />
  <ItemIcon id="neoecoae:eco_lightning_cell_64m" />
  <ItemIcon id="neoecoae:eco_lightning_cell_256m" />
</ItemGrid>

闪电存储矩阵用于存储 AE2 闪电科技的高压闪电和极高压闪电。名称中的 LE 等级表示其科技阶段，而不是容量后缀。

| 矩阵 | 有效容量 | 类型数 | 待机耗电 | 加工机器 |
|------|----------|--------|----------|----------|
| <ItemLink id="neoecoae:eco_lightning_cell_16m" /> | 1,048,576 | 2 | 32,768 AE/t | 闪电模拟室 |
| <ItemLink id="neoecoae:eco_lightning_cell_64m" /> | 4,194,304 | 2 | 131,072 AE/t | 闪电装配室 |
| <ItemLink id="neoecoae:eco_lightning_cell_256m" /> | 16,777,216 | 2 | 524,288 AE/t | 过载处理工厂 |

### LE4 加工

闪电模拟室需要一个外壳、一个 16M ECO 存储组件和一个 V 级闪电存储核心。加工消耗 1,000,000 FE 和 32 高压闪电。

### LE6 加工

闪电装配室需要一个外壳、一个 64M ECO 存储组件、两个 V 级闪电存储核心、两个过载合金板和一个过载奇点。加工消耗 4,000,000 FE 和 64 极高压闪电。

### LE9 加工

过载处理工厂需要一个外壳、一个 256M ECO 存储组件、四个 V 级闪电存储核心、两个终极过载核心、两个苍穹合金锭、四个过载合金板、四个超导处理器、一个闪电坍缩矩阵以及 64,000 mB 凛冰溶液。加工消耗 16,000,000 FE 和 128 极高压闪电。

空的闪电存储矩阵可以通过交替使用拆解。外壳、ECO 存储组件和全部 V 级闪电存储核心会被返还，其他加工材料会被消耗。
