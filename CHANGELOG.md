# 更新日志

本项目遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

## [0.3.0] - 2026-11-09

四项体验升级：横向时间轴、亮色模式、可调字号、地点搜索与直达推荐。源码构建产物：apps/android/build/chelaile-open-0.3.0.apk；仓库发布副本：release/chelaile-open-0.3.0.apk（82,495 字节，SHA-256 971b2eef256437e565da3fd2144069d6df8c818a8eecbe570fe38a775251f0c4）。

### 新增

- **横向时间轴**：线路页站点改为横向滚动，打开自动聚焦最近站；灰点=已过站、深蓝点=车辆下一站、绿点=你在这、橙点=下车站、浅蓝点=未到站，车辆位置用 🚌 标记。
- **推荐下车站**：从地点搜索进入线路页时，顶部显示「推荐乘车 · 在 X 上车 / 乘 N 站，在 Y 下车」，时间轴把下车站标为橙色并显示「🎯 下车站」。
- **亮色模式**：设置 → 显示与字体 → 切换主题，深色/亮色即时切换；状态栏、导航栏图标与全局调色板同步，偏好持久化。
- **可调字号**：标准 100% / 大 115% / 特大 130% / 超大 150% 四档，全局字号即时生效并持久化。
- **地点搜索与直达推荐**：搜索页新增「地点」tab，支持地点/公交站中文与拼音模糊搜索；按当前位置为每条直达线路匹配最近上车站、下车站，显示上/下车站、步行距离与乘坐站数，点击进入线路页继续看实时到站。

### 修复

- **线路标题多字**：纯数字线路名统一补「路」，已含「路/线」的名称不再重复追加，修复「1路区间路」。
- **坐标 gpsType 健壮性**：lineRoute 根字段与站点均可声明 wgs/gcj/bd；优先使用服务端 wgsLat/wgsLng，其次按 gpsType 转换，避免把 WGS-84 当成 BD-09 二次偏移。
- **地点搜索距离**：公交站结果按自身 gpsType 转 WGS-84 后计算距离；删除推荐流程中未使用的终点附近请求。

### 验证

- Android 14 x86_64 模拟器、1080×2400，0.3.0/build5 端到端验证。
- 北京天安门东 → 四惠：地点搜索返回 3 个地点；推荐 1路区间/1路 两条直达方案，均步行 260 米在天安门东上车、乘 8 站到四惠枢纽站、步行 252 米到达。
- 线路页推荐乘车卡、绿色「你在这」、橙色「下车站」均正确；亮色/深色与 130% 字号实机截图验证。
- 亮色/深色切换与 130% 字号实机截图验证。

[0.3.0]: https://github.com/Lumorian/chelaile-open/releases/tag/v0.3.0

## [0.2.2] - 2026-11-08

多城市动态、自动定位与首次启动权限修复。源码构建产物：apps/android/build/chelaile-open-0.2.2.apk；仓库发布副本：release/chelaile-open-0.2.2.apk（70,207 字节，SHA-256 9a18ce6a2d72b83914dc36538dfb4efcb7c74574c1d7a9691cccb998be2ca041）。

### 新增

- **自动定位**：首次启动请求定位权限，GPS/网络双 provider、12 秒超时、30 秒缓存新鲜度判断；成功后 GPS 反查城市名、匹配 cityId 并自动切换附近页。
- **非韶关动态**：发现 App 参数（s=android、v=7.6.8、vc=436、src=android）可请求明文实时接口；附近页改用 stop!nearlines.action，线路详情用 line!timeTable.action 补全 buses。北京 1 路（33 站、9 辆）、上海 71 路（24 站、实时 ETA）实测通过。
- **多城市详情合并**：H5 encryptedLineDetail 提供完整静态站点，App timeTable 提供动态车辆；Api.lineDetail 在 buses 为空时自动合并，UI 不区分城市。
- **发车时刻表自动换行**：新增 FlowLayout，班次 chip 按可用宽度换行，修复长班次列表最后一个 chip 被挤成竖排的问题。

### 修复

