# Android 原型实施

## 移动端原型编码续接（2026-11-18）

- 日常：个人记录与合成排版演示分开切换，复用四类卡片；默认展示个人记录的空状态。
- 记录与隐私：TXT / 约定 JSON 本机解析、预览纠正、取消单条、明确确认、去重、保存和删除。每文件 2 MB、最多 500 条、每条正文 20000 字；本机总记录上限 500 条。数据使用 localStorage，未加密且未按账号隔离，页面已提示使用测试内容。
- 陪伴：四种形象、昵称、待机/思考动画、本次记录参考开关、关键词匹配与引用展开。回复为预设示例，没有外部模型或向量检索。
- 独立问答：新建会话、当前打开期间的历史切换、发送、停止、复制和清空。不会读取个人贴文；不生成伪装成真实答案的内容。
- 自评：0—10 困扰程度演示、保存与清空本次历史；不是正式心理量表或诊断结果。
- 预约授权：移动提交演示时段，专业 Web 预约管理和授权用户页新增联动面板；接受、拒绝、取消、独立选择合成资料范围、期限、撤销。到期后自动停止展示。仅同站点浏览器 localStorage 联动，APK 与电脑尚未通过服务端同步。该面板只展示固定合成资料，绝不共享个人导入内容。
- 关键代码：frontend/src/components/MobileExperience.tsx、MobileRecords.tsx、MobileFeed.tsx；MobilePrototypePage.tsx 组合页面，隐藏页签保留本次状态。
- 自动检查：scripts/check-mobile-records.cjs 覆盖无效输入、条数限制、未知日期、稳定 ID、删除、损坏存储保留。原动态流浏览器脚本已调整为显式进入“排版演示”。本轮未进行浏览器或真机交互验收。
- 尚待正式能力：真实模型/SSE、正式量表接入、用户登录与服务端持久化、真实医生预约和授权、原生分享接收/返回键适配、平台导出包与媒体附件、APK 构建及真机验证。

## 2026-11-18 续接验证

- 本次重新执行 `npm run android:sync`，TypeScript、Vite 移动构建和 Capacitor 同步通过，主 JS 390.91 kB。
- 新增 `frontend/scripts/build-android.ps1`：检查 JDK 21+、SDK platform 36 和 build-tools 36.0.0，再同步资源、执行固定版本 Gradle Wrapper，成功后输出 APK 路径和 SHA256。环境变量仅作用于脚本并在结束时恢复。
- 调用方式：在 frontend 中运行 `powershell -NoProfile -File scripts/build-android.ps1 -JavaHome E:\tools\jdk-24`；添加 `-CheckOnly` 只检查环境。
- 本机找到 JDK 24；默认 Java 17 未改变。当前用户 SDK 有 platform 37.0 和 build-tools 36.0.0，但缺 platform 36，预检已验证能准确阻止构建。
- 实际尝试 assembleDebug：联网放行后 Gradle 8.14.3 下载仍发生连接超时，尚未进入 Java 编译。SDK 36 安装工具的引导下载也仍未确认完成。
- 本次尚未产出 APK，尚未进行安卓模拟器或真机测试。不能将网页构建通过视作安卓验收通过。
- Android 的 `.gradle-local/` 已加入忽略，避免将本机依赖缓存纳入版本控制。

## 2026-11-16 工程接入进展

- 已安装并锁定Capacitor core/android/cli 8.6.2，生成 `frontend/android`。
- `mobile/index.html` 和 `mobile/main.tsx` 是独立移动入口，不引入专业Web路由。
- `vite.mobile.config.ts` 输出 `dist-mobile`；`npm run build:mobile` 已通过，主JS约391KB。
- `capacitor.config.json` 指定应用包名 `com.vis4srd.companion`、显示名“我的日常”和资源目录。包名为项目暂定标识，发布前确认。
- Capacitor add android成功，已把移动资源复制进安卓assets。`npm run android:sync` 用于后续重新构建和同步；`npm run android:open` 打开安卓工程。
- 原生启动文件为 `android/app/src/main/java/com/vis4srd/companion/MainActivity.java`；`AndroidManifest.xml` 声明入口与权限；`variables.gradle` 定义SDK版本；Gradle Wrapper固定构建工具版本。
- 构建前置尚未满足：生成的Capacitor代码要求Java21、compile/target SDK36；本机已核查Java17及SDK37目录，未确认安装Java21和SDK36。尚未生成APK或完成真机测试。
- 当前仍为离线合成内容，无真实用户数据和外部模型调用。独立入口未接入安卓返回键、文件选择及分享接收。
- 官方安装说明：https://capacitorjs.com/docs/getting-started

只开发安卓，不开展iOS适配。复用React与Spring Boot，计划使用Capacitor生成安卓工程，首先实现应用内桌宠，不包括跨应用悬浮桌宠。

## 本轮完成

- `/mobile/` 独立移动端预览入口，四个底部页签：日常、陪伴、AI问答、我的。
- 日常展示合成贴文，其他页签明确标记后续功能，无模型调用、真实资料读取和写入。该纯合成预览公开可访问；接入个人数据前必须增加身份及服务端权限校验。
- 本机存在Android SDK目录和Gradle，当前Java命令指向JDK17；尚未确定Capacitor版本及对应构建要求。

## 后续顺序

1. 核对Capacitor版本的官方环境要求，配置独立移动构建入口，安装依赖并创建Android工程。
2. 检查JDK、SDK、Gradle兼容性，生成调试APK并进行模拟器或真机验证。
3. 完成桌宠、问答、记录与隐私等交互，串起预约及授权演示。
4. 原型确认后接真实接口。模型密钥只放后端，安装包不包含开发密钥或真实个人数据。

当前没有交付APK，浏览器预览和前端构建通过不等于安卓打包或真机验证完成。
