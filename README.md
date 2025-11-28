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

#### 方式一：通过JitPack（推荐，最简单）

**无需任何认证配置，直接使用！**

在项目根目录的 `settings.gradle` 或 `build.gradle` 中添加JitPack仓库：

```gradle
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }  // 添加这一行
    }
}
```

或者如果使用传统的 `build.gradle`：

```gradle
allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }  // 添加这一行
    }
}
```

在您的项目 `build.gradle` 中添加依赖：

```gradle
dependencies {
    // 通过JitPack引入SDK（推荐，无需认证）
    implementation 'com.github.yougaohui:baji-sdk:v1.0.5'
    
    // 【重要】必须添加以下本地依赖（SDK依赖的底层库）
    // 请将SDK demo/libs目录下的所有aar/jar文件复制到您的libs目录
    implementation fileTree(dir: 'libs', include: ['*.aar', '*.jar'])
}
```

**⚠️ 重要提示：依赖底层库**

由于SDK依赖多个本地AAR文件（如蓝牙协议、OTA库等），这些文件无法通过Maven传递。**您必须手动集成这些库**：

1. 下载本仓库源码或Demo
2. 复制 `demo/libs` (或 `sdk/libs`) 目录下的所有 `.aar` 和 `.jar` 文件
3. 粘贴到您项目的 `libs` 目录
4. 确保 `build.gradle` 中有 `implementation fileTree(dir: 'libs', include: ['*.aar', '*.jar'])`

**版本说明**：
- 使用Release标签：`v1.0.5`
- 使用分支：`-SNAPSHOT`（如 `master-SNAPSHOT`）
- 使用提交哈希：`abc1234`（前7位）

#### 方式二：通过GitHub Packages

在项目根目录的 `build.gradle` 或 `settings.gradle` 中添加GitHub Packages仓库：

```gradle
repositories {
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/yougaohui/baji-sdk")
        credentials {
            username = project.findProperty("gpr.user") ?: System.getenv("GITHUB_USERNAME")
            password = project.findProperty("gpr.token") ?: System.getenv("GITHUB_TOKEN")
        }
    }
}
```

**配置GitHub认证**：

1. 创建GitHub Personal Access Token：
   - 前往 GitHub Settings > Developer settings > Personal access tokens > Tokens (classic)
   - 生成新token，勾选 `write:packages` 和 `read:packages` 权限

2. 配置认证（推荐使用环境变量）：
   ```bash
   # Windows (PowerShell)
   $env:GITHUB_USERNAME="yougaohui"
   $env:GITHUB_TOKEN="your_github_token"
   
   # Linux/Mac
   export GITHUB_USERNAME=yougaohui
   export GITHUB_TOKEN=your_github_token
   ```

在您的项目 `build.gradle` 中添加依赖：

```gradle
dependencies {
    // 通过GitHub Packages引入SDK
    implementation 'com.baji:sdk:1.0.0'
}
```

#### 方式三：使用本地AAR文件

如果您不想使用Maven仓库，也可以直接使用AAR文件：

```gradle
dependencies {
    // SDK AAR文件
    implementation files('path/to/baji-sdk-release.aar')
    
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
    
    // SDK依赖的AAR文件（需要主项目提供）
    // 将SDK libs目录下的AAR文件复制到主项目的libs目录，然后添加依赖
    implementation fileTree(dir: 'libs', include: ['*.aar', '*.jar'])
    // 或者单独指定每个AAR文件
    // implementation files('libs/baji-protocol-releaseSuperband.aar')
    // implementation files('libs/ota-module-releaseSuperband.aar')
    // implementation files('libs/network-module-releaseSuperband.aar')
    // implementation files('libs/commonlib-releaseSuperband.aar')
    // implementation files('libs/jl_bluetooth_connect_V1.3.5_10312-release.aar')
    // implementation files('libs/jl_bt_ota_V1.10.0_10932-release.aar')
    // implementation files('libs/jl_rcsp_V0.7.2_527-release.aar')
    // implementation files('libs/JL_Watch_V1.13.1_11214-release.aar')
    // implementation files('libs/mywatch_V1.0.3_debug_20251105.aar')
    // ... 其他AAR文件
}
```

**重要说明**：

1. **第三方库依赖**：SDK必须依赖以上第三方库才能正常工作。如果您的项目中已经包含这些库，请确保版本兼容。建议使用与SDK相同的版本以避免兼容性问题。

2. **AAR文件依赖**：SDK内部依赖的AAR文件（位于SDK的libs目录）不会被打包到SDK的AAR中，需要主项目自行提供这些AAR文件作为依赖。请将SDK libs目录下的所有AAR文件复制到主项目的libs目录，并在build.gradle中添加依赖。

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

## 重要流程说明

### 表盘信息获取流程

**重要**：上传图片/视频前，必须先获取设备的表盘信息。表盘信息包含设备屏幕尺寸、屏幕类型等关键参数，这些信息是文件转换和传输所必需的。

#### 1. 自动获取表盘信息

SDK会在设备连接成功后自动请求表盘信息：

