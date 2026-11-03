---
navigation:
  title: 大型集成工作站
  icon: neoecoae:integrated_working_station
  position: 21
  parent: neoecoae_intro/index.md
item_ids:
  - neoecoae:large_integrated_working_station_casing
  - neoecoae:large_integrated_working_station_input_hatch
  - neoecoae:large_integrated_working_station_smart_cooling_module
  - neoecoae:large_integrated_working_station_output_hatch
  - neoecoae:large_integrated_working_station_interface
  - neoecoae:integrated_working_station_module_empty
---

# 大型集成工作站

<GameScene interactive={true} fullWidth={true} zoom="3">
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="0" y="0" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="1" y="0" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="2" y="0" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="0" y="1" z="0"></Block>
  <Block id="neoecoae:integrated_working_station" p:formed="true" x="1" y="1" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="2" y="1" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_output_hatch" p:formed="true" x="0" y="0" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_interface" p:formed="true" x="1" y="0" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_input_hatch" p:formed="true" x="2" y="0" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="0" y="1" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="1" y="1" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="2" y="1" z="1"></Block>
</GameScene>

大型集成工作站把一台[集成工作站](integrated_working_station.md)扩建成 3×2×2 的多方块机器。成型后它作为 ME 样板供应器接入网络，能一次并行加工数万份配方，并通过升级模块加工其他 AE 附属模组的机器配方。

JEI / EMI 多方块预览右上角的**升级卡模型**按钮可切换当前已注册的升级卡：左键下一项，右键上一项。切换后自动显示对应成型外观，并在材料列表中加入所选卡；常规外观不需要安装卡片。分层与成型外观按钮仍可单独使用。

和单方块工作站相比，大型工作站：

- 从 ME 网络接单、取料和交付产物，不需要手动放料；
- 常规单批并行 256 份，升温后最高 262,144 份；
- 安装升级模块后，可以替代高级 AE、扩展 AE、水晶科技和闪电科技的加工机器。

## 搭建结构

<ItemGrid>
  <ItemIcon id="neoecoae:integrated_working_station" />
  <ItemIcon id="neoecoae:large_integrated_working_station_casing" />
  <ItemIcon id="neoecoae:large_integrated_working_station_interface" />
  <ItemIcon id="neoecoae:large_integrated_working_station_input_hatch" />
  <ItemIcon id="neoecoae:large_integrated_working_station_output_hatch" />
</ItemGrid>

结构需要 1 台 <ItemLink id="neoecoae:integrated_working_station" />（作为主机）、8 个 <ItemLink id="neoecoae:large_integrated_working_station_casing" />、1 个 <ItemLink id="neoecoae:large_integrated_working_station_interface" />、1 个 <ItemLink id="neoecoae:large_integrated_working_station_input_hatch" /> 和 1 个 <ItemLink id="neoecoae:large_integrated_working_station_output_hatch" />。这些部件都在集成工作站中合成，配方见本节末尾。

结构分前后两排、上下两层。下面的场景可以拖动旋转，悬停带颜色的方块查看说明。

**第一步：下层。** 前排三个外壳；后排站在结构背后看，从左到右依次是**冷却液输出仓、通讯接口、冷却液输入仓**。

<GameScene interactive={true} zoom="3">
  <Block id="neoecoae:large_integrated_working_station_casing" x="0" y="0" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" x="1" y="0" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" x="2" y="0" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_output_hatch" x="0" y="0" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_interface" x="1" y="0" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_input_hatch" x="2" y="0" z="1"></Block>
  <IsometricCamera yaw="45" pitch="30" />
  <BlockAnnotation x="0" y="0" z="1" color="#ffa726">
    **冷却液输出仓**：从背面看在左侧，接收冷却副产物。
  </BlockAnnotation>
  <BlockAnnotation x="1" y="0" z="1" color="#4fc3f7">
    **通讯接口**：在后排中间，连接 ME 网络并存放样板。
  </BlockAnnotation>
  <BlockAnnotation x="2" y="0" z="1" color="#66bb6a">
    **冷却液输入仓**：从背面看在右侧，可换成智能冷却模块。
  </BlockAnnotation>
