# 观鱼记

观鱼记是一款面向观赏鱼爱好者的 Android 本地记录应用。它用于管理鱼缸与鱼只档案，并把成长、喂食、水质、换水、养护任务和统计汇总保存在设备本地，不依赖后端、云数据库或账号登录。

当前项目以单一 Android 应用模块交付，技术栈为 Kotlin + Jetpack Compose + MVVM + Room。鱼缸、鱼只、各类记录、任务与设置持久化在本地 SQLite，照片复制到应用私有目录，并可通过应用内备份导出或恢复。

## 实机截图

真机运行的界面效果图（总览、我的鱼、鱼只/鱼缸详情、成长档案、水质趋势、统计报表、鱼只时间线，共 9 张）已整理到 [实机截图文档](docs/实机截图.md)。

截图来自 Xiaomi M2011K2C（Android 14，1080 × 2400）运行的 Debug 构建，展示设备上真实存在的本地记录。本 README 不直接展示图片，请通过上方链接查看。

## 功能

### 总览

- 问候语、当日鱼缸/鱼只/喂食摘要，以及「今天要做的」养护任务。
- 快速入口：记录喂食、记录水质。
- 鱼缸卡片展示最近实测水质（水温、盐度、比重、pH 等）与今日喂食次数，未记录的值显示为空而不是 0。
- 「最近动态」汇总喂食、水质、成长等事件，可跳转全部记录。

### 鱼只

- 创建、编辑、查看鱼只，关联鱼缸、状态（在养 / 隔离 / 已转出 / 已故 / 已归档）、照片、鱼种、性别、来源与备注。
- 列表支持按名称/鱼种搜索、按鱼缸与状态筛选、按最近添加/入缸日期/最近测量排序，以及拖拽自定义顺序。
- 鱼只详情为单页主线：头图 → 今日喂食卡 → 鱼缸/水况卡 → 记录坞；一键喂食可撤销。
- 成长档案：体长/体重切换、30/90/全部范围，展示测量次数、首次与变化值，仅使用真实测量点。
- 鱼只时间线：按月分组，可筛选全部/成长/喂食/状态事件。

### 鱼缸

- 支持淡水缸与海水缸、容量/尺寸/启用日期/设备/备注，海水缸可启用盐度管理。
- 鱼缸列表展示缸体卡片、最近水质与「检测/换水」日期，支持搜索与类型筛选。
- 鱼缸详情为单页主线：头图 → 水况卡（最近实测值 + 趋势入口）→ 缸内鱼群 → 养护卡（今日喂食/上次换水/下次任务）→ 缸内动态 → 鱼缸档案；底部固定记录坞提供记录水质、喂食、换水。
- 二级页：缸内鱼群（搜索/筛选/双列卡）、养护日程（周历/完成/延期）、缸内动态（含鱼只成长事件/日期分组）。

### 记录

- **喂食**：快速或完整记录，可按全缸或指定多条鱼关联；多条鱼记录仍只计为一次鱼缸事件；支持历史查看与编辑。
- **水质**：默认带入上次参数与数值，支持模板复用、7/30/90/全部趋势、按参数查看、只读检测详情；高级字段收进底部面板；1.025 这类数值会提示「像比重」并允许改记盐度。
- **换水**：记录换水量与单位、实时换水比例、时间、前后水温/盐度、耗材与备注、下次计划；无容量时不生成比例。
- **成长**：记录体长、体重、时间、照片与备注；未知测量值保持为空，不以 0 代替。
- 表单处理加载、保存中、成功、失败、重复点击与未保存返回确认。

### 养护

- 今日计划、日历、未来计划、历史记录四个视图。
- 一次性或重复任务（喂食、换水、水质检测、盐度检测、设备维护、自定义），支持完成、延期、跳过、取消。
- 基于 AlarmManager + 本地通知的提醒；系统通知权限关闭时，任务仍保留在养护列表中。

### 统计报表

- 入口：我的 → 统计报表。
- 报表期：7 天 / 30 天 / 90 天 / 自定义，可按单个鱼缸筛选。
- 深色「本期概览」展示喂食、换水、水质、任务次数及说明文案。
- 趋势页签：喂食、换水、水质、成长；换水支持次数/水量切换，成长支持鱼只与体长/体重切换。
- 精度按参数区分（如盐度 3 位小数）；本期记录可跳转到对应记录或换水列表。

### 设置、备份与照片

- 展示单位、日期格式、主题（跟随系统/浅色/深色）、本地通知开关。
- 本地备份：导出 zip、校验备份、恢复前创建当前数据安全副本，恢复会替换鱼缸、鱼只、记录、任务、设置与照片。
- 照片通过系统照片选择器或拍摄导入，复制到应用私有目录，数据库保存引用；列表与详情使用缩略图。

