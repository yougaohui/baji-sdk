# 电子吧唧SDK v1.0.1

## 🔧 修复和改进

### 主要修复

- ✅ **修复 JitPack 构建问题**：排除 demo 模块，确保 JitPack 能够正确构建 SDK
- ✅ **优化构建配置**：改进 `jitpack.yml` 配置，只构建必要的 SDK 模块

### 使用方式

#### JitPack（推荐，最简单）

```gradle
dependencies {
    implementation 'com.github.yougaohui:baji-sdk:v1.0.1'
}
```

#### GitHub Packages

```gradle
dependencies {
    implementation 'com.baji:sdk:1.0.1'
}
```

## 📝 变更说明

此版本主要修复了 JitPack 构建配置问题，确保 SDK 能够正确发布到 JitPack 仓库。现在开发者可以更方便地使用 SDK，无需配置任何认证信息。

## 🔄 从 v1.0.0 升级

只需更新版本号即可：

```gradle
dependencies {
    // 从 v1.0.0 升级到 v1.0.1
    implementation 'com.github.yougaohui:baji-sdk:v1.0.1'
}
```

---

**完整功能列表和使用文档请查看 [README.md](https://github.com/yougaohui/baji-sdk/blob/master/README.md)**

