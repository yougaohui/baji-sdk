# 电子吧唧SDK模块

这是电子吧唧SDK的核心模块，包含所有功能实现。

## 模块结构

```
sdk/
├── src/main/java/com/baji/sdk/
│   ├── BajiSDK.kt              # SDK主入口
│   ├── SDKConfig.kt            # SDK配置类
│   ├── callback/               # 回调接口
│   │   ├── ConnectionCallback.kt
│   │   ├── OTAUpgradeCallback.kt
│   │   ├── VideoConvertCallback.kt
│   │   ├── ImageConvertCallback.kt
│   │   ├── FileTransferCallback.kt
│   │   └── ErrorCallback.kt
│   ├── model/                  # 数据模型
│   │   ├── DeviceInfo.kt
│   │   ├── VideoConvertParams.kt
│   │   ├── ImageConvertParams.kt
│   │   ├── WatchFaceInfo.kt
│   │   └── FileInfo.kt
│   └── service/                # 功能服务
│       ├── BluetoothService.kt
│       ├── OTAService.kt
│       ├── VideoConvertService.kt
│       ├── ImageConvertService.kt
│       ├── WatchFaceService.kt
│       └── FileTransferService.kt
└── build.gradle
```

## 构建

```bash
./gradlew :sdk:assembleRelease
```

生成的AAR文件位于：`sdk/build/outputs/aar/sdk-release.aar`

