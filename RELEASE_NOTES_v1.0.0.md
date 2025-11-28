# 电子吧唧SDK v1.0.0

## 🎉 首次发布

电子吧唧SDK是一个功能完整的Android SDK，为开发者提供与电子吧唧设备交互的完整解决方案。

## ✨ 主要功能

### 核心功能模块

- ✅ **蓝牙连接管理**
  - 设备扫描、连接、断开
  - 连接状态实时监听
  - 设备信息管理

- ✅ **设备管理**
  - 寻找设备（响铃/震动）
  - 恢复出厂设置
  - 设备解绑

- ✅ **OTA升级服务**
  - 自动检查升级
  - 升级流程管理
  - 升级进度和状态监听
  - 支持表盘升级

- ✅ **视频转换服务**
  - 视频转AVI格式
  - AVI转MP4格式
  - AVI转GIF格式
  - 自定义分辨率、帧率、质量参数

- ✅ **图片转换服务**
  - 图片格式转换
  - 图片缩放和裁剪
  - 转换为设备专用BIN格式
  - 支持多种算法优化

- ✅ **表盘管理**
  - 表盘列表查询
  - 表盘详情获取
  - 表盘升级功能

- ✅ **文件传输**
  - 文件上传到设备
  - 文件下载
  - 传输进度实时监听

## 📦 使用方式

### 方式一：JitPack（推荐，最简单）

**无需任何认证配置，直接使用！**

```gradle
// 1. 添加JitPack仓库（在settings.gradle或build.gradle中）
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}

// 2. 添加依赖
dependencies {
    implementation 'com.github.yougaohui:baji-sdk:v1.0.0'
}
```

### 方式二：GitHub Packages

```gradle
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/yougaohui/baji-sdk")
        credentials {
            username = project.findProperty("gpr.user") ?: System.getenv("GITHUB_USERNAME")
            password = project.findProperty("gpr.token") ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    implementation 'com.baji:sdk:1.0.0'
}
```

### 快速开始

```kotlin
// 初始化SDK
val config = SDKConfig.Builder()
    .setApiBaseUrl("https://tomato.gulaike.com")
    .setToken("Bearer your-token")
    .setEnableLog(true)
    .build()

BajiSDK.getInstance().initialize(context, config, broadcastSender)
```

详细使用文档请参考 [README.md](https://github.com/yougaohui/baji-sdk/blob/master/README.md)

## 🔒 安全特性

- ✅ **代码混淆**：Release版本已启用ProGuard代码混淆
- ✅ **源码保护**：仅发布编译后的AAR文件，不包含源码
- ✅ **安全可靠**：使用GitHub Packages进行安全分发

## 📚 文档

- [完整开发文档](docs/DEVELOPMENT.md)
- [使用示例](EXAMPLE.md)
- [API参考](README.md#api参考)

## 🛠️ 技术栈

- Kotlin
- AndroidX
- Coroutines
- Retrofit + OkHttp
- EventBus
- FFmpeg Kit

## 📝 注意事项

1. **初始化顺序**：确保在 `Application.onCreate()` 中初始化SDK
2. **权限要求**：需要蓝牙、位置、网络等权限
3. **依赖库**：请确保添加所有必需的第三方库依赖（详见README）
4. **表盘信息**：上传文件前必须先获取设备的表盘信息

## 🐛 问题反馈

如有问题或建议，请提交 [Issue](https://github.com/yougaohui/baji-sdk/issues)

---

**完整变更日志和API文档请查看项目README**

