# UFI TOOLS Monitor

基于 [Miuix](https://github.com/compose-miuix-ui/miuix)（Compose UI 库，v0.9.3）构建的随身 WiFi 监控客户端：监控运行 UFI-TOOLS 固件的随身 WiFi 设备，提供仪表盘、短信、流量历史、五款可深度自定义的 Glance 桌面小组件、伪息屏显示（AOD）、阈值警报与前台保活监控。

- 应用名：`UFI-TOOLS-Monitor`
- 包名：`com.xingyue.ufitools.monitor`
- 当前版本：v1.1.0

## 功能总览

### 仪表盘

- **设备头部**：商品名 / 型号 / 固件版本 / 运营商 / 网络制式 / 信号格，大标题随滚动折叠
- **状态网格卡片**：信号（RSRP / SINR / 频段 / QCI / AMBR / 载波聚合状态）、实时上下行速率、今日与本月流量（支持上下行拆分的机型显示拆分）、网络地址（客户端 IP / WAN IPv4 / IPv6 / MAC）、温度（多传感器温度列表）、CPU（总体 + 每核使用率 / 频率）、内存（含 SWAP）、电池（电量 / 电流 / 电压 / 电池温度 / 充电状态）、存储（内置 / TF 卡）、WiFi 连接数
- 点击卡片弹出详情底部弹层，展示更细粒度的数据
- 按设定间隔自动刷新（1–60 秒，默认 10 秒），失败时给出原因明确的错误提示（设备无响应 / 鉴权失败 / 缺少本地网络权限）

### 多连接配置

- 最多 12 条连接档案（名称 + 地址 + 密码 + 最近探测到的机型），一键切换
- 地址清洗：自动去协议头 / 全角冒号 / 零宽字符，端口完全可选（不写端口则走协议默认端口），支持 `http://` / `https://` 前缀
- 访问密码 SHA-256 后存储；签名密钥可自定义（高级项）
- 连接测试：先探活免鉴权的 `/api/need_token`，再拉取设备信息校验密码并回显型号
- 切换档案或机型时自动清理接口探测缓存，避免残留的 goform 模式 / Cookie 串设备

### 短信

- 按号码聚合会话，仿 MIUI 短信风格：大标题折叠、圆角搜索栏本地过滤、未读角标、日期右对齐
- 二级会话页：气泡对话视图（发出 / 接收分侧 + 时间头），进入自动置底，键盘弹起消息跟随
- 收发短信：ZTE `SEND_SMS`（UCS2 十六进制编码）与 UFI-TOOLS `/api/send_sms` 双通道；支持长按复制、多行输入限高
- 会话长按菜单与多选模式：标为已读 / 删除（删除仅 goform 设备支持，通用版 UFI 接口无删除）
- 短信时间解析兼容 ZTE `YY,MM,DD,HH,MM,SS,+TZ` 原始串（`+TZ` 段按 UTC 处理）、epoch 与 ISO 串，显示时转系统本地时区
- 双卡通用设备自动探测实际有卡的 SIM 槽位，会话气泡显示槽位徽标、发送时可选槽位
- 未读状态同步：设备无已读接口时本地按连接地址分桶记录已读 id（上限 2000 条）

### 桌面小组件（Glance，5 款）

| 组件 | 尺寸 | 内容 |
| --- | --- | --- |
| 状态卡 | 4×2 | 顶部状态栏（信号 / 制式 / 型号 / 电量）+ 左右两张数据卡片 + 最多 5 项指标条 |
| 流量条 | 4×1 | 左右两张流量卡片横向对照 |
| 信号卡 | 2×2 | 信号格 + 最多 3 项底部指标 |
| 流量卡 | 2×2 | 上下两张数据卡片 + 最多 2 项底部指标 |
| 状态条 | 4×1 | 四列状态指标，每列内容均可自选 |

- **按组件 DIY**：外观（浅色 / 深色 / 玻璃拟态）、内容缩放（70–150%）、点击行为（打开应用 / 点击刷新）、信号用图标或文字显示
- **卡片槽位**（8 种内容）：今日流量 / 本月流量 / 今日上下行 / 本月上下行 / 电池温度 / CPU 温度 / 放电电流 / WiFi 连接数
- **指标条**：QCI / 速率档 / RSRP / SINR / 频段 / WiFi 数 / 电池温度 / CPU 温度 / 放电电流，可开关、排序（各组件目录不同）
- WorkManager 周期后台刷新（15–120 分钟），应用内采集成功同步更新，点击小组件可触发一次性刷新
- 全部组件共享同一份状态快照（JSON），无实例时不产生后台流量

### 息屏显示（伪 AOD）

- 全屏黑底低亮度常亮（亮度 1–40%，可另设未充电亮度），显示时钟 / 日期与设备关键指标
- **13 个模块可 DIY**：时钟 / 日期 / 设备 / 电池 / 网络 / 信号 / 速率 / WiFi / 温度 / 流量 / 系统 / QoS / 自定义文字；支持排序、列数、字号、时钟字号、间距、对齐、模块标题 / 边框、五种配色（灰白 / 暖白 / 护眼红 / 护眼绿 / 琥珀）
- 数据刷新 30–300 秒，失败指数退避并显示缓存快照，标注连接失败原因
- 防烧屏位移（间隔 / 幅度可调）、双击退出、方向与内容位置、显示更新时间、进入提示
- 省电与安全策略：仅充电时运行、自动退出时长、手机低电量退出、口袋防误触（距离传感器遮挡 2 秒自动退出）

### 通知警报与后台监控

- 阈值警报：温度 / 低电量 / CPU / 内存 / 每日流量（GB）/ 每月流量（GB），同类警报防抖间隔可调（默认 30 分钟）
- 新短信系统通知
- 前台保活服务（`dataSync` 类型）：常驻通知实时显示温度 / CPU / 内存 / 电量 / 今日流量摘要，后台监控间隔 15–300 秒，开启后随应用启动自动拉起
- 三条独立通知渠道（设备警报 / 短信提醒 / 后台服务），兼容 Android 13+ `POST_NOTIFICATIONS` 运行时权限与 Android 12 系统开关判断

### 流量历史

- 每次成功采集自动记录当日用量（取当日最大值，保留天数 7–180 天可调，默认 60 天）
- 历史页展示最近 30 天柱状图与明细，可一键清空
- 更换设备地址时自动清空，避免串数据

### 更新与公告

- 内置更新服务端对接（AES-256-GCM 加密传输）：可选更新弹窗 / 强制更新弹窗（不可关闭）/ 版本停用全屏拦截 / 公告弹窗（每次启动最多一次）
- 服务端开启统计时上报 `app_open`（含版本 / 机型 / 系统版本，失败静默）

### 显示与个性化

- 主题模式（跟随系统 / 浅色 / 深色）、莫奈取色（种子色 + 调色板风格）
- 顶栏 / 底栏模糊（miuix-blur AGSL 着色器，Android 13+；Android 12 自动回退无模糊外观）
- 悬浮底栏（液态玻璃效果，基于 backdrop），可独立开关模糊
- 关于页动态背景着色器（OS2 / OS3 双效果）、赞助页
- 锁屏上直接显示应用界面（无需解锁，可开关）

## 支持的设备

- **中兴随身 WiFi goform 系**（`isZtePortableUfiModel`）：F50 / F50 Pro / U30 Air / U30 Pro（MU5358）/ U20 及硬件代号 MU3356、MU300、MU5352、M3 系列等——与 UFI-TOOLS 前端 `getUFIData` 同源的短 cmd 请求，自动适配
- **通用版设备**（如 E5）：`baseDeviceInfo` + `/api/currentCellInfo` + 通用 goform 多路兜底合成信号，支持双卡槽位探测
- goform 兼容模式自动探测：`is_all` / `direct` / `multi_data` 三种请求路径按返回数据评分择优，按作用域（radio / sms / auth / device / write）缓存探测结果
- 官方后端登录兜底：`LOGIN` / `LOGIN_MULTI_USER` 表单 + `AD` 签名（SHA-256(`wa_inner_version`+`cr_version`) + RD），自动获取并续期 Cookie
- F50 等外接供电机型自动隐藏电池相关 UI 并显示「外接供电」

## 设备通信协议

- 端点：`/api/baseDeviceInfo`、`/api/goform/goform_get_cmd_process`（读）、`/api/goform/goform_set_cmd_process`（写）、`/api/version_info`、`/api/need_token`、`/api/get_cookie`、`/api/get_official_web_password`、`/api/AT`（QoS 查询）、`/api/currentCellInfo`、`/api/get_sms` / `/api/send_sms`（UFI-TOOLS 私有短信接口）
- Kano 签名：`HMAC-MD5("minikano" + METHOD + PATH + timestamp, secretKey)`，16 字节摘要对半分、各半 SHA-256 后拼接再整体 SHA-256
- 签名密钥默认 `minikano_kOyXz0Ciz4V7wR0IeKmJFYFQ20jd`，可按设备自定义
- `device_token` 由免鉴权的 `/api/need_token` 获取，鉴权请求前必须就绪（`X-Device-Token` 头），否则开启 token 校验的设备返回 401
- 默认设备地址 `192.168.0.1:2333`（端口可省略，不自动补默认端口）
- 存储 / 电池 / 信号等字段按机型多来源融合：base → goform → AT 指令 / `currentCellInfo` → `root_shell`（`df` 读取存储），缺失字段自动降级

## Android 17 本地网络权限适配

Android 17（API 37）起，targetSdk ≥ 37 的应用默认禁止访问局域网地址（RFC1918、CGNAT、链路本地等网段），未授权时连接只会静默等到超时。本应用：

- 声明并运行时申请 `ACCESS_LOCAL_NETWORK` 权限（Android 16 及以下无此权限，声明被忽略）
- 检测到设备地址落在受限网段且权限缺失时，弹窗说明并引导授权；永久拒绝后引导到系统设置
- 所有设备请求（仪表盘 / 短信 / 小组件 / 保活服务）在权限缺失时先行短路，避免每次轮询白等十几秒

## 系统要求（手机端）

- Android 12（API 31）及以上；顶栏模糊 / 液态玻璃等 AGSL 效果需 Android 13（API 33）+
- 需要与设备处于同一局域网；Android 17+ 需授予「本地网络」权限

## 构建环境

- JDK 17+
- Android SDK：compileSdk 37.0、build-tools 37.0.0、minSdk 31、targetSdk 37
- Gradle 9.6.1（wrapper 自带）、AGP 9.2.1、Kotlin Compose 编译器插件 2.4.0
- 仅构建 `arm64-v8a` ABI

## 构建

```bash
./gradlew assembleRelease
# APK 输出：app/build/outputs/apk/release/app-release.apk
```

Release 构建启用 R8 混淆与资源收缩，使用仓库内置 `app/release.keystore` 签名，产物可直接安装。

## 依赖

- `top.yukonga.miuix.kmp:miuix-ui / miuix-preference / miuix-icons / miuix-blur / miuix-navigation3-ui:0.9.3`
- `androidx.navigation3:navigation3-runtime:1.1.4`
- `io.github.kyant0:backdrop:1.0.6`（液态玻璃）
- `androidx.glance:glance-appwidget / glance-material3:1.1.1`（桌面小组件）
- `com.squareup.okhttp3:okhttp:4.12.0`
- `androidx.work:work-runtime-ktx:2.10.0`
- `androidx.activity:activity-compose:1.13.0`
- `androidx.core:core-ktx:1.17.0`
- `androidx.profileinstaller:profileinstaller:1.4.1`

## 工程结构

```
app/src/main/kotlin/com/xingyue/ufitools/monitor/
├── MainActivity.kt        # 入口：主题装载、锁屏显示开关
├── AodActivity.kt         # 伪息屏显示页：亮度策略、防烧屏、口袋防误触、超时退出
├── data/
│   ├── DeviceApi.kt       # 设备协议客户端：状态采集（多机型兼容）、短信收发/删除/已读、QoS、连接测试
│   ├── NetClient.kt       # OkHttp 单例 + Kano 签名（HMAC-MD5 → SHA-256）
│   ├── DevicePrefs.kt     # 全部 SharedPreferences 持久化（连接/警报/小组件/AOD/界面）
│   ├── ConnectionProfiles.kt # 多连接档案（≤12 条，切换/迁移/机型同步）
│   ├── DeviceStatus.kt    # 状态模型 + 小组件快照 JSON 序列化
│   ├── StatusRepository.kt # 统一采集入口：刷新 + 快照缓存 + 组件更新 + 流量记录 + 警报
│   ├── TrafficHistory.kt  # 每日流量记录（按天保留最大值）
│   ├── LocalNetworkPermission.kt # Android 17 本地网络权限判定与引导
│   └── UpdateApi.kt       # 更新服务端（AES-256-GCM）：检查更新/停用/公告/统计
├── appwidget/             # UfiWidget 等 5 款 Glance 组件、WidgetKind（类型目录）、WidgetStyle（外观）、WidgetCommon（快照与刷新）
├── notify/AlertNotifier.kt # 阈值警报 + 短信通知（防抖、渠道管理）
├── service/MonitorService.kt # 前台保活监控服务（dataSync）
├── worker/RefreshWorker.kt   # WorkManager 周期/一次性刷新
└── ui/
    ├── App.kt             # Navigation3 导航 + 仪表盘/设置双页 Pager + 模糊顶底栏
    ├── UpdateGate.kt      # 启动更新检查：停用拦截 / 强制更新 / 公告
    ├── pages/             # Dashboard / Setup / ConnectionProfiles / Sms / TrafficHistory /
    │                      # Settings + 子页（Widget/Alert/Service/Display/LockAod）/ About / Sponsor
    ├── component/         # 悬浮底栏、权限弹窗、动效组件
    ├── aod/               # 息屏内容视图
    ├── effect/            # 关于页 OS2/OS3 动态背景着色器
    └── theme/             # Miuix 主题、莫奈取色、种子色
```

## 相关项目

- UI 框架：[Miuix](https://github.com/compose-miuix-ui/miuix)
