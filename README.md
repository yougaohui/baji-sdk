# 电子吧唧SDK

电子吧唧SDK是一个功能完整的Android SDK，提供蓝牙连接、OTA升级、视频转换、图片转换、表盘管理和文件传输等功能。

## 功能特性

- ✅ **蓝牙连接**: 设备扫描、连接、断开、状态监听
- ✅ **OTA升级**: 检查升级、启动升级流程、升级状态监听
- ✅ **视频转换**: 视频转AVI、AVI转MP4、AVI转GIF等格式转换
- ✅ **图片转换**: 图片格式转换、缩放、裁剪，支持转换为设备专用格式
- ✅ **表盘管理**: 表盘列表查询、详情获取、表盘升级
- ✅ **文件传输**: 文件上传、下载、传输进度监听

## 快速开始

### 1. 添加依赖

在您的项目 `build.gradle` 中添加：

```gradle
dependencies {
    implementation files('path/to/baji-sdk-release.aar')
    // 或者通过Maven仓库
    // implementation 'com.baji:sdk:1.0.0'
}
```

### 2. 初始化SDK

在 `Application` 的 `onCreate` 方法中初始化SDK：

```kotlin
import com.baji.sdk.BajiSDK
import com.baji.sdk.SDKConfig
import com.baji.protocol.BroadcastSender
import com.baji.protocol.event.BajiBaseEvent
import org.greenrobot.eventbus.EventBus

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // 创建SDK配置
        val config = SDKConfig.Builder()
            .setApiBaseUrl("https://tomato.gulaike.com")
            .setToken("Bearer your-token")
            .setEnableLog(true)
            .setEnableOTA(true)
            .build()
        
        // 实现BroadcastSender接口
        val broadcastSender = object : BroadcastSender {
            override fun sendBroadcast(event: BajiBaseEvent) {
                EventBus.getDefault().post(event)
            }
        }
        
        // 初始化SDK
        val success = BajiSDK.getInstance().initialize(
            context = this,
            config = config,
            broadcastSender = broadcastSender
        )
        
        if (success) {
            Log.d("App", "SDK初始化成功")
        } else {
            Log.e("App", "SDK初始化失败")
        }
    }
}
```

### 3. 使用蓝牙连接服务

```kotlin
val bluetoothService = BajiSDK.getInstance().getBluetoothService()

// 设置连接回调
bluetoothService.setConnectionCallback(object : ConnectionCallback {
    override fun onConnected(deviceInfo: DeviceInfo) {
        Log.d("App", "设备已连接: ${deviceInfo.name}")
    }
    
    override fun onDisconnected(deviceInfo: DeviceInfo) {
        Log.d("App", "设备已断开: ${deviceInfo.name}")
    }
    
    override fun onConnectionFailed(error: String) {
        Log.e("App", "连接失败: $error")
    }
    
    override fun onDeviceFound(deviceInfo: DeviceInfo) {
        Log.d("App", "发现设备: ${deviceInfo.name}")
    }
})

// 开始扫描
bluetoothService.startScan()

// 连接设备
bluetoothService.connectDevice("AA:BB:CC:DD:EE:FF")

// 断开连接
bluetoothService.disconnectDevice()
```

### 4. 使用视频转换服务

```kotlin
val videoService = BajiSDK.getInstance().getVideoConvertService()

// 设置转换回调
videoService.setConvertCallback(object : VideoConvertCallback {
    override fun onConvertStart() {
        Log.d("App", "开始转换")
    }
    
    override fun onConvertProgress(progress: Int) {
        Log.d("App", "转换进度: $progress%")
    }
    
    override fun onConvertSuccess(outputPath: String) {
        Log.d("App", "转换成功: $outputPath")
    }
    
    override fun onConvertFailed(error: String) {
        Log.e("App", "转换失败: $error")
    }
})

// 转换为AVI
val params = VideoConvertParams(
    targetWidth = 240,
    targetHeight = 240,
    fps = 5,
    quality = 3
)
videoService.convertToAVI(
    inputPath = "/path/to/input.mp4",
    outputPath = "/path/to/output.avi",
    params = params
)

// AVI转MP4
videoService.convertAVIToMP4(
    aviPath = "/path/to/input.avi",
    mp4Path = "/path/to/output.mp4"
)

// AVI转GIF
videoService.convertAVIToGIF(
    aviPath = "/path/to/input.avi",
    gifPath = "/path/to/output.gif"
)
```

### 5. 使用图片转换服务

```kotlin
val imageService = BajiSDK.getInstance().getImageConvertService()

// 设置转换回调
imageService.setConvertCallback(object : ImageConvertCallback {
    override fun onConvertSuccess(outputPath: String) {
        Log.d("App", "转换成功: $outputPath")
    }
    
    override fun onConvertFailed(error: String) {
        Log.e("App", "转换失败: $error")
    }
})

// 转换为bin格式（设备专用格式）
val params = ImageConvertParams(
    targetWidth = 240,
    targetHeight = 240,
    quality = 90,
    outputFormat = ImageConvertParams.ImageFormat.BIN,
    algorithm = 0
)
imageService.convertImage(
    inputPath = "/path/to/input.jpg",
    outputPath = "/path/to/output.bin",
    params = params
)
```

### 6. 使用OTA升级服务

```kotlin
val otaService = BajiSDK.getInstance().getOTAService()

// 设置升级回调
otaService.setUpgradeCallback(object : OTAUpgradeCallback {
    override fun onUpgradeStart() {
        Log.d("App", "开始升级")
    }
    
    override fun onUpgradeProgress(progress: Int) {
        Log.d("App", "升级进度: $progress%")
    }
    
    override fun onUpgradeSuccess() {
        Log.d("App", "升级成功")
    }
    
    override fun onUpgradeFailed(error: String) {
        Log.e("App", "升级失败: $error")
    }
})

// 检查升级
otaService.checkUpgrade()

// 启动升级
otaService.startUpgrade("/path/to/ota/file.bin")
```

### 7. 使用文件传输服务

```kotlin
val fileService = BajiSDK.getInstance().getFileTransferService()

// 设置传输回调
fileService.setTransferCallback(object : FileTransferCallback {
    override fun onTransferStart() {
        Log.d("App", "开始传输")
    }
    
    override fun onTransferProgress(progress: Int, bytesTransferred: Long, totalBytes: Long) {
        Log.d("App", "传输进度: $progress% ($bytesTransferred/$totalBytes)")
    }
    
    override fun onTransferSuccess() {
        Log.d("App", "传输成功")
    }
    
    override fun onTransferFailed(error: String) {
        Log.e("App", "传输失败: $error")
    }
})

// 上传文件
fileService.uploadFile(
    filePath = "/path/to/file.jpg",
    fileType = FileInfo.FileType.IMAGE
)

// 下载文件
fileService.downloadFile(
    fileId = 12345L,
    outputPath = "/path/to/output.jpg"
)
```

## 权限要求

SDK需要以下权限，请在 `AndroidManifest.xml` 中添加：

```xml
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

## 注意事项

1. **初始化顺序**: 确保在 `Application.onCreate()` 中初始化SDK
2. **线程安全**: SDK操作是线程安全的，可以在任何线程调用
3. **资源清理**: 在应用退出时调用 `BajiSDK.getInstance().cleanup()` 清理资源
4. **数据库**: SDK不包含数据库相关功能，数据存储由应用自行管理
5. **EventBus**: SDK内部使用EventBus进行事件分发，请确保项目中已添加EventBus依赖

## 版本历史

- **1.0.0**: 初始版本，包含所有核心功能

## 技术支持

如有问题，请联系技术支持团队。