</GameScene>

**第二步：上层。** 集成工作站放在**上层前排中间**，正面朝外；它的左右各一个外壳，后排三个外壳。

<GameScene interactive={true} zoom="3">
  <Block id="neoecoae:large_integrated_working_station_casing" x="0" y="0" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" x="1" y="0" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" x="2" y="0" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_output_hatch" x="0" y="0" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_interface" x="1" y="0" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_input_hatch" x="2" y="0" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" x="0" y="1" z="0"></Block>
  <Block id="neoecoae:integrated_working_station" x="1" y="1" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" x="2" y="1" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" x="0" y="1" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" x="1" y="1" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" x="2" y="1" z="1"></Block>
  <BlockAnnotation x="1" y="1" z="0" color="#ffee58">
    **主机**：集成工作站，必须在上层前排中间，正面朝外。
  </BlockAnnotation>
</GameScene>

结构正确后会自动成型，外壳隐藏，整体换成大型工作站外观。

也可以只放下主机，打开它的界面，点击侧边的**自动搭建大型集成工作站**按钮。背包里有足够材料（创造模式不需要）且目标位置没有冲突方块时，会按主机朝向一次放好全部部件。

### 部件配方

<Row>
  <RecipeFor id="neoecoae:large_integrated_working_station_casing" />
  <RecipeFor id="neoecoae:large_integrated_working_station_interface" />
</Row>

<Row>
  <RecipeFor id="neoecoae:large_integrated_working_station_input_hatch" />
  <RecipeFor id="neoecoae:large_integrated_working_station_output_hatch" />
</Row>

## 接入网络并放入样板

用线缆连接**通讯接口**，大型工作站就会作为样板供应器出现在 ME 网络中。打开通讯接口，把加工样板放进样板槽：样板要写明配方的全部物品、流体输入与输出。之后像普通样板供应器一样下单即可，材料和产物都经由 ME 网络流转。

工作站自身的配方和兼容配方，在 JEI / EMI 的“大型集成工作站”分类中查看。单方块集成工作站不能加工这些兼容配方。

通讯接口初始有 **4 行、36 个样板槽**。在右侧升级槽装入 AE2 的**容量卡**，每张多开 1 行，最多 4 张，即 **8 行、72 格**。取出容量卡前，要先清空将被关闭的最后一行。旧存档中超出当前容量的样板仍可取出，但要补装容量卡才能继续往那些行放样板。

中间的样板区域最多同时显示 7 行，鼠标滚轮带平滑过渡，右侧滚动条可连续拖动；上下边缘会裁切正在滑入、滑出的槽位。取出容量卡前，需要先清空即将关闭的最后一行。旧存档中超出当前容量的样板会保留，仍可取出，但需要补装容量卡才能继续向这些行放入样板。

## 升级模块与兼容配方

主机界面右上角有一个**升级模块**槽，一次只能装一张。不同的卡解锁不同模组的配方：

| 升级模块 | 解锁的配方 |
| --- | --- |
| 空白 | 不增加配方，只作为其他模块的合成材料 |
| 高级 AE | 高级 AE 反应仓（包括流体产物） |
| 扩展 AE | 扩展 AE 与 EAEP 的水晶装配器配方 |
| 水晶科技 | 晶体催生、晶能聚合、电路蚀刻、粉碎与熵变配方 |
| 闪电科技 | 过载处理工厂、闪电装配室、闪电模拟室 |

对应模组安装后，模块才会出现。所有模块都在大型工作站中合成，每张耗能 16,384,000 AE，空白模块是其他模块的基础材料。工作站自身的配方和没有专用模块的兼容配方始终可用。

<RecipeFor id="neoecoae:integrated_working_station_module_empty" />

装上模块后，控制器灯光、运行屏幕和界面电路会换成该模组的主题色。下面从左到右依次是常规、高级 AE、水晶科技和闪电科技的外观：