```kotlin
// 设备连接成功后，SDK会自动请求表盘信息
bluetoothService.setConnectionCallback(object : ConnectionCallback {
    override fun onConnected(deviceInfo: DeviceInfo) {
        // 设备已连接，SDK会自动请求表盘信息
        // 通常需要等待3-5秒后表盘信息才会获取完成
        Log.d("App", "设备已连接，等待表盘信息...")
    }
})
```

#### 2. 检查表盘信息是否已获取

在上传文件前，需要检查表盘信息是否已获取：

```kotlin
val clockDialInfoService = BajiSDK.getInstance().getClockDialInfoService()

// 检查表盘信息是否存在
if (clockDialInfoService.hasClockDialInfo()) {
    // 表盘信息已获取，可以上传文件
    val clockDialInfo = clockDialInfoService.getCurrentClockDialInfo()
    Log.d("App", "表盘信息: ${clockDialInfo?.width}x${clockDialInfo?.height}")
    
    // 开始上传文件
    fileService.uploadFile(filePath, FileInfo.FileType.IMAGE)
} else {
    // 表盘信息未获取，需要等待或手动请求
    Log.w("App", "表盘信息未获取，请等待或手动请求")
}
```

#### 3. 手动请求表盘信息

如果自动获取失败，可以手动请求：

```kotlin
val clockDialInfoService = BajiSDK.getInstance().getClockDialInfoService()

// 手动请求表盘信息
clockDialInfoService.requestClockDialInfo()

// 等待一段时间后检查（建议等待3-5秒）
Handler(Looper.getMainLooper()).postDelayed({
    if (clockDialInfoService.hasClockDialInfo()) {
        Log.d("App", "表盘信息获取成功")
        // 可以开始上传文件
    } else {
        Log.e("App", "表盘信息获取失败，请检查设备连接状态")
    }
}, 5000)
```

#### 4. 完整的文件上传流程

```kotlin
// 1. 检查设备连接状态
val bluetoothService = BajiSDK.getInstance().getBluetoothService()
if (!bluetoothService.isConnected()) {
    Toast.makeText(context, "请先连接设备", Toast.LENGTH_SHORT).show()
    return
}

// 2. 检查表盘信息是否已获取
val clockDialInfoService = BajiSDK.getInstance().getClockDialInfoService()
if (!clockDialInfoService.hasClockDialInfo()) {
    // 表盘信息未获取，先请求
    clockDialInfoService.requestClockDialInfo()
    
    // 等待表盘信息获取完成
    Handler(Looper.getMainLooper()).postDelayed({
        if (clockDialInfoService.hasClockDialInfo()) {
            // 表盘信息已获取，继续后续流程
            proceedWithFileUpload()
        } else {
            Toast.makeText(context, "表盘信息获取失败，请重新连接设备", Toast.LENGTH_SHORT).show()
        }
    }, 5000)
} else {
    // 表盘信息已获取，直接继续
    proceedWithFileUpload()
}

fun proceedWithFileUpload() {
    // 3. 转换图片/视频（需要表盘信息中的屏幕尺寸）
    val clockDialInfo = clockDialInfoService.getCurrentClockDialInfo()
    val targetWidth = clockDialInfo?.width ?: 240
    val targetHeight = clockDialInfo?.height ?: 240
    
    // 转换图片
    val imageService = BajiSDK.getInstance().getImageConvertService()
    val imageParams = ImageConvertParams(
        targetWidth = targetWidth,
        targetHeight = targetHeight,
        quality = 90,
        outputFormat = ImageConvertParams.ImageFormat.BIN,
        algorithm = clockDialInfo?.algorithm ?: 0
    )
    
    imageService.convertImage(
        inputPath = "/path/to/original.jpg",
        outputPath = "/path/to/converted.bin",
        params = imageParams
    )
    
    // 4. 上传转换后的文件
    val fileService = BajiSDK.getInstance().getFileTransferService()
    fileService.uploadFile(
        filePath = "/path/to/converted.bin",
        fileType = FileInfo.FileType.IMAGE
    )
}
```

#### 5. 监听表盘信息获取事件

可以通过EventBus监听表盘信息获取完成事件：

```kotlin
@Subscribe(threadMode = ThreadMode.MAIN)
fun onClockDialInfoEvent(event: ClockDialInfoEvent) {
    if (event.body != null) {
        val clockDialInfo = event.body
        Log.d("App", "表盘信息获取成功: ${clockDialInfo.width}x${clockDialInfo.height}")
        // 表盘信息已获取，可以开始上传文件
        proceedWithFileUpload()
    } else {
        Log.e("App", "表盘信息获取失败")
    }
}
```

### 图片/视频转换流程

图片和视频转换需要使用表盘信息中的屏幕尺寸参数：

