# 电子吧唧SDK使用示例

## 完整示例代码

### Application初始化

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // 初始化SDK
        initBajiSDK()
    }
    
    private fun initBajiSDK() {
        val config = SDKConfig.Builder()
            .setApiBaseUrl("https://tomato.gulaike.com")
            .setToken("Bearer 6fcb7f58475b4e5aad8f0f1cadce235e")
            .setEnableLog(true)
            .setEnableOTA(true)
            .build()
        
        val broadcastSender = object : BroadcastSender {
            override fun sendBroadcast(event: BajiBaseEvent) {
                EventBus.getDefault().post(event)
            }
        }
        
        val success = BajiSDK.getInstance().initialize(
            context = this,
            config = config,
            broadcastSender = broadcastSender
        )
        
        if (success) {
            Log.d("App", "电子吧唧SDK初始化成功")
        }
    }
}
```

### Activity中使用

```kotlin
class MainActivity : AppCompatActivity() {
    private lateinit var bajiSDK: BajiSDK
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        bajiSDK = BajiSDK.getInstance()
        
        // 初始化蓝牙服务
        initBluetoothService()
        
        // 初始化视频转换服务
        initVideoConvertService()
        
        // 初始化图片转换服务
        initImageConvertService()
    }
    
    private fun initBluetoothService() {
        val bluetoothService = bajiSDK.getBluetoothService()
        bluetoothService.setConnectionCallback(object : ConnectionCallback {
            override fun onConnected(deviceInfo: DeviceInfo) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "设备已连接: ${deviceInfo.name}", Toast.LENGTH_SHORT).show()
                }
            }
            
            override fun onDisconnected(deviceInfo: DeviceInfo) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "设备已断开", Toast.LENGTH_SHORT).show()
                }
            }
            
            override fun onConnectionFailed(error: String) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "连接失败: $error", Toast.LENGTH_SHORT).show()
                }
            }
            
            override fun onDeviceFound(deviceInfo: DeviceInfo) {
                Log.d("MainActivity", "发现设备: ${deviceInfo.name} (${deviceInfo.macAddress})")
            }
        })
        
        // 开始扫描
        bluetoothService.startScan()
    }
    
    private fun initVideoConvertService() {
        val videoService = bajiSDK.getVideoConvertService()
        videoService.setConvertCallback(object : VideoConvertCallback {
            override fun onConvertStart() {
                Log.d("MainActivity", "开始转换视频")
            }
            
            override fun onConvertProgress(progress: Int) {
                Log.d("MainActivity", "转换进度: $progress%")
            }
            
            override fun onConvertSuccess(outputPath: String) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "转换成功: $outputPath", Toast.LENGTH_SHORT).show()
                }
            }
            
            override fun onConvertFailed(error: String) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "转换失败: $error", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }
    
    private fun initImageConvertService() {
        val imageService = bajiSDK.getImageConvertService()
        imageService.setConvertCallback(object : ImageConvertCallback {
            override fun onConvertSuccess(outputPath: String) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "图片转换成功: $outputPath", Toast.LENGTH_SHORT).show()
                }
            }
            
            override fun onConvertFailed(error: String) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "图片转换失败: $error", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 清理资源（可选，SDK会在应用退出时自动清理）
        // bajiSDK.cleanup()
    }
}
```

## 注意事项

1. 确保在AndroidManifest.xml中声明了所有必要的权限
2. 确保项目中已添加EventBus依赖
3. 确保FFmpeg库已正确集成
4. 确保杰理SDK相关库已正确集成（用于图片转换）