<GameScene interactive={true} fullWidth={true} zoom="2">
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="0" y="0" z="0"></Block>
  <Block id="neoecoae:integrated_working_station" p:formed="true" p:working="true" p:module="regular" x="1" y="1" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="2" y="1" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="4" y="0" z="0"></Block>
  <Block id="neoecoae:integrated_working_station" p:formed="true" p:working="true" p:module="advanced_ae" x="5" y="1" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="6" y="1" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="8" y="0" z="0"></Block>
  <Block id="neoecoae:integrated_working_station" p:formed="true" p:working="true" p:module="ae2cs" x="9" y="1" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="10" y="1" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="12" y="0" z="0"></Block>
  <Block id="neoecoae:integrated_working_station" p:formed="true" p:working="true" p:module="aelt" x="13" y="1" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="14" y="1" z="1"></Block>
  <BlockAnnotation x="1" y="1" z="0" color="#878fa5">常规（未装模块或空白模块）</BlockAnnotation>
  <BlockAnnotation x="5" y="1" z="0" color="#b668d0">高级 AE</BlockAnnotation>
  <BlockAnnotation x="9" y="1" z="0" color="#7dcbe8">水晶科技</BlockAnnotation>
  <BlockAnnotation x="13" y="1" z="0" color="#ed7ccc">闪电科技</BlockAnnotation>
</GameScene>

- **同类配方任选一张。** 多个模组都能做的同一产物，在 JEI / EMI 中合并为一项，右侧列出全部可用模块，装任意一张即可。悬停模块可查看那条路线的单批产量、加工时间与费用。各路线保留自己的最小批量：例如扩展 AE 一批 4 个、水晶科技一批 32 个，装水晶科技模块时就必须按 32 个一批下单。
- **换卡不会丢任务。** 更换或取出模块后，通讯接口刷新可用样板，机器外观切换成对应主题色；已接收的任务保留材料与进度，可以继续完成。
- **服务器可忽略模块。** 配置 `largeWorkstationIgnoreUpgradeCards` 默认 `false`。设为 `true` 并重新进入世界或重启服务器后，不装模块也能使用所有兼容配方。

部分路线的额外规则：

- **晶体催生**（水晶科技）：每个种子产出一个对应的高纯晶体，不消耗水，已部分生长的种子也能用。按种子完整生长周期计费，默认每个 6,000 AE，JEI / EMI 显示的来源时间为 200 tick；新批次实际按 1 tick 加工。
- **闪电**（闪电科技）：配方保留原有的闪电档位和数量。工作站自动从 ME 网络提取闪电，不用写进样板；缺闪电时暂停等待。高压闪电不能替代超高压闪电。取消未完成的合成任务时，归还已提取的闪电。

## 并行与温度

大型工作站默认开启**温度机制**：开启超频并让机器升到对应温度，才能使用更高的并行。

| 档位 | 需要的温度 | 单批并行上限 |
| --- | ---: | ---: |
| 常规 | 无要求 | 256 |
| 4 | ≥ 20% | 16,384 |
| 6 | ≥ 50% | 65,536 |
| 9 | ≥ 80% | 262,144 |

能量按实际加工的配方份数支付：一批同时做 100 份，就付 100 份配方的能量。高并行只提高同时加工的份数，不减少总耗能。

档位只读，由当前温度自动决定。**左键**超频按钮开关超频；超频本身不会升温，要有配方实际在加工才会产热。

主机界面右下三行依次显示：当前档位与温度、冷却液与实际降温速度、超频状态与并行上限。悬停可以看详细数值，包括最近一秒的加工产热、预热、冷却和净变化。

