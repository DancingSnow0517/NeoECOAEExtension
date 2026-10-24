# ECO CPU 详情页空白修复

从计算主机侧边 CPU 列表再次点击已选处理器时，会切换到 ECO 独立详情页。此前切换已经发生，但主页面和详情页都不可见。

`screens/computation/controller.json` 将 `computation_status` 的初始布局设为 `display: none`。点击回调隐藏 `computation_main`，并调用详情元素的 `setDisplay(true)`。Forge 移植层的这个方法只更新布尔状态和 LDLib1 可见性，没有更新布局的 `display`；后续布局仍然判定详情页隐藏，并将其尺寸设为零。因此背景、CPU 列表、任务物料和返回按钮都不会显示。

修复位于 `platform/ui/runtime/gui/ui/UIElement.java`：`setDisplay(boolean)` 同步设置布局的 `FLEX` / `NONE`，与原 LDLib2 的同名 API 行为一致。原布局、列表选择逻辑和 CPU 任务执行没有改动。

新增回归用例使用实际 JSON 加载器及实际 CPU 列表事件处理器，验证：

- 初始隐藏的 JSON 面板能够重新显示，恢复尺寸和鼠标命中，之后可以再次隐藏和显示。
- 点击侧边 CPU 列表中已选的条目后，主页面隐藏，详情页可见且尺寸为 326 × 254，返回按钮可交互。
- 返回后恢复展开的主页面及 CPU 列表，尺寸为 362 × 231；重复进入和返回仍然正确。

计算主机测试的独立初始化同时补齐 Registrate 的启动器边界模拟，使这些测试可以单独执行。

修复前，同一组 10 项测试中的上述两个回归用例均在“详情页应可见”的断言失败；修复后全部通过。完整验证在基于提交 `7e4e4b9` 的独立检出目录执行，仅加入本次三个 Java 文件的修改，未混入工作区的结构终端改造：

```powershell
.\build-local.ps1 build --offline --console=plain
python tools/porting/audit_package.py --jar build/libs/neoecoae-20.6.0-dev.jar --output build/cpu-status-package-audit.json
```

完整构建成功；253 个测试类共 1,530 项，0 失败、0 错误、12 跳过。JAR 静态审计成功：976 个源码对应类齐全，1,605 个类均为 Java 17 字节码，64 个注册 Mixin 齐全。另以 `javap` 确认发行包中的 `UIElement.setDisplay(boolean)` 已包含布局显示状态的更新。

修复包位于 `build/release/cpu-status-display/neoecoae-20.6.0-dev.jar`，7,039,604 字节；SHA-256 为 `4f4a320dd2458cb3f2785415941e4fdcae2861de39444ee8e4f22ca419ed2d36`。机器可读记录见 `porting/cpu-status-display-validation.json`。

本轮没有替换运行中实例的 JAR，也没有重启客户端。现场只读诊断时游戏停在暂停菜单；MCP 桥接端口未监听，因此未发出 mcpfabric 请求。尚未完成安装新包后的真实 CPU 列表点击及画面验收，编译、测试和 JAR 审计不代替此项验收。
