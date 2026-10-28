# 专服 UI 回归

此探针用于 #144 的真实 Forge 专服验证，与生产 Mod 分开加载，不进入发布 JAR。

使用 Java 17，在新的隔离目录 `build/ui-server-run/` 中准备已同意的 `eula.txt`，以及只监听本机的 `server.properties`。不要使用生产世界；探针会放置测试方块，并在完成后保存、停止服务器。

```properties
online-mode=false
server-ip=127.0.0.1
server-port=0
level-name=ui-regression
level-type=minecraft\:flat
generate-structures=false
spawn-protection=0
```

在工程根目录使用 PowerShell：

```powershell
.\build-local.ps1 -GradleArguments @('runServer', '--init-script', 'tools/ui-server/verify.init.gradle', '--console=plain')
```

默认使用基础运行环境；可通过 `-PportingProfile=combined` 增加已配置的可选依赖。Gradle 验证应串行运行。

结果写入 `build/ui-server-run/ui-report.json`。检查包含专服共享纹理初始化、各公共方块界面重复打开、初始数据编码、真实大型工作站成型后的状态快照、原生槽位取放，以及同位置同型号方块实体替换后旧菜单的操作拒绝和关闭。Gradle 根据报告判断通过，不能把进程正常退出等同于测试通过。

大型工作站样板供应器使用单独的 AE2 原生菜单路由，探针显式标记为跳过，不计为公共 `block_ui` 路由通过。FakePlayer 会被 LDLib 跳过实际网络发送；这些结果不证明真实客户端收包、显示、切页或 GUI 流体桶操作已验收。