- **首次启动权限竞争**：MainActivity 不再在 onCreate 请求通知权限；通知权限改为首次设置到站提醒时按需请求，避免把定位权限弹窗挤掉。
- **页面级权限回调**：MainActivity 把 onRequestPermissionsResult 转发给当前页面，LineScreen 处理通知权限并恢复待启动的提醒。
- **“你在这”不一致**：不再直接使用服务端 nearStnOrder，改为用当前 GPS 对所有站点计算几何最近站（优先 wgsLat/wgsLng，否则 BD-09→GCJ-02→WGS-84）；韶关 1/2 路、北京 1/120 路实测落到同一站。
- **“你在这”距离**：chip 显示“你在这 · N 米”，距离由 haversine 计算。
- **提醒文案**：常驻通知不再出现“前 0 站提醒”，到站提醒改为“车辆到站时提醒你 · 「X」”，Toast 改为“已开启到站提醒：X”。
- **旧定位缓存**：定位只采信 30 秒内的 lastKnown，超过则等待新鲜定位，避免刚换城市仍显示旧城市。

### 协议

- 确认 H5 参数（s=h5、v=9.1.2）与 App 参数（s=android、v=7.6.8）的差异；H5 encryptedLineDetail 在非韶关返回空 buses，App 参数请求加密端点返回另一种密文，当前未解。
- 城市列表、cityLineList、lineRoute、nearlines、nearPhysicalStns、timeTable 等端点改用 App 参数后可返回明文动态数据。

### 验证

- Android 14 x86_64 模拟器，Android 14、1080×2400：韶关、北京、上海三地自动定位与附近动态。
- 韶关东堤中路 139m；北京天安门东 200m；上海西藏中路 89m。
- 韶关 1/2 路“东堤中路 · 144 米”；北京 1/120 路“天安门东 · 260/201 米”。
- 上海 71 路时间轴车辆 marker、你在这、发车时刻表均正常；长班次列表已换行。

[0.2.2]: https://github.com/example/chelaile-open/releases/tag/v0.2.2

## [0.2.1] - 2026-11-08

Android 客户端功能补全与实机验收。发布产物：apps/android/build/chelaile-open-0.2.1.apk（66,111 字节，SHA-256 483c726d7d97f56e3d11e5635df03cd110b93a93bc5f45016777c7cd548983ae）。

### 新增

- **Android 客户端**：纯 Java + Android SDK，无 Gradle、无 AndroidX、无第三方运行时依赖；aapt2 + javac + d8 + zipalign + apksigner 一键构建。
- **附近线路**：定位/手选城市 → 附近物理站分组 → 线路、距离、到站预测，30 秒刷新。
- **线路搜索**：整城线路表缓存 24 小时，本地精确/模糊过滤，离线可搜。
- **线路详情**：首末班、票价、站数、车辆位置、完整站序时间轴、运营信息。
- **发车时刻表**：line!depTimeTable.action 分段展示，forecastDesc 转「下一班 HH:MM 发车」。
- **换向**：起终点、站序、targetOrder 同步刷新。
- **收藏**：本机保存，关注页集中查看，长按取消。
- **上下车提醒**：点时间轴任一站点设置「车辆到站时提醒我（上车）/提前 1–3 站/我要在这站下车（提前 1 站）」；前台服务 20 秒轮询，高优先级通知带「停止提醒」。
- **历史到站**：本机 SQLite 记录车辆跨站时刻，按 30 分钟桶展示平均值与样本数，明确标注「本机记录/仅本机」。
- **城市选择**：499 城、拼音/中文搜索、热门城市、GPS 反查、刷新间隔 10/15/30/60 秒。

### 修复

- **时间轴语义**：buses[].order 是车辆正在驶向的下一站序号；已过站灰点、车辆下一站蓝点、用户站绿点、未到淡蓝；车辆 marker 插在下一站行之前，文案「🚌 车辆在此 · 距下一站 N 米」。
- **附近分组 → 站点详情**：分组坐标是 BD-09，先转 GCJ-02 再转 WGS-84 后查 nearPhysicalStns，避免落到另一片区域。
- **站点页空白大卡**：普通 View 使用 matchWrap 布局在 AT_MOST 下撑满整卡，改为 space 固定高度。
- **ETA 文案**：按 targetOrder - bus.order 判断「还有 N 站/即将到站/已过站/等待下一班」，过滤无意义的 predictionText「发车\n时间」。
- **城市列表解析**：/cdatasource/citylist 直接返回 data，没有 jsonr 包裹；客户端增加直接 data 回退。
- **线路详情偶发失败**：首次失败自动 900ms 重试一次，避免上游单次异常直接显示「加载失败」。
- **通知图标**：新增白色公交 vector ic_stat_bus，替换系统默认信息图标。
- **底部 Tab 根页返回箭头**：页面栈只有一层时不再显示无意义返回箭头。

