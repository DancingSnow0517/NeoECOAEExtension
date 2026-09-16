# API 接入指南

> 源码快照：Neo ECO AE Extension `21.2.0-beta4`，2026-09-16。本文描述当前源码，不代表永久兼容承诺。

## 1. 当前接入状态

项目已经在 `cn.dancingsnow.neoecoae.api` 下形成较完整的 Java API，覆盖存储元件、ECO 等级和元件类型、样板存储、合成生命周期和任务状态、Provider 调度、产物路由、进度、规划设置以及客户端模型注册。

当前发布条件：

- 没有独立 API source set 或 `-api` artifact，完整 Mod JAR 同时承载 API。
- 项目启用了 `java-library` 和 `maven-publish`，但当前发布目标只是项目内的 `repo` 目录，没有声明公开 Maven 仓库。
- API 类型直接引用 Minecraft、NeoForge、AE2，少数高级 API 还引用 ECO 实现类型。调用方必须使用匹配版本编译。
- Mod 使用 GPLv3；分发链接后的衍生作品前应评估许可证要求。
- 当前版本为 beta，项目没有声明语义化 API 兼容策略。应锁定精确版本，并在升级时回归测试。

使用本地 JAR 时推荐的 Gradle 配置：

```groovy
repositories {
    flatDir { dirs "libs" }
}

dependencies {
    implementation("org.appliedenergistics:appliedenergistics2:19.2.17")
    compileOnly(name: "neoecoae-21.2.0-beta4")
    localRuntime(name: "neoecoae-21.2.0-beta4") // 仅开发运行环境需要
}
```

依赖名填写实际 JAR 文件名且不带 `.jar`。以后如增加公开 Maven 仓库，应改用发布方给出的正式坐标。再根据代码能否在缺少 ECO 时加载，在 `neoforge.mods.toml` 中声明 required 或 optional 的 `neoecoae` 依赖。

## 2. 稳定性分级

| 级别 | 范围 | 建议 |
| --- | --- | --- |
| 推荐 | `api.storage` 契约；生命周期监听器和附件注册表；调度策略；进度视图；样板存储服务；Provider 契约 | 预期的扩展边界，但 beta 阶段仍需锁定版本。 |
| 有条件使用 | `IECOTier`、自定义注册表、模型注册表、规划/网络设置、产物认领、`ECOFastPathFacade` | 严格遵守所有权和生命周期规则；通常对版本敏感。 |
| 只读桥接 | diagnostics、菜单接口、能力快照、产物路由 | 多由 Mixin 注入 AE2/ECO 对象。通过 `instanceof` 获取；除非契约明确要求，否则不要自行实现。 |
| 内部实现 | `ECOCraftingCPU`、`ECOCraftingCPULogic`、执行/运行时/持久化/worker 类、集成加载器内部类 | 因实现需要而公开，不是稳定的第三方边界。不要构造、继承或持久化这些类型。 |

目前 `@ApiStatus.Internal` 标记了事件触发和附件创建方法，但并非所有面向实现的 public 类都有标记，因此不能只凭 `public` 或包路径判断稳定性。

## 3. 注册与生命周期规则

所有可变注册表都是进程级单例。应在 Mod 构造或 common setup 阶段注册一次；测试环境或可重复装载的宿主还应在结束时注销监听器和策略。除非契约另有说明，游戏回调和 Provider 提交都运行在所属服务端线程。

不要从专用服务端类路径调用客户端模型 API。不要从异步规划线程修改 ECO 任务、Provider 库存或 AE 网络。

### 生命周期观察器

```java
private static final ECOCraftingLifecycleListener LISTENER = new ECOCraftingLifecycleListener() {
    @Override
    public void onPatternDispatched(ECOCraftingDispatchEvent event) {
        long crafts = event.dispatchedCrafts();
        UUID jobId = event.job().craftingJobId();
        // 只观察，不要在回调中修改任务。
    }
};

public static void register() {
    ECOCraftingLifecycle.register(LISTENER);
}
```

使用 `register`/`unregister`。`addListener`/`removeListener` 只是即将移除的二进制兼容桥。监听器异常会被记录并隔离，不会中断任务。

### 每任务持久化附件

```java
ECOCraftingJobAttachmentRegistry.register(
    ResourceLocation.fromNamespaceAndPath("examplemod", "audit"),
    context -> new AuditAttachment(context.craftingJobId())
);
```

每个附件实例只属于一个任务，`id()` 必须与注册 id 相同。`save`/`load` 管理附件自己的 `CompoundTag`；`clear` 会收到终态 `SUCCESS`、`FAILURE` 或 `CANCELLED`。工厂抛异常、返回 null 或返回错误 id 时会被忽略并记录。不要调用内部方法 `create`/`createAll`。

### 调度策略