```kotlin
// 1. 获取表盘信息
val clockDialInfoService = BajiSDK.getInstance().getClockDialInfoService()
val clockDialInfo = clockDialInfoService.getCurrentClockDialInfo()

if (clockDialInfo == null) {
    Toast.makeText(context, "请先连接设备并获取表盘信息", Toast.LENGTH_SHORT).show()
    return
}

// 2. 使用表盘信息中的屏幕尺寸进行转换
val targetWidth = clockDialInfo.width
val targetHeight = clockDialInfo.height
val screenType = clockDialInfo.screenType // 0=方屏，1=圆屏
val algorithm = clockDialInfo.algorithm

// 3. 转换图片
val imageParams = ImageConvertParams(
    targetWidth = targetWidth,
    targetHeight = targetHeight,
    quality = 90,
    outputFormat = ImageConvertParams.ImageFormat.BIN,
    algorithm = algorithm
)

imageService.convertImage(
    inputPath = "/path/to/input.jpg",
    outputPath = "/path/to/output.bin",
    params = imageParams
)

// 4. 转换视频
val videoParams = VideoConvertParams(
    targetWidth = targetWidth,
    targetHeight = targetHeight,
    fps = 5,
    quality = 3
)

videoService.convertToAVI(
    inputPath = "/path/to/input.mp4",
    outputPath = "/path/to/output.avi",
    params = videoParams
)
```

### 流程总结

1. **连接设备** → 2. **等待表盘信息自动获取**（或手动请求）→ 3. **检查表盘信息是否存在** → 4. **转换文件（使用表盘信息中的尺寸）** → 5. **上传文件**

**注意事项**：
- 表盘信息获取通常需要3-5秒，请耐心等待
- 如果表盘信息获取失败，请检查设备连接状态并重新连接
- 上传文件前必须确保表盘信息已获取，否则会失败
- 图片/视频转换需要使用表盘信息中的屏幕尺寸，确保转换后的文件适配设备屏幕

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

## 发布SDK到Maven仓库

### 使用GitHub Actions自动发布（推荐）

SDK已配置GitHub Actions工作流，可以自动发布到GitHub Packages，**无需任何本地配置**。

#### 发布方式

**方式一：通过创建GitHub Release发布（推荐）**

1. 在 `sdk/build.gradle` 中更新版本号（如：`version = '1.0.1'`）
2. 提交并推送代码到GitHub
3. 在GitHub仓库页面：
   - 点击右侧 "Releases" > "Create a new release"
   - 输入版本标签（如：`v1.0.1`，会自动移除v前缀）
   - 填写Release标题和描述
   - 点击 "Publish release"
4. GitHub Actions会自动触发，构建并发布SDK到GitHub Packages

**方式二：手动触发发布**

1. 在 `sdk/build.gradle` 中更新版本号
2. 提交并推送代码到GitHub
3. 在GitHub仓库页面：
   - 点击 "Actions" 标签页
   - 选择 "Publish SDK to GitHub Packages" 工作流
   - 点击 "Run workflow"
   - 输入版本号（如：`1.0.1`）
   - 点击 "Run workflow" 按钮
4. 等待工作流完成，SDK会自动发布

#### 优势

- ✅ **无需本地配置**：不需要配置GitHub Token或环境变量
- ✅ **自动版本管理**：通过Release标签或手动输入版本号
- ✅ **自动代码混淆**：Release构建会自动启用ProGuard混淆
- ✅ **安全可靠**：使用GitHub内置的 `GITHUB_TOKEN`，无需管理密钥

#### 查看发布结果

发布成功后，可以在以下位置查看：
- GitHub Packages：`https://github.com/yougaohui/baji-sdk/packages`
- Maven仓库：`https://maven.pkg.github.com/yougaohui/baji-sdk`

#### 仓库信息

- 仓库地址：`https://github.com/yougaohui/baji-sdk`
- Maven仓库地址：`https://maven.pkg.github.com/yougaohui/baji-sdk`
- Group ID：`com.baji`
- Artifact ID：`sdk`

### 源码保护

SDK发布配置已确保：
- ✅ **不发布源码jar**：只发布编译后的AAR文件
- ✅ **代码混淆**：release构建自动启用ProGuard混淆
- ✅ **敏感信息保护**：`.gitignore` 已配置，不会提交token等敏感信息

### 版本管理

发布新版本的标准流程：

1. **更新版本号**：在 `sdk/build.gradle` 中修改 `version` 字段（如：`version = '1.0.1'`）
2. **更新文档**：在 `README.md` 的版本历史中添加新版本说明
3. **提交代码**：
   ```bash
   git add .
   git commit -m "Release version 1.0.1"
   git push origin main
   ```
4. **创建GitHub Release**：
   - 在GitHub仓库页面创建Release
   - 版本标签使用 `v1.0.1`（带v前缀，GitHub Actions会自动处理）
   - GitHub Actions会自动构建并发布SDK

**注意**：如果使用手动触发方式，版本号会在工作流运行时自动更新到 `build.gradle` 中。

## 版本历史

- **1.0.0**: 初始版本，包含所有核心功能

## 📖 更多文档

详细的API参考、使用示例和最佳实践，请查看 [开发文档](docs/DEVELOPMENT.md)。

## 技术支持

如有问题，请联系技术支持团队。