### 构建

- build.sh 自动选择 SDK 中最高版本 build-tools；build-tools 34.0.0 的 d8 8.2.2 对匿名内部类存在 NullPointerException，改用 36.0.0。
- debug keystore 移出 build/ 目录，避免每次构建重建密钥导致覆盖安装签名不匹配。

### 文档

- 新增 docs/06-Android客户端.md：架构、构建、协议、时间轴/时刻表/历史/提醒/坐标语义、权限、验证清单、已知限制与合规。
- README 改为 Android 优先；docs/05 更新为 Android 0.2.1 状态与路线图。
- docs/screenshots 增加 9 张 Android 实机截图。

[0.2.1]: https://github.com/example/chelaile-open/releases/tag/v0.2.1

## [0.2.0] - 2026-11-08

首个 Android 客户端内部版本，完成附近、搜索、线路、站点、关注、设置六个页面与基础协议客户端；在 Android 14 x86_64 模拟器上跑通真实韶关数据。

### 新增

- Android 工程 apps/android：AndroidManifest、深色主题、启动图标、构建脚本。
- core：Crypto（MD5/AES-256-ECB）、Api（H5 网关）、Models、Geo（WGS/GCJ/BD-09）、Format。
- data：HistoryStore（SQLite 历史到站）。
- service：ReminderService（前台服务 + 通知）。
- ui：NearbyScreen、SearchScreen、LineScreen、StationScreen、FavoritesScreen、SettingsScreen、Ui、Screen。

[0.2.0]: https://github.com/example/chelaile-open/releases/tag/v0.2.0

## [0.1.1] - 2026-11-08

界面打磨，依据真实数据截图逐项修正。

### 修复

- **附近/搜索列表行重建**：站名独占一行，价格与运营时间下移，到站文案右对齐。
  旧版把到站文案挤在同一行的右侧，长站名（如「碧桂园（凤凰城） → 碧桂园（太阳城）」）
  被压成竖排单字。
- **站点详情同名站分组**：上游 stationList 里的同名物理站只有线路号、没有实时数据，
  旧版渲染成一排「→ 暂无车辆信息」空行；现在拆成独立的「同名站」卡片，并提供跳转。
- **tabbar / 顶栏 / 搜索框图标**：emoji 全部换成内联 SVG，跨平台渲染一致。
- **搜索热词**：原先「快」「夜」会被拼成「快 路」，改为完整的常见线路名。
- 详情页不再错误高亮底部「附近」标签。

### 文档

- README 增加界面预览（4 张真实接口截图，存于 docs/screenshots/）。

[0.1.1]: https://github.com/example/chelaile-open/releases/tag/v0.1.1

## [0.1.0] - 2026-11-08

首个可运行版本（TypeScript 协议研究栈）。

### 新增

- **protocol**：零依赖协议层
  - AES-256-ECB/PKCS#7 纯 TS 实现（附 NIST 向量与 node:crypto 对拍）
  - MD5 / cryptoSign 签名
  - \*\*YGKJ…YGKJ## 网关剥壳
  - WGS-84 / GCJ-02 / BD-09 坐标互转，stationToWgs 三级降级
  - ChelaileClient：城市、线路、站序、附近、站点详情、实时详情
  - models：到站文案、距离、运营时间窗口
  - fixtures：离线演示数据
- **server**：node:http BFF
  - 9 个 /api/* 路由，TTL 缓存 + 并发请求合并 + LRU 上限
  - 静态托管 apps/web/dist（SPA 回退、assets 长缓存）
  - CORS、结构化错误、优雅退出
- **web**：React 19 + Vite 8
  - 附近 / 搜索 / 关注 / 设置 / 线路 / 站点 六个视图
  - URL 即状态（hash 路由），localStorage 持久化
  - 15–20 秒轮询、深色模式、骨架屏、演示模式
- **cli**：cities / near / search / line / station 五个子命令
- **docs**：逆向分析报告、协议参考、架构与实现、合规与免责、路线图

### 已知问题

- 夜间（0:00–5:00）上游无实时车辆，UI 以「首班未发」兜底
- 服务端搜索接口参数未逆清，搜索为客户端过滤
- 不支持地铁换乘、账号、支付、推送

[0.1.0]: https://github.com/example/chelaile-open/releases/tag/v0.1.0
