# SDGun 社区客户端（Android）

非官方第三方 Android 客户端：以 Material 3 Expressive 界面浏览 SDGun 社区（bbs.sdgun.com.cn）的移动端页面。

## 功能

- **浏览**：版块、帖子、搜索，触屏版完整功能
- **Material You**：Android 12+ 动态取色；品牌红暗色/亮色主题
- **页面与客户端视觉对齐**：卡片化排版、圆角、统一配色（亮暗双模式）
- **阅读舒适度**：网页文字大小调节（100%–140%）
- **链接唤起**：点击论坛链接可直接进入本客户端
- **容错**：页面加载失败时提供错误提示与一键重试
- 页面内的登录、发帖、回帖等操作走论坛官方表单，由论坛自己处理

## 安装

Android 12+。从 [Releases](../../releases) 下载 APK 安装。

签名证书 SHA-256：`614c0c24ae75e9f5417720ae1d8e092d36c172e587904ba031bb4287e642f8fb`

## 声明（Disclaimer）

**本应用只起到外观美化的作用**：它以原生界面将论坛自有的移动端页面重新排版呈现，不提供任何页面内容之外的额外能力。本应用的全部效用，等同于在手机上用浏览器直接访问 bbs.sdgun.com.cn——你能看到的一切内容、能进行的一切操作，与手机浏览器直接访问完全一致；登录、发帖、回帖等操作均通过论坛官方页面进行。

- **不存储任何数据**：不收集、不存储、不上传任何用户数据与论坛内容；无统计 SDK、无崩溃上报。登录状态与页面缓存由系统 WebView 组件按浏览器标准行为在本机管理，开发者无法接触。
- **不提供任何逆向、破解服务**：不逆向论坛程序或协议，不破解任何权限、付费墙或访问控制；本应用对论坛的访问方式与任意手机浏览器无异。
- **不侵犯任何版权**：本仓库不含任何论坛素材或资源，不复制、不分发任何受版权保护的内容；应用内展示的一切内容版权归原权利人所有。
- **不承担任何法律责任**：在适用法律允许的最大范围内，本应用按“现状”（AS IS）提供，不作任何明示或默示担保；因使用本应用产生的一切后果由使用者自行承担，开发者不承担任何责任。使用即表示同意自行遵守论坛规则。
- **非官方**：本应用与 SDGun 论坛及其运营方无任何关联，未获其授权或认可。
- **AI 使用披露**：本项目的全部代码由 AI 编写，不含任何人类编写成分；人类负责提出需求、审阅与验收。

**Disclaimer**: This app is a purely cosmetic layer over the forum's own mobile pages; its effect is identical to visiting bbs.sdgun.com.cn directly in a browser on a phone. It stores no user data, provides no reverse-engineering or cracking services, infringes no copyright, and is provided AS IS without warranty of any kind — the developers assume no legal liability for its use. Unofficial; not affiliated with SDGun.

*SDGun is a trademark of its respective owner; this project is not affiliated with it.*

## 构建

Android Studio 打开本仓库，或命令行 `./gradlew assembleRelease`（JDK 21）。
