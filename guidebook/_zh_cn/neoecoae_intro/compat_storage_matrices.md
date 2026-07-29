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
---

# 兼容存储矩阵

兼容存储矩阵让 <ItemLink id="neoecoae:eco_drive" /> 能够存储其他模组提供的资源类型。存储子系统控制器可以驱动与自身同级或更低等级的矩阵。

这些矩阵只会在对应模组存在时注册。**AE2 Omni Cells** 提供全能系列。

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
| <ItemLink id="neoecoae:eco_omni_cell_16m" /> | 16 MB | 63 | 8 AE/t |
| <ItemLink id="neoecoae:eco_omni_cell_64m" /> | 64 MB | 63 | 9 AE/t |
| <ItemLink id="neoecoae:eco_omni_cell_256m" /> | 256 MB | 63 | 10 AE/t |

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
| <ItemLink id="neoecoae:eco_complex_omni_cell_16m" /> | 16 MB | 1,600 | 256 AE/t |
| <ItemLink id="neoecoae:eco_complex_omni_cell_64m" /> | 64 MB | 3,200 | 512 AE/t |
| <ItemLink id="neoecoae:eco_complex_omni_cell_256m" /> | 256 MB | 6,400 | 1,024 AE/t |

<ItemLink id="neoecoae:eco_complex_omni_cell_housing" /> 需要充能末影锭和复合链接处理器。

<RecipeFor id="neoecoae:eco_complex_omni_cell_housing" />

### 量子全能

<ItemGrid>
  <ItemIcon id="neoecoae:eco_quantum_omni_cell_16m" />
  <ItemIcon id="neoecoae:eco_quantum_omni_cell_64m" />
  <ItemIcon id="neoecoae:eco_quantum_omni_cell_256m" />
</ItemGrid>

量子全能矩阵没有类型数量上限，容量为对应普通矩阵的四倍。极高的灵活性也会带来显著的待机耗电。

| 矩阵 | 容量 | 类型上限 | 待机耗电 |
|------|------|----------|----------|
| <ItemLink id="neoecoae:eco_quantum_omni_cell_16m" /> | 64 MB | 无限 | 6,561 AE/t |
| <ItemLink id="neoecoae:eco_quantum_omni_cell_64m" /> | 256 MB | 无限 | 19,683 AE/t |
| <ItemLink id="neoecoae:eco_quantum_omni_cell_256m" /> | 1,024 MB | 无限 | 59,049 AE/t |

<ItemLink id="neoecoae:eco_quantum_omni_cell_housing" /> 需要多维扩展处理器。量子全能矩阵需要在集成工作站中使用一个外壳和四个对应等级的量子全能存储元件合成。

<RecipeFor id="neoecoae:eco_quantum_omni_cell_housing" />

空的全能系列矩阵可以通过交替使用拆解，返还外壳和 ECO 存储组件。