```java
ECOCraftingDispatchPolicy policy = new ECOCraftingDispatchPolicy() {
    @Override
    public boolean mayTick(ECOCraftingCpuContext cpu) {
        return !maintenanceMode;
    }

    @Override
    public boolean isProviderAvailable(ECOCraftingCpuContext cpu, ICraftingProvider provider) {
        return !provider.isBusy();
    }
};
ECOCraftingDispatchPolicyRegistry.register(policy);
```

所有策略都是否决器；任一策略拒绝或抛异常都会关闭该 tick/Provider。回调内禁止抽取输入、推送 Provider 或修改任务。

## 4. 存储 API

### 元件发现

实现 `IECOCellHandler`，并在 common setup 的排队任务中注册单例：

```java
event.enqueueWork(() -> ECOStorageCells.register(MyCellHandler.INSTANCE));
```

第一个返回非 null inventory 的 handler 胜出。`isCell`、`getCellInventory`、释放和运行时缓存清理必须保持一致。handler 可以按 stack/host 缓存，但必须在 `releaseCellInventory` 释放绑定宿主的状态，并在 `clearRuntimeState` 丢弃临时状态。

handler 返回扩展 AE2 `StorageCell` 的 `IECOStorageCell`。只有当内容可以被无损枚举、清空、持久化和重新插入时，才实现 `IECOStorageMigrationCell`，用于可恢复迁移到无限存储域。

元件物品可实现：

- `IECOStorageCellItem`：等级、元件类型和接受的 `AEKeyType`。
- `IBasicECOCellItem`：标准有限元件契约，包括字节数、每类型字节、类型总数、待机耗电和黑名单判断。

### 等级与元件类型

两个同步自定义注册表：

- `neoecoae:eco_tier`，键 `NERegistries.Keys.ECO_TIER`，值类型 `IECOTier`。
- `neoecoae:cell_type`，键 `NERegistries.Keys.CELL_TYPE`，值类型 `ECOCellType`。

内置等级为 `l4`、`l6`、`l9`，内置元件类型为 `items`、`fluids`。外部 Mod 应在自定义注册表创建后，通过 NeoForge `RegisterEvent` 注册。自定义等级必须提供合成、计算、存储、能量和覆盖纹理全部参数。`supportsComponentTier` 使用等级顺序判断。注册表 id 和值会同步，因此客户端和服务端注册必须完全一致。

`NERegistrate` 及其 builder 是项目内部便利工具，不应作为跨 Mod 契约。

## 5. 样板存储 API

通过 AE2 获取网络级服务：

```java
IECOPatternStorageService service = grid.getService(IECOPatternStorageService.class);
ECOPatternInsertionResult result = service.insertPreparedPattern(prepared);
```

`PatternCatalog` 是网络中样板位置、计数和容量的唯一事实来源。已经解析过样板详情时优先传 `ECOPreparedPattern`。返回值会区分插入成功、已存在、空间不足和不兼容。

在 `IGridNodeService` 上实现 `IECOPatternStorage` 可暴露可写目的地。必须正确处理 `KnownUnique` 方法：只有网络目录已经证明唯一时，目标实现才可以跳过重复扫描。`checksLogicalDomainForDuplicates()` 必须反映真实行为。

外部样板索引的 claim/release 方法是服务端线程协调原语。成功、取消或失败时都必须释放 owner UUID 的全部 claim。拓扑变化后不要继续持有 `PatternContainer` 或槽位引用。

## 6. 合成 Provider API

这里有两个互相独立的契约，不能混用。

### 普通并行调度

能原子接收多个普通处理样板的 Provider 实现 `ECOParallelCraftingProvider`：

- `eco$getAvailableParallelSlots()` 返回当前容量。
- `eco$pushPatternBatch(...)` 收到的是完整 `craftCount` 的输入总量，不是单份输入。
- 只有完整接管全部输入后才能返回 `true`。
- 返回 `false` 时不得改变输入或 Provider 状态。

### 已验证 FastPath 调度

同步执行已验证 ECO/F9 批次的 Provider 实现 `ECOFastPathDispatchProvider`。`eco$prepareFastPath(context)` 只能检查和准备，禁止消费资源；返回带正容量和提交 predicate 的 `Preparation`。

提交 predicate 收到输入、输出和容器返还物总量。只有整个批次被接收时才返回 `true`。返回 `false` 或抛普通异常表示没有任何内容被接收，ECO 会回滚输入和能量。若无法确认是否已接收，必须抛 `ECOIndeterminateBatchException`；ECO 会保留资源所有权并停止降级路径，防止复制。

`ECOBatchCapacityProvider` 已弃用，应直接实现 `ECOFastPathDispatchProvider`。