**升档。** 新批次使用当前温度已经达标的最高档位。可用并行提高后，当前批次会从同一合成任务的就绪区段补入同配方且每份输入输出一致的材料，补到当前并行上限；可以跳过输入已齐备、尚未推进的不同配方任务，它们仍保留在原队列位置。跨配方补批前会模拟检查扩大后的下一加工步能否由当前 AE、FE 缓存或网络 FE 支付，无法支付时保留可运行的小批次。遇到其他合成任务、已推进、降档分段、等待输出或缺少额外输入的任务时停止跨任务扫描；跨任务补批不提前提取后续任务的额外输入。放不下的部分仍留在原任务中。温度模式和冷却液模式均支持补批，已付工作量、进度余数与剩余输入输出随存档保留，不重复扣费。

**降档。** 加工中温度跌破该批次的门槛时，剩余部分自动改为每段最多 256 份继续做完，不需要重新升温，也不会重复扣费。之后只要开启超频、温度回到原档位门槛，就会自动恢复原来的并行。

## 升温与预热

温度范围为 **0%～100%**，用于约束持续超频。超频开启、能源支付成功且配方实际推进时，记为一个工作 tick；同一服务器 tick 内推进多个批次或多次推进只计一次产热。连续加工时每秒产热为 `0.65 + 3.35 × (1 − 温度百分比 / 100)²` 个百分点，按实际工作 tick 数折算，**与批量、倍率样板、配方时长及当前最大并行容量无关**。新任务仍按 1 tick 加工，小批次不会因高并行容量而几乎不升温；低于首档门槛的普通批次也可在超频开启时产热。**低温起步快，温度越高，产热越慢**；关闭超频、空闲、支付失败、缺料暂停或等待输出时不产加工热，旧存档已经付费的进度也不重复产热。

20 TPS、每 tick 持续超频加工且不开冷却时的大致时间：

| 区间 | 用时 |
| --- | ---: |
| 0% → 20% | 约 6 秒 |
| 20% → 50% | 约 15 秒 |
| 50% → 80% | 约 29 秒 |
| 80% → 100% | 约 29 秒 |

**电预热。** 不想靠加工慢慢升温，可以**右键**超频按钮启动预热（再次右键停止）。预热满功率耗电 1,000 AE/t，升温速度是满载加工的 2 倍；供电不足时按实际供电比例减速。预热到下一个档位门槛（20% / 50% / 80%）就会停下，需要再次右键才继续；开始加工后预热也会停止。从 0% 预热到 20% / 50% / 80% 累计约需 3 / 10 / 25 秒。

**自然冷却。** 没有超频加工也没有预热时，温度每秒自然下降 0.1 个百分点，直到 0%。

## 冷却

温度达到 100% 会过热停机，所以长时间满负载时需要主动冷却。冷却液从**冷却液输入仓**送入，副产物进入**冷却液输出仓**，两个仓各能存 1,024,000 mB。

在主机界面打开**主动冷却**后，每 tick 消耗 100 mB 冷却液（每秒 2 B）。主动冷却独立于超频：即使机器空闲，只要温度高于 0% 且液体充足，就会继续消耗并降温，到 0% 才停。

| 冷却液 | 每秒降温 | 副产物 |
| --- | ---: | --- |
| 水 | 0.1 个百分点 | 装有 Mekanism 时产生蒸汽，否则无 |
| 钠 | 0.3 个百分点 | 过热钠（Mekanism） |
| 凛冰溶液 | 0.6 个百分点 | 无 |

冷却液越强，降温越多，但也可能把温度压到档位门槛以下：

高级冷却液不保证温度只升不降。80% 温度连续超频加工时产热为 **+0.784 个百分点/s**，通入凛冰溶液后净变化为 **+0.184 个百分点/s**；持续加工并通入凛冰时，80% → 100% 约需 **4 分 10 秒**。每秒只有一半 tick 实际加工时，加工产热约为 **+0.392 个百分点/s**，空档还会自然降温，同样的凛冰散热可能使温度下降。批次数量较少但每 tick 都持续加工时，仍使用连续加工的产热速率。跌破最高档门槛后，旧任务改为 256 分段继续，新任务按当前温区接单。

手动控温时的目标是让温度停留在需要的档位区间、不冲到 100%。想自动恒温可以使用下文的智能冷却模块。

