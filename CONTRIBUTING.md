# 贡献指南

## 先跑起来

### Android 客户端（主交付物）

依赖 JDK 17 与 Android SDK（platforms;android-34、build-tools;36.0.0、platform-tools），不需要 Gradle：

    export ANDROID_SDK_ROOT=$HOME/android-sdk
    cd apps/android
    ./build.sh
    adb install -r build/chelaile-open-0.3.0.apk

模拟器：

    $ANDROID_SDK_ROOT/emulator/emulator -avd chelaile -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect
    adb emu geo fix 113.591965 24.813761

### TypeScript 协议研究栈（可选）

    npm install
    npm test
    npm run test:live      # 需要联网，打真实接口

## 代码风格

Android：

- Java 11，不引入 AndroidX、Kotlin 或第三方运行时库；
- 所有网络请求走 core/Api，所有 JSON 解析走 core/Models；
- UI 用 ui/Ui 的 dp/卡片/按钮工具，不要硬编码像素；
- 普通 View 不要用 matchWrap 加到 LinearLayout，否则在 AT_MOST 下会撑满整卡；
- 注释写“为什么”，不写“是什么”。

TypeScript：

- strict，不加 any；线上字段不确定就标可选；
- packages/protocol 的 dependencies 必须为空；
- 提交前跑 npx tsc --noEmit（web 目录）与 npm test。

## 提交信息

    <type>(<scope>): <subject>

type: feat | fix | docs | refactor | test | chore
scope: android | protocol | server | web | cli | docs

例：fix(android): 附近分组坐标先转 WGS-84 再查站点

## 不接受的内容

- 从原 APK 反编译得到的 smali / Java / 资源文件；
- 账号、支付、广告、打卡相关接口；
- 提高请求频率、去掉缓存、并发遍历城市的“优化”；
- 引入上游商标、图标、字体、插画；
- 任何绕过登录、付费或授权数据的改动。

## 最有价值的贡献

1. **真机兼容性报告**：Android 版本、厂商、截图、崩溃日志；尤其 Android 7–13 与国产 ROM。
2. **真实车辆样本**：白天 07:00–09:00 / 17:00–19:00 的 encryptedNearlines / encryptedLineDetail 原始响应（不要放坐标、userId、设备标识），脱敏后放 tests/fixtures。
3. **城市数据核对**：你的城市在列表里但附近线路为空？开 Issue 附 cityId + 时间 + 截图。
4. **提醒与无障碍**：通知渠道、TalkBack 标签、对比度、字号，改动小但用户感知强。

## Issue 模板要点

- 复现步骤（APK 版本 / 页面 / 输入）
- 期望 vs 实际
- cityId、时间（北京时间）、Android 版本与厂商
- 若涉及解密：只贴脱敏后的字段名，不要贴完整响应（可能含位置）

## 许可证

贡献即同意以 MIT 发布。见 LICENSE。