`ECOFastPathFacade` 是非 ECO CPU 的高级边界。必须在该 CPU 所属服务端线程的同一 tick 内完成 prepare 和 submit。`PreparedBatch` 只能使用一次，拒绝也算使用。成功后调用方负责恰好一次的记账；提交阶段的抽取和回滚由 facade 负责。没有持久化能量预留和对账方案时不要使用此 API。

## 7. 产物、进度与 Mixin 桥接

ECO CPU 通过显式 getter 暴露进度和产物认领：

```java
if (cpu instanceof ECOCraftingCPU ecoCpu) {
    ECOCraftingProgressView view = ecoCpu.getProgressView();
    float progress = view.progress();

    ECOCraftingOutputClaimResult result =
        ecoCpu.getOutputClaimSink().claimCraftingOutput(request);
}
```

从 AE2 `ICraftingCPU` 开始时，这个具体类型检查是目前受支持的发现路径；通用 CPU 不会直接实现 view/sink 接口。不要自行构造或继承 `ECOCraftingCPU`。

产物认领是服务端线程上的原子操作。`expectedKey` 是计划预留的键，`actualKey` 是机器实际产生的替代/动态键。应检查返回状态及各去向数量，禁止直接修改 CPU 的 waiting inventory。

其他桥接包括 `ECOCraftingNetworkSettings`、诊断接口、`ECOCraftingOutputRouter`、`ECOJobOutputReceiver`、菜单扩展和 `ECOCraftingProviderRevision`。通常应对文档指定的 AE2/集成对象做 `instanceof` 获取，其中大部分由 Mixin 注入。由于 Mixin 配置和版本条件可能使接口不存在，调用方必须允许检查失败并正常降级。

## 8. 客户端模型注册

驱动器元件模型使用 `ECOCellModels.register(holder, model)`；计算元件和线缆使用 `ECOComputationModels`。基于 Holder 的注册可以发生在注册对象解析之前。ECO 会在 `FMLClientSetupEvent` 加载客户端集成后消费延迟注册。

传入的 `ResourceLocation` 是模型 id，不是纹理 id。调用必须放在客户端代码中。延迟队列处理后再注册虽会更新映射，但可能错过模型烘焙，因此应在客户端 setup/集成加载阶段注册。

## 9. 可选集成加载器

标记 `@Integration("目标_mod_id")` 的类会从 Mod 扫描数据中发现。类必须有可访问的无参构造器，可选声明 `public void apply()` 和/或 `public void applyClient()`。只有目标 Mod 已加载时才会实例化。

这个机制适合项目内部；外部 Mod 通常应使用自己的生命周期和上述公开注册表。加载器依赖反射/MethodHandle，不提供集成之间的顺序 API，异常可能中止加载。

## 10. 配方与数据 API

已注册配方类型：

- `neoecoae:cooling`：`input`、可选 `output`、`coolant`、可选 `max_overclock`（默认 `0`）。
- `neoecoae:integrated_working_station`：`inputItems`（0-9 个）、可选 `inputFluid`、可选 `itemOutput`、可选 `fluidOutput`、必填 `energy`。

Java builder 可用于数据生成；存在 KubeJS 时，同名 id 会注册 KubeJS schema。配方类是公开数据类型，但 serializer/type 由 `NERecipeTypes` 所有。其他 Mod 应生成 JSON/数据配方，不要重复注册 serializer。

## 11. 网络与兼容注意事项

`cn.dancingsnow.neoecoae.network` 是私有协议实现。协议版本目前是字面值 `"1"`，包含两个 C2S 和一个 S2C payload。不要直接发送这些包，也不要依赖 payload record 布局。

避免依赖 `impl`、`mixins`、`blocks.entity`、`multiblock` 或 `compat` 包。特别注意：

- 不要持久化 ECO 实现类名或内部 NBT key。
- 不要调用事件 `fire*` 或附件 `create*` 方法。
- 未经 `instanceof` 检查，不要假定 AE2 对象实现了 ECO 桥接。
- 除非 API 明确允许，不要跨 tick 保存可变 `ItemStack`、`KeyCounter`、grid、Provider 或方块实体引用。
- 数量必须保持 `long`，不要把存储/合成数量收窄为 `int`。

## 12. 发布前检查清单

1. 使用 Java 21、Minecraft 1.21.1、NeoForge 21.1.x、AE2 19.2.17+ 和精确 ECO JAR 编译。
2. optional 依赖必须分别测试存在和缺少 ECO 的环境。
3. 测试专用服务端，保证客户端模型/UI 类不会被加载。
4. 测试服务端重启、区块卸载/重载、任务取消、Provider 拒绝以及产物存储已满。
5. Provider API 必须明确测试原子拒绝和“是否接收未知”两种情况。
6. 使用桥接所针对的精确可选 Mod 版本进行测试。
