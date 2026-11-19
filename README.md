# SDGun 社区客户端（Android）

非官方第三方 Android 客户端：通过原生页面浏览版块、帖子和个人资料，部分页面在 WebView 中打开。

## 功能

- **浏览**：版块、帖子、搜索和阅读历史
- **Material You**：Android 12+ 动态取色；品牌红暗色/亮色主题
- **页面与客户端视觉对齐**：卡片化排版、圆角、统一配色（亮暗双模式）
- **阅读舒适度**：网页文字大小调节（100%–140%）
- **链接唤起**：点击论坛链接可直接进入本客户端
- **容错**：页面加载失败时提供错误提示与一键重试
- 支持登录、发帖与回帖。草稿保存在本机，提交后由论坛处理。

## 安装

Android 12+。从 [GitHub Releases](https://github.com/deserthouse/sdgun-android-client/releases) 下载 APK 安装。

签名证书 SHA-256：`614c0c24ae75e9f5417720ae1d8e092d36c172e587904ba031bb4287e642f8fb`

## 声明（Disclaimer）

本客户端通过论坛服务读取内容，并按登录账号的权限提交帖子、回复与互动。论坛内容和商标归相应权利人所有。

- **本机存储**包括界面偏好、账号会话、发帖和回复草稿，以及帖子阅读历史。WebView 另外管理自己的 Cookie 与缓存。
- **网络与使用范围**　登录、浏览、上传和主动提交的内容会发往论坛。请遵守论坛规则，本项目按现状提供。

- **非官方**：本应用与 SDGun 论坛及其运营方无任何关联，未获其授权或认可。
- **AI 使用披露**：本项目不含任何人类成分。本项目的全部代码由 AI 编写，不含任何人类编写成分。人类负责提出需求、审阅与验收。

**Data and affiliation.** This unofficial Android client reads and submits content through the forum. It stores preferences, session data, drafts and reading history locally. WebView also maintains cookies and cache. Forum actions use the signed-in account’s permissions.

*SDGun is a trademark of its respective owner; this project is not affiliated with it.*

## 构建

开发调试使用 JDK 21，在 Android Studio 打开本仓库，或运行 `./gradlew assembleDebug`（Windows 使用 `gradlew.bat assembleDebug`）。正式构建还需配置发布签名。
