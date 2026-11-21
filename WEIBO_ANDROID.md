# 微博安卓导入第一版

实现范围：接收 Android ACTION_SEND / text/plain 的 EXTRA_TEXT，通过本地 Capacitor TextShare 插件传给移动页。冷启动事件保留至网页监听，运行中的分享通过 onNewIntent 送达。收到内容只跳转“我的”并提示预览，不自动保存、不请求网络。没有系统分享入口时可复制粘贴。

来源由用户确认，系统分享本身不能证明发送方是微博。识别 weibo.com、weibo.cn 及其子域名、t.cn 链接；短链不展开、不抓正文。完整保留收到的文字，不伪造时间、媒体或互动数据。确认后按原链接（无链接时按文本）去重，日常详情提供原链接入口。

## 实际文件

- frontend/android/app/src/main/AndroidManifest.xml：注册文字分享目标，关闭自动备份。
- frontend/android/app/src/main/java/com/vis4srd/companion/TextSharePlugin.java：有长度限制的分享读取和桥接事件。
- MainActivity.java：启动前注册插件。
- frontend/src/pages/MobilePrototypePage.tsx：原生事件监听、跳转记录管理。
- frontend/src/components/WeiboImport.tsx：微博候选链接解析、预览纠正、确认、保存。
- MobileRecords.tsx / MobileFeed.tsx：原链接持久化及详情展示。

## 安装后的验证路线

1. 微博自己的贴文中尝试系统分享，选择“我的日常”；分别验证 App 未启动和已启动情况。
2. 确认收到的文字/链接与微博输出一致，未确认时日常列表不增加。
3. 勾选归属和本机保存确认，保存后在日常查看，再分享同一链接确认去重。
4. 仅分享链接时不应出现虚构正文、图片、日期；详情可以主动打开原链接。
5. 取消预览不保存；删除后日常不再展示。若微博无系统分享入口，复制后在微博导入面板粘贴。
6. adb 可模拟接收协议（仅协议检查，不替代微博真机验证）：

```text
adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT https://weibo.com/123/demo -n com.vis4srd.companion/.MainActivity
```

前端类型与构建、解析单元检查、Capacitor 同步已验证。原生 Java 编译、APK、实际微博客户端分享格式尚未验证。现有 SDK/Gradle 下载阻碍仍在，不将源代码实现宣称为已交付 APK。未实现微博账户登录、历史批量获取、平台导出包、图片视频接收、进程被系统终止后的未保存草稿恢复。

Android 机制参考：https://developer.android.com/develop/ui/compose/sharing/receive
