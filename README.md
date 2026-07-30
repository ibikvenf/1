# 宙斯盾安全 AegisAV 🛡

一款**完全离线、本地查杀**的 Android 杀毒软件。
架构思路参考了世界首个开源 Android 杀软 [Divested-Mobile/Hypatia](https://github.com/Divested-Mobile/Hypatia)（GPL，ClamAV 签名驱动），本项目代码为原创实现。

## 📦 安装包下载（CI 自动构建）

仓库附带了构建流水线定义 **`ci/android-build.yml`**，每次推送自动：
**跑单元测试 → 编译 Debug APK → 上传工件**

首次使用需一次性启用流水线（30 秒）：
1. 打开仓库网页 → `ci/android-build.yml`
2. 点击编辑（铅笔），把文件路径改为 **`.github/workflows/android-build.yml`** 并提交
   （工作流文件必须位于该目录，GitHub 网页端操作会自动获得 workflows 权限）
3. 完成！此后每次推送自动构建

下载方式（任选其一）：

1. **Actions 工件**：打开仓库 `Actions` 选项卡 → 选择最新成功构建 → 页面底部 `aegisav-debug-apk` 下载解压即得 `app-debug.apk`
2. **命令行**（已登录 `gh`）：`gh run download -n aegisav-debug-apk`
3. **本地构建**：Android Studio 打开本仓库 → `Build ▸ Build APK(s)`

> Debug 包使用调试证书签名，安装时允许"未知来源"即可；正式上架请自行配置 release 签名。

## 核心特性

| 模块 | 能力 |
| ---- | ---- |
| **病毒查杀** | 快速扫描（应用全量 + 存储**增量**：仅查上次扫描后变化的文件）/ 全盘扫描（全量）|
| **签名引擎** | ClamAV 兼容 `.hdb`(MD5) / `.hsb`(SHA-1/SHA-256)，`哈希:大小:名称`，HashMap O(1) 查询 |
| **黑名单引擎** | 自研 `blacklist.txt`：包名前缀规则 + 签名证书 SHA-256 指纹（打击换壳/家族木马）|
| **单遍哈希** | 文件只读一遍同时算出 MD5/SHA-1/SHA-256；分享内容经 `content://` 流式查杀不落盘 |
| **实时防护** | 递归 `FileObserver` 监控存储写入/移动；新装/更新应用即刻查杀（`PACKAGE_ADDED`） |
| **分享查杀** | 任意 App 可分享文件/文本给宙斯盾即时查杀（`ACTION_SEND` / `ACTION_VIEW`）|
| **启发式审计** | 危险权限组合 + 安装来源评分，画出"木马画像"，列出高/中风险应用 |
| **病毒库更新** | 多来源 HTTPS 下载、`If-Modified-Since` 条件请求（304 跳过）、临时文件原子替换 |
| **隔离区** | 可疑文件移入私有目录加锁，可还原/彻底删除 |
| **处置闭环** | 结果按等级排序；卸载回执校验；历史记录保存威胁快照可回看 |
| **离线隐私** | 全程本地查杀，文件哈希永不离开设备；网络只用于更新病毒库 |

## 技术栈

- Kotlin 1.9 + ViewBinding + Material 3（DayNight）
- 协程 + 前台服务（`dataSync`）+ StateFlow 进度推送
- Gson 持久化（历史/隔离区/签名源），SharedPreferences 设置
- `minSdk 26 / targetSdk 34`，AGP 8.2（需要 **JDK 17**）

## 构建运行

1. 安装 **Android Studio**（Hedgehog+，自带 JDK 17 与 Gradle）。
2. `File ▸ Open` 打开本仓库根目录，等待 Gradle Sync 完成。
   （仓库未提交 `gradle-wrapper.jar`，Android Studio 会自动引导生成；或本地执行 `gradle wrapper`。）
3. 通过 `Run ▶` 安装到真机（Android 8.0+）。
4. 首次扫描前请授予**文件访问权限**（Android 11+ 为"所有文件访问"）。

```bash
# 或使用已有 Gradle 命令行构建
./gradlew assembleDebug

# 运行纯 JVM 单元测试（签名库解析 + 哈希向量，含 EICAR 公开哈希断言）
./gradlew testDebugUnitTest
```

## 验证查杀能力（无需真实病毒）

1. 内置签名已收录 **EICAR 标准测试文件**（无害）  
   从 <https://www.eicar.org/download-anti-malware-testfile/> 下载 `eicar.com` 到"下载"目录 → 快速扫描 → 应报告 `Eicar-Test-Signature`。
2. 另附演示签名：创建文本文件，内容为一行  
   `AegisAV-Test-String-Harmless-Demo-Signature`  
   保存后应被识别为 `Aegis-Test-Signature`。
3. 开启"实时防护"后，**复制** EICAR 文件到存储任意目录，应立即弹出拦截告警。

## 代码结构

```
app/src/main/java/com/aegis/av/
├── AegisApp.kt                 # 应用入口：通知渠道 / Prefs / 签名仓库初始化
├── engine/
│   ├── HashEngine.kt           # 单遍 MD5/SHA-1/SHA-256（文件/流/字节三入口）
│   ├── ScannerEngine.kt        # 扫描编排 + 文件遍历 + 增量过滤 + 取消协作 + 黑名单
│   └── HeuristicAnalyzer.kt    # 权限画像评分 + 签名证书指纹提取（自研规则）
├── data/
│   ├── SignatureDatabase.kt    # ClamAV hsb/hdb 解析 + O(1) 查询
│   ├── BlacklistDb.kt          # 包名前缀 / 证书指纹黑名单
│   ├── SignatureRepository.kt  # 内置库拷贝、合并加载（带缓存）、来源管理
│   ├── QuarantineStore.kt      # 隔离区
│   ├── HistoryStore.kt         # 扫描历史 + 威胁快照（JSON）
│   └── Prefs.kt / ScanModels.kt
├── update/DatabaseUpdater.kt   # 条件请求 + 原子落盘 + 格式校验
├── service/
│   ├── ScanService.kt          # 前台扫描服务（全量/增量）+ 进度通知 + StateFlow
│   └── RealtimeService.kt      # 递归 FileObserver 实时防护（写入即查 + 去抖）
├── receiver/
│   ├── InstallReceiver.kt      # 新装应用即时查杀（含黑名单）
│   └── BootReceiver.kt         # 开机恢复实时防护
└── ui/                         # 主页/扫描/结果/审计/历史/病毒库/隔离区/设置/分享查杀

app/src/test/java/com/aegis/av/ # JVM 单元测试（SignatureDatabase / HashEngine）
```

## 病毒库接入生产数据

签名源完全按 ClamAV 文本格式：

```
<sha256>:<size|*>:<Malware.Name>      # .hsb
<md5>:<size|*>:<Malware.Name>         # .hdb
```

自有黑名单（`blacklist.txt`，与签名库同目录合并生效）：

```
pkg:com.evil.fakebank                 # 包名前缀
cert:<64位小写sha256>                 # 签名证书指纹
```

在"病毒库管理 → 添加来源"填入自托管地址即可；适合接入内网威胁情报平台每日导出的哈希库。

## 签名/发布注意事项

- `MANAGE_EXTERNAL_STORAGE` 与 `QUERY_ALL_PACKAGES` 属 Google Play 受限权限——
  防病毒类应用属**政策允许类别**，上架时需在 Play Console 提交声明。
- debug 构建带 `.debug` 后缀可与正式版共存。

## 路线图（参考 Hypatia Roadmap）

- [x] 分享/打开文件时联动查杀（Intent.ACTION_VIEW / SEND）✅ 已实现
- [x] 包名 / 证书黑名单（超越 ClamAV 哈希库的自研特征）✅ 已实现
- [x] 增量快速扫描 ✅ 已实现
- [ ] ClamAV 全文特征（.ndb）支持
- [ ] Root 模式下扫描 /system 分区
- [ ] 签名库签名校验（.sig）
- [ ] 定时自动扫描（WorkManager）

## 免责与安全提示

- 本应用仅本地查杀，不代替安全使用习惯；
- 仓库根目录的 `root-shushuys2.2.7.sh`（15MB 来历不明脚本）与本项目无关，
  **请勿直接执行**，建议先放入隔离思路人工审计（例如用本应用扫描其哈希）。

## 参考与致谢

- [Hypatia](https://github.com/Divested-Mobile/Hypatia) — 开源 Android 杀软（GPL）：签名格式、单遍哈希、递归 FileObserver 等架构思路借鉴来源（本项目代码为原创编写）。
- [ClamAV 签名文档](https://docs.clamav.net/manual/Signatures.html) — `.hdb/.hsb` 格式规范。
- [EICAR](https://www.eicar.org/) — 标准反病毒测试文件。
- Material Design Icons（Apache 2.0）。
