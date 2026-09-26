# 附属压缩盘接入存储主机标记页面

附属压缩盘的物品类实现
`cn.dancingsnow.neoecoae.api.storage.IECOBulkMarkableCellItem`，
安装到当前存储主机所属的 ECO 存储驱动器后，主机会自动显示标记面板，
并将该盘加入面板上方的元件选择列表。不需要登记物品 ID。

## 接入要求

- 元件本身必须已兼容 ECO 存储驱动器和存储后端。本接口只负责手动标记页面，
  不负责让普通 AE2/MEGA Cells 磁盘成为可安装的 ECO 元件。
- 必须安装并启用 MEGA Cells 集成；页面沿用现有的压缩链校验和重复标记校验。
- 实现 `ConfigInventory getConfigInventory(ItemStack stack)`，返回该盘后端实际使用的
  物品标记库存。库存必须通过 change listener 等机制把修改保存回传入的 ItemStack，
  不能每次返回没有持久化的临时库存。
- 库存大小代表当前可编辑的标记槽数。每页 25 格，面板最多支持 50 格。
  例如 9 槽盘只有前 9 格可编辑；26 槽盘有两页，第二页只有第 1 格可编辑。
  分页按库存大小决定，不强制要求附属盘使用 ECO MEGA 升级卡。
- 若升级改变可编辑槽数，返回升级后的有效库存大小，并由附属自行保留非活动标记，
  或限制会导致标记丢失的升级卡拆卸操作。
- 可选实现 `IUpgradeInventory getUpgrades(ItemStack stack)`，供页面上的
  ECO MEGA 升级卡槽使用。不支持该槽时返回 null。库存自行执行升级兼容及拆卸限制。
  从 ECOStorageCellItem 继承时，父类已有的 getUpgrades 方法会生效。

如果小盘已经继承 ECOStorageCellItem，且已正确覆盖 getConfigInventory，
通常只需在类声明上添加接口：

```java
public class SmallMegaBulkCellItem extends ECOStorageCellItem
        implements IECOBulkMarkableCellItem {
    // 保留已有构造器、存储后端及 getConfigInventory 实现。
}
```

页面写入或清除标记后，主机会调用驱动器的 onCellConfigurationChanged，
并刷新主机存储配置。附属后端应从同一份配置读取标记并更新存储过滤行为。

本接口不将附属盘加入自动标记/自动搬运目标；这些操作继续使用原有大盘后端。