## 技术栈

| 领域 | 当前配置 | 用途 |
| --- | --- | --- |
| 语言 | Kotlin 2.2.20、JDK 17 | 应用与测试代码 |
| UI | Jetpack Compose、Material 3、Compose BOM 2025.06.01 | 声明式界面与主题组件 |
| 架构 | MVVM、ViewModel + StateFlow、Repository | 页面状态、业务逻辑与数据访问 |
| 导航 | Navigation Compose 2.9.1 | 页面、二级页与底部主导航 |
| 本地数据库 | Room 2.7.2、SQLite、KSP 2.2.20-2.0.4 | 实体、DAO、事务与 v1→v8 显式迁移 |
| 异步 | Kotlin Coroutines、Flow（Room KTX） | 异步读写与持续数据流 |
| 图片 | Coil Compose 2.7.0、AndroidX ExifInterface 1.4.1 | 图片加载与方向处理 |
| 列表排序 | reorderable 3.1.0 | 网格长按拖拽排序 |
| 构建 | AGP 8.13.2、Gradle Wrapper 8.14.4、compileSdk/targetSdk 35、minSdk 26 | Android 构建 |
| 测试 | JUnit 4、AndroidX Test、Room Testing、Compose UI Test、Espresso | 单元、数据库、仪器与界面测试 |

版本信息以根目录 `build.gradle.kts`、`app/build.gradle.kts` 与 `gradle/wrapper/gradle-wrapper.properties` 为准；项目未使用 `gradle/libs.versions.toml`。

## 项目结构

```text
.
├── app/
│   ├── build.gradle.kts
│   ├── schemas/                            # Room schema 导出
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/guanyji/app/
│       │   │   ├── MainActivity.kt         # 入口、主题偏好与提醒同步
│       │   │   ├── core/
│       │   │   │   ├── designsystem/       # 卡片外壳、深度装饰、详情组件、离开确认
│       │   │   │   └── time/               # 设备日界与日期范围
│       │   │   ├── data/
│       │   │   │   ├── local/              # Room 实体、DAO、迁移、通知调度
│       │   │   │   ├── repository/         # 本地数据访问、排序与测量偏好
│       │   │   │   ├── photo/              # 照片复制、缩略图与引用
│       │   │   │   └── backup/             # 备份导出、校验与恢复
│       │   │   ├── feature/
│       │   │   │   ├── overview/           # 总览
│       │   │   │   ├── fish/               # 鱼只列表、详情、成长档案、时间线
│       │   │   │   ├── tank/               # 鱼缸列表、详情、水质趋势、换水/水质记录
│       │   │   │   ├── feeding/            # 喂食记录与计算
│       │   │   │   ├── care/               # 养护计划与记录
│       │   │   │   ├── statistics/         # 统计报表与计算
│       │   │   │   ├── profile/            # 我的、设置与备份
│       │   │   │   ├── photo/              # 照片选择与加载
│       │   │   │   └── shell/              # 页面外壳
│       │   │   ├── navigation/             # AppDestination、路由与主导航
│       │   │   └── ui/theme/               # 主题、颜色与卡片 token
│       │   └── res/                        # 图标、图片与路径资源
│       ├── test/                           # 本地 JVM 单元测试
│       └── androidTest/                    # 仪器测试（数据库、Repository、备份、导航）
├── docs/
│   ├── 产品设计/                           # 产品设计文档
│   ├── screenshots/                        # 实机截图原图
│   └── 实机截图.md                          # 真机界面截图汇总与说明
├── gradle/wrapper/
├── build.gradle.kts
└── settings.gradle.kts
```

关键入口文件：

- `app/src/main/java/com/guanyji/app/MainActivity.kt`：Activity、主题偏好、通知权限与提醒同步。
- `app/src/main/java/com/guanyji/app/navigation/FishApp.kt`：导航图、底部栏显隐与二级页路由。
- `app/src/main/java/com/guanyji/app/data/local/Entities.kt`：Room 实体与业务枚举。
- `app/src/main/java/com/guanyji/app/data/local/FishDatabase.kt`：DAO、数据库定义与显式迁移。
- `app/src/main/java/com/guanyji/app/data/repository/LocalRepository.kt`：本地数据访问与业务校验。
- `app/src/main/java/com/guanyji/app/data/backup/LocalBackupManager.kt`：备份导出、校验与恢复。

## 数据存储与隐私

