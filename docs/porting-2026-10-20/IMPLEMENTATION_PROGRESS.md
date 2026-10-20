# 1.20.1 移植实施进度

本文件记录实际实施状态；原调查文档及证据仍保留其历史含义。工作目录为 `E:\NeoECOAEExtension-1.20.1`，不修改旧版或 1.21.1 工程的业务源码，不自动推送。

## 已验证的最小环境

- Java 17.0.10、Gradle 8.12.1、ModDevGradle Legacy Forge 2.0.148、Minecraft 1.20.1、Forge 47.4.28。
- 基础依赖：AE2 15.4.11、GuideME 20.1.15、LDLib 1.0.52.a；Registrate MC1.20-1.3.11 和 MixinExtras 0.5.3 通过 JarJar 打包。
- `build-local.ps1 build --no-configuration-cache --console=plain`：通过，产出 `build/libs/neoecoae-20.6.0-dev.jar`。主类 class major=61；开发命名 JAR 与 reobf JAR 分开放置。
- `build-local.ps1 runClient -PbootstrapSmoke=true --no-configuration-cache --console=plain`：真实客户端到达标题界面，记录 `NEOECOAE_BOOTSTRAP_CLIENT_TITLE_READY` 后正常退出。
- `build-local.ps1 runServer -PbootstrapSmoke=true --no-configuration-cache --console=plain`：开发专服完成世界初始化并触发 ServerStartedEvent，记录 `NEOECOAE_BOOTSTRAP_SERVER_READY` 后正常保存退出。仅监听本机 127.0.0.1:25595。
- 此时测试任务为 `NO-SOURCE`，没有执行业务单测。普通 Forge 安装的发布物验证、业务交互、双玩家与完整功能验收尚未进行。

机器可读记录与产物哈希见 `porting/environment-validation.json`。原始日志位于本地忽略目录 `validation/` 和 `run/base/*/logs/`。

## 输入与范围

主体源码固定为已批准的 main `dc8344f48ee866e57e02c285fdda5e0fb5da9421`。开始实施时远端 main 已为 `86860f4b8b8bb671807399d6ae3f0a1a785c63a6`，本次不静默混入新提交。结构终端来源单独锁为旧版 `origin/1.20.1` 的 `a6b3d776d677c3093954081d40d4871592864c3b`，仅限终端及必要契约。见 `porting/source-lock.json`。

大集成仅常规、AAE、EAE、AE2LT；保留结构终端，移除机器 UI 自动搭建按钮。Plus 其他联动保留。其余存储、计算、合成系统等级组合继续保留。

## 后续执行顺序

1. 保存可运行的最小环境检查点，然后从锁定 Git archive 导入源码、资源、指南和相关测试，保留目标构建与本目录文档。
2. 按范围裁剪附属入口、Mixin、配方/资源与其他大集成；导入、裁剪过渡提交允许尚不能编译，准确记录缺口。
3. 适配 Java17/Forge/AE2 15 基础接口，针对性接入结构终端，验证所需 UI 兼容层，再逐子系统恢复编译和功能。

当前最小入口仅证明环境，不包含 NeoECOAE 业务功能。后续导入源码后必须重新编译，不能沿用本节的成功记录声称业务已移植。
