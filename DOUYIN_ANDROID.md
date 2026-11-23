# Android 抖音授权部署与验收

网页版继续使用 `DOUYIN_REDIRECT_URI`。Android 用单独的 HTTPS App Link 回调，在系统浏览器中授权并回到 App，不依赖浏览器登录本站。

1. 在抖音开放平台登记 `https://<授权域名>/mobile/douyin/android-callback`，保留原网页版回调；在后端把 `DOUYIN_ANDROID_REDIRECT_URI` 设为这条完整 HTTPS 地址（无端口、查询参数或尾斜杠）。
2. 在该域名的 `/.well-known/assetlinks.json` 直接返回 Digital Asset Links JSON，包含 `delegate_permission/common.handle_all_urls`、包名 `com.vis4srd.companion` 以及实际安装 APK 的签名证书 SHA-256 指纹。确认反向代理不会拦截该路径或把它重定向到首页。建议不记录回调路径的 query string，因为它含短期授权码。
3. 构建 APK 前设置 `DOUYIN_ANDROID_CALLBACK_HOST` 为同一个域名（不含协议和路径），并将移动端 `VITE_API_BASE` 设置为已部署 API 的 HTTPS 基址。用现有 Android 构建脚本重新打包并安装。旧 APK 或未配置回调的环境仍提示使用移动网页版。

真机检查：在 Android 的 App 设置中确认支持的链接已启用，可用 `adb shell pm get-app-links com.vis4srd.companion` 检查域名是否为 `verified`。在 App 内登录个人账号，点击“连接抖音”，授权后应回到“一键导入”并刷新同步状态。分别验证 App 在后台和被系统回收后的返回；再验证拒绝授权、切换账号、未配置 Android 回调以及原网页版授权。若回调留在浏览器，先检查域名验证、签名指纹和系统链接设置。