- 全部业务数据保存在应用私有的 Room/SQLite 数据库中（当前 schema 版本 8），照片位于应用私有文件目录，数据库只保存路径引用。
- 项目未配置业务后端或账号体系，Manifest 未声明 `INTERNET` 权限，运行时只申请通知权限与开机重排提醒权限。
- 数据库升级使用显式 Migration（v1→v8），未启用 destructive migration，升级不会清空用户记录。
- 卸载应用、清除数据或设备故障会移除本地记录，重要数据应通过「我的 → 数据与偏好 → 存储与备份」导出并自行保存。
- 恢复备份会替换当前鱼缸、鱼只、记录、任务、设置和照片；应用在恢复前会创建当前数据的安全副本。

## 环境要求

- JDK 17。
- Android SDK Platform 35，`minSdk = 26`（Android 8.0）。
- Gradle Wrapper 8.14.4（首次使用需能获取对应发行包）。
- 运行仪器测试需要模拟器或设备；在带真实数据的真机上运行前请先阅读下方「测试与验证」。

## 构建与安装

使用 Android Studio 打开仓库根目录，选择 JDK 17 并等待 Gradle Sync 完成；或在 PowerShell 中执行：

```powershell
.\gradlew.bat assembleDebug
```

Debug APK 输出到 `app\build\outputs\apk\debug\app-debug.apk`。已连接设备并启用 ADB 时，覆盖安装不会清除数据：

```powershell
& "$env:ANDROID_SDK_ROOT\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
```

仓库中没有正式发布版本，也没有 APK 下载地址；`app/build/outputs` 下的文件是本地构建产物。

## 测试与验证

```powershell
# 编译 Debug APK
.\gradlew.bat assembleDebug

# 本地 JVM 单元测试
.\gradlew.bat testDebugUnitTest

# Android Lint
.\gradlew.bat lintDebug

# 编译仪器测试代码
.\gradlew.bat compileDebugAndroidTestKotlin

# 在模拟器或隔离 QA 包运行仪器测试
.\gradlew.bat connectedDebugAndroidTest "-PacceptanceApplicationId=com.guanyji.app.qa"
```

### 最近一次实际执行结果（2026-10-18，Windows / JDK 17）

| 命令 | 结果 |
| --- | --- |
| `.\gradlew.bat assembleDebug` | 通过（BUILD SUCCESSFUL，输出 debug APK） |
| `.\gradlew.bat :app:testDebugUnitTest` | 通过：21 个测试类 / 86 个用例，0 失败、0 错误、0 跳过 |
| `.\gradlew.bat :app:lintDebug` | 通过：0 error、26 warning、3 hint |
| `adb install -r app-debug.apk` | 通过，覆盖升级后既有本地记录保留 |

单元测试覆盖业务计算（喂食、成长、水质、换水比例、统计、日期范围）、卡片视觉 token 与对比度、备份校验、排序与测量偏好、导航路由等。

### 仪器测试限制

`app/src/androidTest` 包含 9 个测试类（Repository、DAO、备份、照片与导航）。本仓库不在装有真实数据的真机上运行 `connectedDebugAndroidTest`：AGP 在任务结束后会卸载应用包，连带删除应用私有数据库与照片。仪器测试应在模拟器上执行，或使用 `-PacceptanceApplicationId=com.guanyji.app.qa` 隔离包名，并先导出应用内备份。本次 README 更新没有运行仪器测试。

### 其他未完成验收

- 通知在强停、重启、厂商省电策略下的端到端投递，仍需要在目标设备上做专项验收。
- 尚未配置 CI；上述结果来自本地开发机执行，不代表其他环境的构建或测试结论。

## 项目文档

- [实机截图文档](docs/实机截图.md)：真机运行的界面效果图与页面说明。
- [第一阶段本地版 App 设计文档](docs/产品设计/第一阶段本地版App设计文档.md)：产品范围、页面结构、交互逻辑、数据模型与验收标准。

早期验收报告、审计报告、位图设计稿与逐页改版 mockup 已从仓库移除，其内容不再反映当前实现；视觉基线以实机截图与设计文档 3.1 规范为准。

## 已知限制

- 统计、趋势和成长图只使用真实记录，数据不足时显示提示而不是补造数据点。
- 归档的鱼只/鱼缸仍可查看历史记录，但界面不提供一键恢复为在养状态的入口。
- 设计文档中仍有部分目标功能（如提醒中心、日历视图的更多筛选维度）未完全交付，README 不将其视为现有功能。

## 贡献

提交问题时请提供复现步骤、设备型号、Android 版本与构建版本；代码改动建议附带对应单元测试。涉及数据库结构时需提供可测试的非破坏性 Migration，并避免在真实数据设备上运行会卸载应用的仪器测试。提交前建议执行 `testDebugUnitTest` 与 `lintDebug`。

## 许可证

仓库当前没有 `LICENSE` 文件，许可证尚未指定；使用、分发或贡献代码前请与维护者确认。