缺少冷却液或输出仓已满时，主动冷却不生效，也不会扣除这部分液体。使用有副产物的冷却液时，记得把输出仓的流体导走。

## 过热与恢复

温度到达 **100%** 时，机器锁定：停止加工、不接新单、关闭预热。已经接收的材料与进度保留。

解锁的唯一方法是用**凛冰溶液**强制冷却：

1. 保持主动冷却开启；
2. 输入仓持续提供凛冰溶液，输出仓保持畅通；
3. 连续冷却 **15 秒**，共消耗 30,000 mB。

完成后温度降到 0% 并解锁；悬停主机界面右侧的信息按钮可以查看恢复进度。之前的高并行任务先按每段 256 份继续，温度回到原档位后自动恢复并行。

恢复必须**连续**完成。中途关闭冷却、断液、输出堵塞、结构被拆或重新加载，都会让计时归零、温度回到 100%。水、钠、其他冷却液、开关超频或单纯等待都无法解锁。

## 智能冷却模块

<ItemLink id="neoecoae:large_integrated_working_station_smart_cooling_module" /> 可以直接放在冷却液输入仓的位置，代替输入仓使用。安装后工作站换成带冷却模块的外观：

<GameScene interactive={true} zoom="3">
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="0" y="0" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="1" y="0" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="2" y="0" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="0" y="1" z="0"></Block>
  <Block id="neoecoae:integrated_working_station" p:formed="true" p:smart_cooling="true" x="1" y="1" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="2" y="1" z="0"></Block>
  <Block id="neoecoae:large_integrated_working_station_output_hatch" p:formed="true" x="0" y="0" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_interface" p:formed="true" x="1" y="0" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_smart_cooling_module" p:formed="true" p:facing="south" x="2" y="0" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="0" y="1" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="1" y="1" z="1"></Block>
  <Block id="neoecoae:large_integrated_working_station_casing" p:formed="true" p:invisible="true" x="2" y="1" z="1"></Block>
  <IsometricCamera yaw="45" pitch="30" />
  <BlockAnnotation x="2" y="0" z="1" color="#66bb6a">
    **智能冷却模块**：替换冷却液输入仓，右键设置目标温度。
  </BlockAnnotation>
</GameScene>

可将结构中的冷却液输入仓原位替换为**大型集成工作站智能冷却模块**。右键模块设置目标温度（0–99%，默认 75%），并在冷却液缓存卡片右上角的标记槽指定冷却液：手持流体容器右键标记，或从 JEI/EMI 拖入；空手左键清除。标记不消耗流体，目标温度与标记均随存档保存。模块只从同一 ME 网络提取标记的种类，不自动选择或切换等级；每 **200 tick（10 秒）**补货一次，按缺口补足 **16,384,000 mB** 的内部容量，避免遍历网络库存。冷却副产物仍进入原输出仓。

面板不带玩家背包；可先在主手持有流体容器，再打开模块并右键标记槽，容器不会被消耗。如果光标上已有容器，则优先读取光标上的容器。

切换或清除标记后，下次补货会先将旧液退回 ME；网络拒收或只能接收一部分时，剩余旧液继续保留，退完后才能拉取新液。未标记时不拉取。旧存档保留真实缓存，但不会把缓存种类自动变成标记，需要玩家重新指定。

模块根据目标温度自动升温、降温与恒温，**待机、暂停加工或等待产物输出时也持续调温**。例如设置为 80% 后，低于目标时自动加热，高于目标时自动冷却；以有限速率逐步达到 80% 后，持续补偿加工发热与自然散热，维持在 80%。目标可随时调整，模块安装后由目标值接管手动预热与冷却开关。过热保护仍需连续通入凛冰溶液恢复：必须手动将标记改为凛冰溶液，模块不会覆盖玩家的选择，恢复后再回到设定温度。

