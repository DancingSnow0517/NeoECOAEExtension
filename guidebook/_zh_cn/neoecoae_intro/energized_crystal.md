---
navigation:
  title: 盈能水晶母岩
  icon: neoecoae:flawless_budding_energized_crystal
  position: 10
  parent: neoecoae_intro/index.md
item_ids:
  - neoecoae:flawless_budding_energized_crystal
  - neoecoae:flawed_budding_energized_crystal
  - neoecoae:chipped_budding_energized_crystal
  - neoecoae:damaged_budding_energized_crystal
  - neoecoae:energized_crystal
---

# 获取盈能水晶母岩

盈能水晶母岩是制作本模组许多组件的关键材料。它的功能类似于AE2的赛特斯石英母岩，但生长的是盈能水晶。

## 水晶品质等级

<ItemGrid>
  <ItemIcon id="neoecoae:flawless_budding_energized_crystal" />
  <ItemIcon id="neoecoae:flawed_budding_energized_crystal" />
  <ItemIcon id="neoecoae:chipped_budding_energized_crystal" />
  <ItemIcon id="neoecoae:damaged_budding_energized_crystal" />
</ItemGrid>

盈能水晶母岩有四个品质等级：
- <ItemLink id="neoecoae:flawless_budding_energized_crystal" /> - 最佳品质，不会退化
- <ItemLink id="neoecoae:flawed_budding_energized_crystal" /> - 高品质
- <ItemLink id="neoecoae:chipped_budding_energized_crystal" /> - 中等品质
- <ItemLink id="neoecoae:damaged_budding_energized_crystal" /> - 最低品质

品质越高，水晶生长速度越快，耐久性越好。

## 方法一：雷击转化

放置AE2的赛特斯石英母岩方块，使用雷电击中它们。方块有一定概率转化为盈能水晶母岩。

### 转化对照表

| 原方块 | 转化结果 |
|--------|----------|
| <ItemLink id="ae2:flawless_budding_quartz" /> | <ItemLink id="neoecoae:flawless_budding_energized_crystal" /> |
| <ItemLink id="ae2:flawed_budding_quartz" /> | <ItemLink id="neoecoae:flawed_budding_energized_crystal" /> |
| <ItemLink id="ae2:chipped_budding_quartz" /> | <ItemLink id="neoecoae:chipped_budding_energized_crystal" /> |
| <ItemLink id="ae2:damaged_budding_quartz" /> | <ItemLink id="neoecoae:chipped_budding_energized_crystal" /> |

### 转化机制

- 转化概率基于与雷击点的距离随机决定
- 被闪电直接击中的方块转化率最高
- 雷击点周围3x3x3范围内的方块也可能转化
- 距离越近，转化率越高（中心基础概率：1/2，随距离递减）

### 小技巧
- 使用避雷针或引雷三叉戟来控制闪电击中的位置
- 将多个石英母岩紧密放置以提高转化效率
- 在雷暴天气时进行建造以利用自然闪电，或使用带有引雷附魔的三叉戟

## 方法二：水晶修复器（需要ExtendedAE）

如果安装了ExtendedAE模组，可以使用水晶修复器逐级升级盈能水晶方块。

<ItemGrid>
  <ItemIcon id="neoecoae:energized_crystal" />
</ItemGrid>

### 升级路径

| 输入 | 输出 | 成功率 |
|------|------|--------|
| <ItemLink id="neoecoae:energized_crystal" /> | <ItemLink id="neoecoae:damaged_budding_energized_crystal" /> | 80% |
| <ItemLink id="neoecoae:damaged_budding_energized_crystal" /> | <ItemLink id="neoecoae:chipped_budding_energized_crystal" /> | 80% |
| <ItemLink id="neoecoae:chipped_budding_energized_crystal" /> | <ItemLink id="neoecoae:flawed_budding_energized_crystal" /> | 5% |

### 使用方法
- 使用 <ItemLink id="neoecoae:energized_crystal" /> 作为水晶修复器的燃料
- 最后一级升级到"有瑕疵"只有5%成功率，请备足材料
- 这种方法比雷击更可控，但需要安装ExtendedAE

## 生长盈能水晶

获得盈能水晶母岩后，它们会随时间自然生长盈能水晶簇，类似于赛特斯石英母岩生长赛特斯石英水晶。

<ItemGrid>
  <ItemIcon id="neoecoae:energized_crystal" />
</ItemGrid>

从这些晶簇中收获的 <ItemLink id="neoecoae:energized_crystal" /> 用于制作ECO多方块系统的各种组件。
