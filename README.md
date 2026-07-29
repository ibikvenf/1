# 宙斯盾安全 AegisAV 🛡

一款**完全离线、本地查杀**的 Android 杀毒软件。
架构思路参考了世界首个开源 Android 杀软 [Divested-Mobile/Hypatia](https://github.com/Divested-Mobile/Hypatia)（GPL，ClamAV 签名驱动），本项目代码为原创实现。

## 核心特性

| 模块 | 能力 |
| ---- | ---- |
| **病毒查杀** | 快速扫描（高危目录）/ 全盘扫描（全部共享存储）/ 已安装应用 APK 查杀 |
| **签名引擎** | ClamAV 兼容 `.hdb`(MD5) / `.hsb`(SHA-1/SHA-256)，`哈希:大小:名称`，HashMap O(1) 查询 |
| **单遍哈希** | 文件只读一遍同时算出 MD5/SHA-1/SHA-256，极大减少 IO |
| **实时防护** | 递归 `FileObserver` 监控存储写入/移动；新装/更新应用即刻查杀（`PACKAGE_ADDED`） |
| **启发式审计** | 危险权限组合 + 安装来源评分，画出"木马画像"，列出高/中风险应用 |
| **病毒库更新** | 多来源 HTTPS 下载、`If-Modified-Since` 条件请求（304 跳过）、临时文件原子替换 |
| **隔离区** | 可疑文件移入私有目录加锁，可还原/彻底删除 |
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
│   ├── HashEngine.kt           # 单遍 MD5/SHA-1/SHA-256
│   ├── ScannerEngine.kt        # 扫描编排 + 文件遍历 + 取消协作
│   └── HeuristicAnalyzer.kt    # 权限画像评分（自研规则）
├── data/
│   ├── SignatureDatabase.kt    # ClamAV hsb/hdb 解析 + O(1) 查询
│   ├── SignatureRepository.kt  # 内置库拷贝、合并加载、来源管理
│   ├── QuarantineStore.kt      # 隔离区
│   ├── HistoryStore.kt         # 扫描历史（JSON）
│   └── Prefs.kt / ScanModels.kt
├── update/DatabaseUpdater.kt   # 条件请求 + 原子落盘 + 格式校验
├── service/
│   ├── ScanService.kt          # 前台扫描服务 + 进度通知 + StateFlow
│   └── RealtimeService.kt      # 递归 FileObserver 实时防护
├── receiver/
│   ├── InstallReceiver.kt      # 新装应用即时查杀
│   └── BootReceiver.kt         # 开机恢复实时防护
└── ui/                         # 主页/扫描/结果/审计/历史/病毒库/隔离区/设置
```

## 病毒库接入生产数据

签名源完全按 ClamAV 文本格式：

```
<sha256>:<size|*>:<Malware.Name>      # .hsb
<md5>:<size|*>:<Malware.Name>         # .hdb
```

在"病毒库管理 → 添加来源"填入自托管地址即可；适合接入内网威胁情报平台每日导出的哈希库。

## 签名/发布注意事项

- `MANAGE_EXTERNAL_STORAGE` 与 `QUERY_ALL_PACKAGES` 属 Google Play 受限权限——
  防病毒类应用属**政策允许类别**，上架时需在 Play Console 提交声明。
- debug 构建带 `.debug` 后缀可与正式版共存。

## 路线图（参考 Hypatia Roadmap）

- [ ] 分享/打开文件时联动查杀（Intent.ACTION_VIEW / SEND）
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
