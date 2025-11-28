# 电子吧唧SDK

电子吧唧SDK是一个功能完整的Android SDK，提供蓝牙连接、OTA升级、视频转换、图片转换、表盘管理和文件传输等功能。

## 📚 文档

- [开发文档](docs/DEVELOPMENT.md) - 完整的API参考和使用指南
- [使用示例](EXAMPLE.md) - 快速开始示例代码

## 功能特性

- ✅ **蓝牙连接**: 设备扫描、连接、断开、状态监听
- ✅ **设备管理**: 寻找设备、恢复出厂设置、解绑设备
- ✅ **OTA升级**: 检查升级、启动升级流程、升级状态监听
- ✅ **视频转换**: 视频转AVI、AVI转MP4、AVI转GIF等格式转换
- ✅ **图片转换**: 图片格式转换、缩放、裁剪，支持转换为设备专用格式
- ✅ **表盘管理**: 表盘列表查询、详情获取、表盘升级
- ✅ **文件传输**: 文件上传、下载、传输进度监听

## 📖 快速导航

- [快速开始](#快速开始)
- [功能特性](#功能特性)
- [API参考](docs/DEVELOPMENT.md#api参考)
- [功能模块详解](docs/DEVELOPMENT.md#功能模块详解)
- [配置说明](docs/DEVELOPMENT.md#配置说明)
- [最佳实践](docs/DEVELOPMENT.md#最佳实践)
- [常见问题](docs/DEVELOPMENT.md#常见问题)

## 快速开始

### 1. 添加依赖

在您的项目 `build.gradle` 中添加：

```gradle
dependencies {
    // SDK AAR文件
    implementation files('path/to/baji-sdk-release.aar')
    // 或者通过Maven仓库
    // implementation 'com.baji:sdk:1.0.0'
    
    // SDK必须依赖的第三方库
    // Android 核心库
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.11.0'
    
    // Kotlin协程
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
    
    // 网络请求
    implementation 'com.squareup.okhttp3:okhttp:3.12.9'
    implementation 'com.squareup.retrofit2:retrofit:2.9.0'
    implementation 'com.squareup.retrofit2:converter-gson:2.9.0'
    implementation 'com.squareup.retrofit2:adapter-rxjava2:2.9.0'
    implementation 'io.reactivex.rxjava2:rxandroid:2.1.1'
    implementation 'io.reactivex.rxjava2:rxjava:2.2.18'
    
    // JSON解析
    implementation 'com.google.code.gson:gson:2.10.1'
    
    // 工具库
    api 'com.blankj:utilcodex:1.31.1'
    
    // 事件总线（SDK内部使用EventBus进行事件分发）
    implementation 'org.greenrobot:eventbus:3.3.1'
}
```

**注意**：SDK必须依赖以上第三方库才能正常工作。如果您的项目中已经包含这些库，请确保版本兼容。建议使用与SDK相同的版本以避免兼容性问题。

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

// 连接设备
bluetoothService.connectDevice("AA:BB:CC:DD:EE:FF")

// 断开连接
bluetoothService.disconnectDevice()

// 检查连接状态
val isConnected = bluetoothService.isConnected()

// 获取当前连接的设备
val device = bluetoothService.getConnectedDevice()
```

### 3.1 设备管理功能

```kotlin
val bluetoothService = BajiSDK.getInstance().getBluetoothService()

// 寻找设备（让已连接的设备发出提示，如响铃或震动）
// 注意：需要设备已连接
try {
    bluetoothService.findDevice()
    Toast.makeText(context, "已发送寻找设备指令", Toast.LENGTH_SHORT).show()
} catch (e: IllegalStateException) {
    Toast.makeText(context, "设备未连接", Toast.LENGTH_SHORT).show()
}

// 恢复出厂设置（重置设备到出厂状态）
// 注意：需要设备已连接，此操作不可恢复
try {
    bluetoothService.factoryReset()
    Toast.makeText(context, "已发送恢复出厂设置指令", Toast.LENGTH_SHORT).show()
} catch (e: IllegalStateException) {
    Toast.makeText(context, "设备未连接", Toast.LENGTH_SHORT).show()
}

// 解绑设备（解绑当前设备，清除本地存储的设备信息）
// 支持设备已连接和未连接两种情况
bluetoothService.unbindDevice { success, error ->
    if (success) {
        Log.d("App", "解绑成功")
        // 解绑成功后的处理，如更新UI、清空设备列表等
    } else {
        Log.e("App", "解绑失败: $error")
        // 解绑失败的处理
    }
}
```

### 4. 设备扫描（主项目自行实现）

蓝牙设备扫描功能不在SDK中，需要主项目自行实现。可以参考demo中的实现方式：

```kotlin
// 使用Android系统的BluetoothLeScanner进行扫描
val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
val bluetoothAdapter = bluetoothManager.adapter
val bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

// 开始扫描
val scanSettings = ScanSettings.Builder()
    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
    .build()
val scanFilters = emptyList<ScanFilter>()
bluetoothLeScanner.startScan(scanFilters, scanSettings, scanCallback)

// 扫描结果处理
private val scanCallback = object : ScanCallback() {
    override fun onScanResult(callbackType: Int, result: ScanResult) {
        // 使用SDK的过滤工具检查是否为电子吧唧设备
        val manufacturerData = result.scanRecord?.manufacturerSpecificData
        if (BluetoothFilterUtil.isValidBajiDevice(manufacturerData, result.device.name)) {
            // 转换为DeviceInfo并通过SDK回调通知
            val deviceInfo = DeviceInfo(
                name = result.device.name ?: "Unknown",
                macAddress = result.device.address,
                isConnected = false,
                rssi = result.rssi
            )
            val bluetoothService = BajiSDK.getInstance().getBluetoothService()
            bluetoothService.onDeviceFound(deviceInfo)
        }
    }
}
```

### 5. 使用视频转换服务

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

### 6. 使用图片转换服务

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

### 7. 使用OTA升级服务

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
        // 错误信息可能包含错误码，需要解析并显示对应的错误说明
        // 参考下面的错误码说明
    }
})

// 检查升级
otaService.checkUpgrade()

// 启动升级
otaService.startUpgrade("/path/to/ota/file.bin")
```

#### 表盘升级错误码说明

当表盘升级失败时，错误回调中可能包含错误码。以下是表盘升级相关的错误码及其含义：

##### 本地错误码 (1000-1015)

| 错误码 | 说明 | 可能原因 |
|--------|------|----------|
| 1000 | 正在升级中 | 升级操作正在进行，请勿重复调用 |
| 1001 | 超时 | 升级操作超时 |
| 1002 | 重试超时 | 重试操作超时 |
| 1003 | 校验错误 | 文件校验失败 |
| 1004 | 镜像固件不存在 | 镜像固件文件不存在 |
| 1005 | 字体固件不存在 | 字体固件文件不存在 |
| 1006 | 设备断开连接 | 升级过程中设备断开连接 |
| 1007 | 未知错误 | 未知的错误类型 |
| 1008 | 电量低 | 设备电量过低 |
| 1009 | 充电状态异常 | 设备充电状态异常 |
| 1010 | 空间不足 | 设备存储空间不足 |
| 1011 | 表盘数量超限 | 表盘数量超过限制 |
| 1012 | 重复升级 | 正在升级中 |
| 1013 | 表盘ID未找到 | 指定的表盘ID不存在 |
| 1014 | 升级已停止 | 升级操作被停止 |
| 1015 | 升级过于频繁 | 升级操作过于频繁 |

##### 表盘升级错误处理建议

1. **电量相关错误 (1008)**: 提示用户充电后再试
2. **连接相关错误 (1006)**: 检查设备连接状态，重新连接后重试
3. **文件相关错误 (1004, 1005)**: 检查表盘文件是否存在和完整
4. **校验相关错误 (1003)**: 重新下载表盘文件或检查网络连接
5. **频繁操作错误 (1015)**: 提示用户稍后再试
6. **表盘相关错误 (1011, 1013)**: 检查表盘ID是否正确，表盘数量是否超限

```kotlin
// 表盘升级错误处理示例
override fun onUpgradeFailed(errorCode: Int, error: String) {
    when (errorCode) {
        1008 -> {
            // 电量不足
            Toast.makeText(context, "设备电量不足，请充电后再试", Toast.LENGTH_LONG).show()
        }
        1006 -> {
            // 连接问题
            Toast.makeText(context, "设备连接异常，请重新连接后重试", Toast.LENGTH_LONG).show()
        }
        1003 -> {
            // 校验失败
            Toast.makeText(context, "文件校验失败，请重新下载", Toast.LENGTH_LONG).show()
        }
        1013 -> {
            // 表盘ID未找到
            Toast.makeText(context, "表盘ID不存在，请检查表盘信息", Toast.LENGTH_LONG).show()
        }
        1011 -> {
            // 表盘数量超限
            Toast.makeText(context, "表盘数量已满，请先删除部分表盘", Toast.LENGTH_LONG).show()
        }
        1015 -> {
            // 升级过于频繁
            Toast.makeText(context, "升级操作过于频繁，请稍后再试", Toast.LENGTH_LONG).show()
        }
        else -> {
            // 其他错误
            Toast.makeText(context, "表盘升级失败: $error", Toast.LENGTH_LONG).show()
        }
    }
}
```

### 8. 使用文件传输服务

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

## 📖 更多文档

详细的API参考、使用示例和最佳实践，请查看 [开发文档](docs/DEVELOPMENT.md)。

## 技术支持

如有问题，请联系技术支持团队。

