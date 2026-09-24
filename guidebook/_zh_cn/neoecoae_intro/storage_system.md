---
navigation:
  title: ECO 存储系统
  icon: neoecoae:storage_system_l9
  parent: neoecoae_intro/index.md
item_ids:
  - neoecoae:storage_system_l4
  - neoecoae:storage_system_l6
  - neoecoae:storage_system_l9
  - neoecoae:eco_drive
  - neoecoae:storage_interface
  - neoecoae:storage_casing
  - neoecoae:storage_vent
  - neoecoae:energy_cell_l4
  - neoecoae:energy_cell_l6
  - neoecoae:energy_cell_l9
  - neoecoae:eco_item_storage_cell_16m
  - neoecoae:eco_item_storage_cell_64m
  - neoecoae:eco_item_storage_cell_256m
  - neoecoae:eco_fluid_storage_cell_16m
  - neoecoae:eco_fluid_storage_cell_64m
  - neoecoae:eco_fluid_storage_cell_256m
---

# ECO 存储系统

<GameScene zoom="4" interactive={true}>
  <ImportStructure src="../scenes/store_min.nbt" />
  <IsometricCamera yaw="45" pitch="30" />
</GameScene>

ECO 存储系统为 ME 网络提供大容量存储。上方是最小结构，可以旋转查看各个部件的位置；需要更多容量时，再沿长度方向扩展驱动器和能量元件。

## 先把结构搭起来

<ItemGrid>
  <ItemIcon id="neoecoae:storage_system_l4" />
  <ItemIcon id="neoecoae:storage_system_l6" />
  <ItemIcon id="neoecoae:storage_system_l9" />
  <ItemIcon id="neoecoae:eco_drive" />
  <ItemIcon id="neoecoae:energy_cell_l4" />
  <ItemIcon id="neoecoae:energy_cell_l6" />
  <ItemIcon id="neoecoae:energy_cell_l9" />
  <ItemIcon id="neoecoae:storage_interface" />
  <ItemIcon id="neoecoae:storage_vent" />
  <ItemIcon id="neoecoae:storage_casing" />
</ItemGrid>

主机决定整套结构的等级，<ItemLink id="neoecoae:eco_drive" /> 用于放置 ECO 存储矩阵。高密度能量元件（<ItemLink id="neoecoae:energy_cell_l4" />、<ItemLink id="neoecoae:energy_cell_l6" />、<ItemLink id="neoecoae:energy_cell_l9" />）提供能量存储，等级必须与主机匹配。

<ItemLink id="neoecoae:storage_interface" /> 负责接入 ME 网络，<ItemLink id="neoecoae:storage_vent" /> 用于热量管理，<ItemLink id="neoecoae:storage_casing" /> 补齐结构框架。

搭建时先让主机朝外，再按场景确定各部件位置：

1. 用结构外壳搭出主机周围的框架，留出右方和右后方。
2. 在主机左后方放置通讯接口。
3. 在主机右侧水平排列驱动器。
4. 每一纵列驱动器背面，上下各放一个能量元件，中间放散热器。
5. 用外壳补齐剩余结构。

扩建时继续添加驱动器和能量元件。如果不想逐块放置，可以使用控制器里的[自动搭建面板](multiblock_builder.md)，先预览再补齐结构。

## 选用哪一级主机？

| 等级 | 控制器 | 存储容量 | 能量存储 |
|------|--------|----------|----------|
| L4 | <ItemLink id="neoecoae:storage_system_l4" /> | 每单元16MB | 10,000,000 AE |
| L6 | <ItemLink id="neoecoae:storage_system_l6" /> | 每单元64MB | 100,000,000 AE |
| L9 | <ItemLink id="neoecoae:storage_system_l9" /> | 每单元256MB | 1,000,000,000 AE |

三个等级分别对应入门的 L4、中级的 L6 和顶级的 L9，稀有度依次为罕见、稀有、史诗。准备材料时，把主机、能量元件和要使用的存储矩阵一起考虑。

## 装入存储矩阵

物品和流体矩阵都放入驱动器。两类矩阵各有 16MB、64MB、256MB 三档：

<ItemGrid>
  <ItemIcon id="neoecoae:eco_item_storage_cell_16m" />
  <ItemIcon id="neoecoae:eco_item_storage_cell_64m" />
  <ItemIcon id="neoecoae:eco_item_storage_cell_256m" />
  <ItemIcon id="neoecoae:eco_fluid_storage_cell_16m" />
  <ItemIcon id="neoecoae:eco_fluid_storage_cell_64m" />
  <ItemIcon id="neoecoae:eco_fluid_storage_cell_256m" />
</ItemGrid>

| 容量 | 物品矩阵 | 流体矩阵 |
|------|----------|----------|
| 16MB | <ItemLink id="neoecoae:eco_item_storage_cell_16m" /> | <ItemLink id="neoecoae:eco_fluid_storage_cell_16m" /> |
| 64MB | <ItemLink id="neoecoae:eco_item_storage_cell_64m" /> | <ItemLink id="neoecoae:eco_fluid_storage_cell_64m" /> |
| 256MB | <ItemLink id="neoecoae:eco_item_storage_cell_256m" /> | <ItemLink id="neoecoae:eco_fluid_storage_cell_256m" /> |

安装 AE2 Omni Cells、AE2 闪电科技、AppFlux 等模组后，还可以使用[兼容存储矩阵](compat_storage_matrices.md)存放更多资源类型。

## 接入 ME 网络

结构形成后，通过通讯接口连接 ME 网络，就能从网络中的终端访问已存入的物品和流体。主机界面会显示当前能量存储水平和能量容量百分比。

<SubPages icons={true} />
