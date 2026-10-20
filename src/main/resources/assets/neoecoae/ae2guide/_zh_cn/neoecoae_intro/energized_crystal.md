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

进入 ECO 科技线，先要获得能生长盈能水晶的母岩。它的生长方式类似 AE2 的赛特斯石英母岩，收获的 <ItemLink id="neoecoae:energized_crystal" /> 会用在后续组件中。

## 用雷击获得第一块母岩

将 AE2 的赛特斯石英母岩放在一起，在雷暴天气利用自然闪电，或使用避雷针、带引雷附魔的三叉戟控制雷击位置。雷击有概率将它们转化为对应品质的盈能水晶母岩：

| 原方块 | 转化结果 |
|--------|----------|
| <ItemLink id="ae2:flawless_budding_quartz" /> | <ItemLink id="neoecoae:flawless_budding_energized_crystal" /> |
| <ItemLink id="ae2:flawed_budding_quartz" /> | <ItemLink id="neoecoae:flawed_budding_energized_crystal" /> |
| <ItemLink id="ae2:chipped_budding_quartz" /> | <ItemLink id="neoecoae:chipped_budding_energized_crystal" /> |
| <ItemLink id="ae2:damaged_budding_quartz" /> | <ItemLink id="neoecoae:chipped_budding_energized_crystal" /> |

被直接击中的方块转化率最高，中心基础概率为 **1/2**；雷击点周围 **3×3×3** 范围内的方块也可能转化，概率随距离递减。把多个母岩紧密放置，可以让一次雷击覆盖更多方块。

## 留下母岩，收获水晶

<Row>
  <BlockImage id="neoecoae:flawless_budding_energized_crystal" scale="2"></BlockImage>
  <BlockImage id="neoecoae:flawed_budding_energized_crystal" scale="2"></BlockImage>
  <BlockImage id="neoecoae:chipped_budding_energized_crystal" scale="2"></BlockImage>
  <BlockImage id="neoecoae:damaged_budding_energized_crystal" scale="2"></BlockImage>
</Row>

盈能水晶母岩有四个品质等级：
- <ItemLink id="neoecoae:flawless_budding_energized_crystal" /> - 最佳品质，不会退化
- <ItemLink id="neoecoae:flawed_budding_energized_crystal" /> - 高品质
- <ItemLink id="neoecoae:chipped_budding_energized_crystal" /> - 中等品质
- <ItemLink id="neoecoae:damaged_budding_energized_crystal" /> - 最低品质

品质越高，水晶生长速度越快，耐久性越好。

获得母岩后，等待它自然长出盈能水晶簇，再从晶簇中收获水晶。

<ItemGrid>
  <ItemIcon id="neoecoae:energized_crystal" />
</ItemGrid>

准备好盈能水晶，就可以继续制作[集成工作站](integrated_working_station.md)，加工 ECO 多方块系统的组件。

## 安装 ExtendedAE 后，还可以修复母岩

水晶修复器可以逐级升级盈能水晶方块，并使用 <ItemLink id="neoecoae:energized_crystal" /> 作为燃料：

| 输入 | 输出 | 成功率 |
|------|------|--------|
| <ItemLink id="neoecoae:energized_crystal" /> | <ItemLink id="neoecoae:damaged_budding_energized_crystal" /> | 80% |
| <ItemLink id="neoecoae:damaged_budding_energized_crystal" /> | <ItemLink id="neoecoae:chipped_budding_energized_crystal" /> | 80% |
| <ItemLink id="neoecoae:chipped_budding_energized_crystal" /> | <ItemLink id="neoecoae:flawed_budding_energized_crystal" /> | 5% |

这条路线比雷击更可控，但最后一级升级到“有瑕疵”的成功率只有 **5%**，需要备足材料。表中的升级路径止于有瑕疵母岩。
