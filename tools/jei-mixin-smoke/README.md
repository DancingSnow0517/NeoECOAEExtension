# JEI 配方高度 Mixin 验证

在 Java 17 下对所选 JEI 正式 JAR 应用生产代码中的两个高度 Mixin，再以 `-Xverify:all` 加载转换后的类。此检查可以捕获构造器参数槽被复用后产生的 `VerifyError`，不需要启动游戏。

使用项目默认 JEI：

```powershell
.\build-local.ps1 -GradleArguments @('-I', 'tools/jei-mixin-smoke/verify.init.gradle', 'verifyJeiLayoutMixins', '--console=plain')
```

使用崩溃报告中的 JEI 15.74.0.232：

```powershell
.\build-local.ps1 -GradleArguments @('-I', 'tools/jei-mixin-smoke/verify.init.gradle', 'verifyJeiLayoutMixins', '-PjeiVersion=15.74.0.232', '--console=plain')
```

成功时必须输出两条 `JEI_MIXIN_VERIFIED`，转换后的类位于 `build/jei-mixin-smoke/<JEI 版本>/`。该检查覆盖真实 Mixin 应用和 JVM 类验证；配方高度行为另由 `WorkstationRecipeHeightTest` 验证，游戏内操作仍需单独验证。