每个成功调温 tick 固定消耗 **100000 AE**，包括恒温与待机，模块加热已包含在此费用内，配方加工能耗另计；同一 tick 不重复收取运行电费。冷却液循环用量至少 **300 mB/t**，即普通仓的 **3 倍**，需要抵消更多加工热量时自动增加耗液，副产物按实际消耗量同比结算。供能、冷却液不足或副产物输出堵塞时暂停主动调温，保留真实温度变化，资源恢复后自动继续。ME 离线时保留并继续使用现有缓存，恢复连接后按补货间隔重试。关闭温度机制时不执行恒温，保持每个实际推进的超频 tick 消耗 300 mB 的冷却液模式。

本模组默认不提供智能冷却模块的合成配方，可通过创造模式或整合包添加的配方获得。

## 关闭温度机制：冷却液模式

服务器配置 `largeWorkstationTemperatureEnabled` 默认 `true`。设为 `false` 并重新进入世界或重启服务器后，工作站改用**冷却液模式**：不需要升温，也不会过热，并行上限直接由冷却液等级决定。

| 冷却液 | 单批并行上限 |
| --- | ---: |
| 无冷却液，或未同时开启超频与主动冷却 | 256 |
| 水 | 16,384 |
| 钠 | 65,536 |
| 凛冰溶液 | 262,144 |

需要**超频**与**主动冷却**同时开启。超频批次每实际推进一个加工 tick 消耗 100 mB 冷却液（智能冷却模块为 300 mB），空闲、缺电和暂停时不消耗。冷却液不足、等级低于批次要求或副产物堵塞时暂停，材料和进度保留。高等级冷却液可以维持低档位的批次。

切换模式不会丢弃已有批次。关闭期间保存的温度与过热状态仍会保留，重新开启温度机制后恢复检查，已过热的机器仍需完成凛冰恢复。

## 加工时间与能源

- **加工时间。** 当前新接收批次统一按 **1 tick** 实际加工，并行数量决定同时处理的份数，每份配方总费用不变。JEI / EMI 仍显示来源配方换算后的时间作为参考，不代表当前工作站的实际耗时；来源时间的 200 tick 上限及各联动的时间基准不再限制新批次的加工速度。翻倍样板同比增加材料与费用，新批次仍按 1 tick 加工。等待输出不再扣加工能量；缺能量或冷却条件时保留进度。更新前已经接收的旧任务保留原来的加工时间、费用与已付进度，不在任务中途重新定价。
- **FE 配方。** 闪电科技等以 FE 计费的路线保留原始 FE 用量。安装 Applied Flux 后，工作站可从 ME 网络提取 FE；网络 FE 不足或未安装 Applied Flux 时，缺额部分由等价 AE 代付，换算按 AE2 当前配置。
- **高级 AE 反应仓**可以用 AE 或 FE 支付，接单时优先选 AE；两种能源都不足时暂停，补充后继续。
- 预热、智能调温仍只用 AE；闪电等额外资源不能用能量代替。

JEI / EMI 配方下方显示耗能、加工时间和额外需要；装有 Applied Flux 时 FE 配方显示 FE，否则显示等价 AE。

## 常见问题

| 现象 | 原因与处理 |
| --- | --- |
| 放好方块但没有成型 | 检查主机是否在上层前排中间，下层后排从背面看是否为“输出仓、通讯接口、输入仓”；或改用自动搭建 |
| 开了超频仍只有 256 并行 | 温度还没到 20%；让配方实际加工，或右键超频按钮预热 |
| 降温后任务只用 256 并行 | 临时分段模式；开启超频并升回原档位门槛后自动恢复 |
| 开着冷却温度仍在上升 | 产热大于冷却；换更强的冷却液，或检查液量与输出仓 |
| 换了凛冰后温度一直降、掉档 | 冷却超过产热；改用较弱的冷却液，或使用智能冷却模块 |
| 兼容配方的样板不可用 | 检查是否装了对应升级模块，以及下单数量是否满足该路线的最小批量 |
| 过热恢复进度不增长或归零 | 确认输入的是凛冰溶液，主动冷却开启，输出仓畅通，期间不要中断或重载 |
