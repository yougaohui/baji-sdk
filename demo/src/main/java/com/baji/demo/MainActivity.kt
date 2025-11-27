package com.baji.demo

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.baji.protocol.BroadcastSender
import com.baji.protocol.event.BajiBaseEvent
import com.baji.sdk.BajiSDK
import com.baji.sdk.SDKConfig
import com.baji.sdk.callback.*
import com.baji.sdk.model.DeviceInfo
import com.baji.sdk.model.FileInfo
import com.baji.sdk.model.ImageConvertParams
import com.baji.sdk.model.VideoConvertParams
import com.baji.demo.databinding.ActivityMainBinding
import java.io.File
import com.permissionx.guolindev.PermissionX
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var deviceAdapter: DeviceAdapter
    private val deviceList = mutableListOf<DeviceInfo>()
    private var connectedDevice: DeviceInfo? = null
    
    // 蓝牙扫描相关
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var isScanning = false
    private val scanHandler = Handler(Looper.getMainLooper())
    private val scanTimeoutMillis = 30000L // 30秒扫描超时
    
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { syncImage(it) }
    }
    
    private val videoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { syncVideo(it) }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 注册EventBus
        EventBus.getDefault().register(this)
        
        // 初始化蓝牙管理器
        initBluetoothManager()
        
        // 初始化SDK
        initSDK()
        
        // 初始化UI
        initUI()
        
        // 请求权限
        requestPermissions()
    }
    
    private fun initBluetoothManager() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
        
        if (bluetoothAdapter == null) {
            Log.e(TAG, "设备不支持蓝牙")
            Toast.makeText(this, "设备不支持蓝牙", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun initSDK() {
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
            Log.d(TAG, "SDK初始化成功")
            setupCallbacks()
        } else {
            Toast.makeText(this, "SDK初始化失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupCallbacks() {
        val bluetoothService = BajiSDK.getInstance().getBluetoothService()
        bluetoothService.setConnectionCallback(object : ConnectionCallback {
            override fun onConnected(deviceInfo: DeviceInfo) {
                runOnUiThread {
                    connectedDevice = deviceInfo
                    updateConnectionStatus(true)
                    Toast.makeText(this@MainActivity, "设备已连接: ${deviceInfo.name}", Toast.LENGTH_SHORT).show()
                }
            }
            
            override fun onDisconnected(deviceInfo: DeviceInfo) {
                runOnUiThread {
                    connectedDevice = null
                    updateConnectionStatus(false)
                    Toast.makeText(this@MainActivity, "设备已断开", Toast.LENGTH_SHORT).show()
                }
            }
            
            override fun onConnectionFailed(error: String) {
                runOnUiThread {
                    connectedDevice = null
                    updateConnectionStatus(false)
                    Toast.makeText(this@MainActivity, "连接失败: $error", Toast.LENGTH_SHORT).show()
                }
            }
            
            override fun onDeviceFound(deviceInfo: DeviceInfo) {
                runOnUiThread {
                    if (!deviceList.any { it.macAddress == deviceInfo.macAddress }) {
                        deviceList.add(deviceInfo)
                        deviceAdapter.notifyItemInserted(deviceList.size - 1)
                    }
                }
            }
        })
        
        val fileService = BajiSDK.getInstance().getFileTransferService()
        fileService.setTransferCallback(object : FileTransferCallback {
            override fun onTransferStart() {
                runOnUiThread {
                    binding.syncProgress.visibility = View.VISIBLE
                    binding.syncProgress.progress = 0
                }
            }
            
            override fun onTransferProgress(progress: Int, bytesTransferred: Long, totalBytes: Long) {
                runOnUiThread {
                    binding.syncProgress.progress = progress
                    binding.syncStatusText.text = getString(R.string.sync_progress, progress)
                }
            }
            
            override fun onTransferSuccess() {
                runOnUiThread {
                    binding.syncProgress.visibility = View.GONE
                    binding.syncStatusText.text = getString(R.string.sync_success)
                    Toast.makeText(this@MainActivity, getString(R.string.sync_success), Toast.LENGTH_SHORT).show()
                }
            }
            
            override fun onTransferFailed(error: String) {
                runOnUiThread {
                    binding.syncProgress.visibility = View.GONE
                    binding.syncStatusText.text = getString(R.string.sync_failed) + ": $error"
                    Toast.makeText(this@MainActivity, getString(R.string.sync_failed) + ": $error", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }
    
    private fun initUI() {
        // 设备列表
        deviceAdapter = DeviceAdapter(deviceList) { device ->
            if (connectedDevice?.macAddress == device.macAddress) {
                // 断开连接
                BajiSDK.getInstance().getBluetoothService().disconnectDevice()
            } else {
                // 连接设备
                BajiSDK.getInstance().getBluetoothService().connectDevice(device.macAddress)
            }
        }
        
        binding.deviceRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.deviceRecyclerView.adapter = deviceAdapter
        
        // 扫描按钮
        binding.scanButton.setOnClickListener {
            if (binding.scanButton.text == getString(R.string.scan_devices)) {
                startScan()
            } else {
                stopScan()
            }
        }
        
        // 同步图片按钮
        binding.syncImageButton.setOnClickListener {
            if (connectedDevice == null) {
                Toast.makeText(this, "请先连接设备", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            imagePickerLauncher.launch("image/*")
        }
        
        // 同步视频按钮
        binding.syncVideoButton.setOnClickListener {
            if (connectedDevice == null) {
                Toast.makeText(this, "请先连接设备", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            videoPickerLauncher.launch("video/*")
        }
        
        updateConnectionStatus(false)
    }
    
    private fun requestPermissions() {
        PermissionX.init(this)
            .permissions(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
            .request { allGranted, grantedList, deniedList ->
                if (allGranted) {
                    Log.d(TAG, "所有权限已授予")
                } else {
                    Toast.makeText(this, "需要授予权限才能使用", Toast.LENGTH_SHORT).show()
                }
            }
    }
    
    private fun startScan() {
        // 检查蓝牙是否可用
        if (!checkBluetoothAvailable()) {
            if (bluetoothAdapter == null) {
                Toast.makeText(this, "设备不支持蓝牙", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "请先开启蓝牙", Toast.LENGTH_SHORT).show()
            }
            return
        }
        
        // 检查权限
        if (!checkBluetoothPermissions()) {
            Toast.makeText(this, "需要蓝牙扫描权限", Toast.LENGTH_SHORT).show()
            requestPermissions()
            return
        }
        
        // 如果正在扫描，先停止
        if (isScanning) {
            stopScan()
            return
        }
        
        // 清空设备列表
        deviceList.clear()
        deviceAdapter.notifyDataSetChanged()
        
        // 开始扫描
        isScanning = true
        binding.scanButton.text = getString(R.string.stop_scan)
        
        try {
            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            
            val scanFilters = emptyList<ScanFilter>() // 不过滤，扫描所有设备
            
            if (bluetoothLeScanner != null) {
                bluetoothLeScanner!!.startScan(scanFilters, scanSettings, scanCallback)
                Log.d(TAG, "开始扫描蓝牙设备")
                
                // 设置扫描超时
                scanHandler.postDelayed({
                    if (isScanning) {
                        stopScan()
                        Toast.makeText(this, "扫描超时，已停止扫描", Toast.LENGTH_SHORT).show()
                    }
                }, scanTimeoutMillis)
            } else {
                Log.e(TAG, "BluetoothLeScanner不可用")
                isScanning = false
                binding.scanButton.text = getString(R.string.scan_devices)
                Toast.makeText(this, "蓝牙扫描器不可用", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "扫描失败：权限不足", e)
            isScanning = false
            binding.scanButton.text = getString(R.string.scan_devices)
            Toast.makeText(this, "扫描失败：权限不足", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "扫描失败：${e.message}", e)
            isScanning = false
            binding.scanButton.text = getString(R.string.scan_devices)
            Toast.makeText(this, "扫描失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun stopScan() {
        if (!isScanning) {
            return
        }
        
        isScanning = false
        binding.scanButton.text = getString(R.string.scan_devices)
        
        // 移除超时回调
        scanHandler.removeCallbacksAndMessages(null)
        
        try {
            if (bluetoothLeScanner != null && checkBluetoothPermissions()) {
                bluetoothLeScanner!!.stopScan(scanCallback)
                Log.d(TAG, "停止扫描蓝牙设备")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "停止扫描失败：权限不足", e)
        } catch (e: Exception) {
            Log.e(TAG, "停止扫描失败：${e.message}", e)
        }
    }
    
    // 扫描回调
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            handleScanResult(result)
        }
        
        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            super.onBatchScanResults(results)
            for (result in results) {
                handleScanResult(result)
            }
        }
        
        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            runOnUiThread {
                val errorMsg = when (errorCode) {
                    ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "扫描已在进行中"
                    ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "应用注册失败"
                    ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "不支持扫描功能"
                    ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "内部错误"
                    else -> "扫描失败：错误代码 $errorCode"
                }
                Log.e(TAG, errorMsg)
                Toast.makeText(this@MainActivity, errorMsg, Toast.LENGTH_SHORT).show()
                if (isScanning) {
                    stopScan()
                }
            }
        }
    }
    
    private fun handleScanResult(result: ScanResult) {
        val device = result.device
        val deviceName = device.name ?: "Unknown Device"
        val macAddress = device.address
        
        // 过滤掉无效的设备
        if (macAddress.isBlank()) {
            return
        }
        
        // 转换为DeviceInfo
        val deviceInfo = DeviceInfo(
            name = deviceName,
            macAddress = macAddress,
            isConnected = false
        )
        
        // 通过SDK的回调通知设备发现
        // 注意：setupCallbacks中已设置的ConnectionCallback.onDeviceFound会更新UI
        runOnUiThread {
            Log.d(TAG, "发现设备: $deviceName ($macAddress)")
            val bluetoothService = BajiSDK.getInstance().getBluetoothService()
            bluetoothService.onDeviceFound(deviceInfo)
        }
    }
    
    private fun checkBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ 需要BLUETOOTH_SCAN权限
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Android 11及以下需要位置权限
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
    
    private fun checkBluetoothAvailable(): Boolean {
        if (bluetoothAdapter == null) {
            return false
        }
        return isBluetoothEnabled()
    }
    
    private fun updateConnectionStatus(connected: Boolean) {
        if (connected) {
            binding.connectionStatus.text = getString(R.string.connected)
            binding.connectionStatus.setTextColor(getColor(android.R.color.holo_green_dark))
            binding.syncImageButton.isEnabled = true
            binding.syncVideoButton.isEnabled = true
        } else {
            binding.connectionStatus.text = getString(R.string.disconnected)
            binding.connectionStatus.setTextColor(getColor(android.R.color.holo_red_dark))
            binding.syncImageButton.isEnabled = false
            binding.syncVideoButton.isEnabled = false
        }
    }
    
    private fun syncImage(uri: Uri) {
        try {
            // 将URI内容复制到临时文件
            val tempFile = File(getExternalFilesDir(null), "temp_image_${System.currentTimeMillis()}.jpg")
            contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            val filePath = tempFile.absolutePath
            
            // 转换图片（如果需要）
            val imageService = BajiSDK.getInstance().getImageConvertService()
            imageService.setConvertCallback(object : ImageConvertCallback {
                override fun onConvertSuccess(outputPath: String) {
                    // 上传转换后的图片
                    val fileService = BajiSDK.getInstance().getFileTransferService()
                    fileService.uploadFile(outputPath, FileInfo.FileType.IMAGE)
                }
                
                override fun onConvertFailed(error: String) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "图片转换失败: $error", Toast.LENGTH_SHORT).show()
                    }
                }
            })
            
            // 转换图片为设备格式（240x240, bin格式）
            val params = ImageConvertParams(
                targetWidth = 240,
                targetHeight = 240,
                quality = 90,
                outputFormat = ImageConvertParams.ImageFormat.BIN,
                algorithm = 0
            )
            
            val outputPath = getExternalFilesDir(null)?.absolutePath + "/converted_image_${System.currentTimeMillis()}.bin"
            imageService.convertImage(filePath, outputPath, params)
            
        } catch (e: Exception) {
            Log.e(TAG, "同步图片失败: ${e.message}", e)
            Toast.makeText(this, "同步图片失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun syncVideo(uri: Uri) {
        try {
            // 将URI内容复制到临时文件
            val tempFile = File(getExternalFilesDir(null), "temp_video_${System.currentTimeMillis()}.mp4")
            contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            val filePath = tempFile.absolutePath
            
            // 转换视频为AVI格式
            val videoService = BajiSDK.getInstance().getVideoConvertService()
            videoService.setConvertCallback(object : VideoConvertCallback {
                override fun onConvertStart() {
                    runOnUiThread {
                        binding.syncProgress.visibility = View.VISIBLE
                        binding.syncProgress.progress = 0
                        binding.syncStatusText.text = "正在转换视频..."
                    }
                }
                
                override fun onConvertProgress(progress: Int) {
                    runOnUiThread {
                        binding.syncProgress.progress = progress
                        binding.syncStatusText.text = "转换进度: $progress%"
                    }
                }
                
                override fun onConvertSuccess(outputPath: String) {
                    // 上传转换后的视频
                    val fileService = BajiSDK.getInstance().getFileTransferService()
                    fileService.uploadFile(outputPath, FileInfo.FileType.VIDEO)
                }
                
                override fun onConvertFailed(error: String) {
                    runOnUiThread {
                        binding.syncProgress.visibility = View.GONE
                        Toast.makeText(this@MainActivity, "视频转换失败: $error", Toast.LENGTH_SHORT).show()
                    }
                }
            })
            
            // 转换视频为AVI格式（240x240, 5fps）
            val params = VideoConvertParams(
                targetWidth = 240,
                targetHeight = 240,
                fps = 5,
                quality = 3
            )
            
            val outputPath = getExternalFilesDir(null)?.absolutePath + "/converted_video_${System.currentTimeMillis()}.avi"
            videoService.convertToAVI(filePath, outputPath, params)
            
        } catch (e: Exception) {
            Log.e(TAG, "同步视频失败: ${e.message}", e)
            Toast.makeText(this, "同步视频失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: Any) {
        // 处理EventBus事件
        Log.d(TAG, "收到事件: ${event.javaClass.simpleName}")
    }
    
    override fun onPause() {
        super.onPause()
        // 可选：在Activity暂停时停止扫描以节省电量
        // if (isScanning) {
        //     stopScan()
        // }
    }
    
    override fun onResume() {
        super.onResume()
        // 如果蓝牙被关闭，更新UI状态
        if (bluetoothAdapter != null && !bluetoothAdapter!!.isEnabled && isScanning) {
            stopScan()
            Toast.makeText(this, "蓝牙已关闭，停止扫描", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // 停止扫描
        if (isScanning) {
            stopScan()
        }
        
        EventBus.getDefault().unregister(this)
        // 可选：清理SDK资源
        // BajiSDK.getInstance().cleanup()
    }
    
    companion object {
        private const val TAG = "MainActivity"
    }
}

