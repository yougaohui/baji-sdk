package com.baji.demo

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.baji.demo.databinding.ActivityMainBinding
import com.baji.demo.viewmodel.ImageSyncViewModel
import com.baji.protocol.BroadcastSender
import com.baji.protocol.event.BajiBaseEvent
import com.baji.sdk.BajiSDK
import com.baji.sdk.SDKConfig
import com.baji.sdk.callback.ConnectionCallback
import com.baji.sdk.callback.FileTransferCallback
import com.baji.sdk.callback.ImageConvertCallback
import com.baji.sdk.callback.VideoConvertCallback
import com.baji.sdk.model.DeviceInfo
import com.baji.sdk.model.FileInfo
import com.baji.sdk.model.ImageConvertParams
import com.baji.sdk.model.VideoConvertParams
import com.baji.sdk.util.BluetoothFilterUtil
import com.luck.picture.lib.PictureSelector
import com.luck.picture.lib.config.PictureConfig
import com.permissionx.guolindev.PermissionX
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.File

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var deviceAdapter: DeviceAdapter
    private val deviceList = mutableListOf<DeviceInfo>()
    private var connectedDevice: DeviceInfo? = null
    
    // ViewModel
    private lateinit var imageSyncViewModel: ImageSyncViewModel
    
    // 蓝牙扫描相关
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var isScanning = false
    private val scanHandler = Handler(Looper.getMainLooper())
    private val scanTimeoutMillis = 30000L // 30秒扫描超时
    
    // PictureSelector 使用 onActivityResult，不需要 launcher
    
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
        
        // 初始化ViewModel
        imageSyncViewModel = ViewModelProvider(this)[ImageSyncViewModel::class.java]
        setupImageSyncViewModel()
        
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
                    
                    // 更新设备列表中的连接状态
                    val index = deviceList.indexOfFirst { it.macAddress == deviceInfo.macAddress }
                    if (index != -1) {
                        deviceList[index] = deviceInfo.copy(isConnected = true)
                        deviceAdapter.notifyItemChanged(index)
                    }
                    
                    Toast.makeText(this@MainActivity, "设备已连接: ${deviceInfo.name}", Toast.LENGTH_SHORT).show()
                }
            }
            
            override fun onDisconnected(deviceInfo: DeviceInfo) {
                runOnUiThread {
                    // 更新设备列表中的连接状态
                    val index = deviceList.indexOfFirst { it.macAddress == deviceInfo.macAddress }
                    if (index != -1) {
                        deviceList[index] = deviceInfo.copy(isConnected = false)
                        deviceAdapter.notifyItemChanged(index)
                    }
                    
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
                    val existingIndex = deviceList.indexOfFirst { it.macAddress == deviceInfo.macAddress }
                    if (existingIndex == -1) {
                        // 新设备，添加到列表
                        deviceList.add(deviceInfo)
                        deviceAdapter.notifyItemInserted(deviceList.size - 1)
                    } else {
                        // 已存在的设备，更新信息（包括RSSI）
                        deviceList[existingIndex] = deviceInfo
                        deviceAdapter.notifyItemChanged(existingIndex)
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
            val bluetoothService = BajiSDK.getInstance().getBluetoothService()
            if (connectedDevice?.macAddress == device.macAddress) {
                // 断开连接
                bluetoothService.disconnectDevice()
            } else {
                // 连接设备
                bluetoothService.connectDevice(device.macAddress)
                
                // 连接后延迟检查连接状态，确保UI正确更新
                Handler(Looper.getMainLooper()).postDelayed({
                    bluetoothService.checkAndUpdateConnectionStatus()
                }, 3000) // 3秒后再次检查连接状态
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
            imageSyncViewModel.startPictureSelector(this)
        }
        
        // 同步视频按钮
        binding.syncVideoButton.setOnClickListener {
            if (connectedDevice == null) {
                Toast.makeText(this, "请先连接设备", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // TODO: 实现视频选择功能
            Toast.makeText(this, "视频同步功能待实现", Toast.LENGTH_SHORT).show()
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
        // Android 12+需要权限才能访问设备名称和地址
        val deviceName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                device.name
            } else {
                null
            }
        } else {
            device.name
        }
        val macAddress = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                device.address
            } else {
                ""
            }
        } else {
            device.address
        }
        
        // 过滤掉无效的设备
        if (macAddress.isBlank()) {
            return
        }
        
        // 使用SDK的过滤工具检查是否为电子吧唧设备
        val scanRecord = result.scanRecord
        val manufacturerData = scanRecord?.manufacturerSpecificData
        
        // 综合检查：设备名称和设备特征
        if (!BluetoothFilterUtil.isValidBajiDevice(manufacturerData, deviceName)) {
            Log.d(TAG, "过滤非电子吧唧设备: $deviceName ($macAddress)")
            return
        }
        
        // 获取信号强度（RSSI）
        val rssi = result.rssi
        
        // 转换为DeviceInfo
        val deviceInfo = DeviceInfo(
            name = deviceName ?: "Unknown Device",
            macAddress = macAddress,
            isConnected = false,
            rssi = rssi
        )
        
        // 通过SDK的回调通知设备发现
        // 注意：setupCallbacks中已设置的ConnectionCallback.onDeviceFound会更新UI
        runOnUiThread {
            Log.d(TAG, "发现电子吧唧设备: ${deviceInfo.name} (${deviceInfo.macAddress})")
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
    
    private fun syncImage(imagePath: String) {
        try {
            val file = File(imagePath)
            if (!file.exists()) {
                Toast.makeText(this, "图片文件不存在", Toast.LENGTH_SHORT).show()
                return
            }
            
            val filePath = file.absolutePath
            
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
    
    
    /**
     * 设置图片同步ViewModel的回调
     */
    private fun setupImageSyncViewModel() {
        imageSyncViewModel.setFileTransferCallback(object : FileTransferCallback {
            override fun onTransferStart() {
                runOnUiThread {
                    binding.syncProgress.visibility = View.VISIBLE
                    binding.syncStatusText.text = getString(R.string.syncing)
                }
            }

            override fun onTransferProgress(progress: Int, bytesTransferred: Long, totalBytes: Long) {
                runOnUiThread {
                    binding.syncStatusText.text = getString(R.string.syncing) + " ($progress%)"
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
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                PictureConfig.CHOOSE_REQUEST -> {
                    // 处理PictureSelector返回的结果
                    val result = PictureSelector.obtainMultipleResult(data)
                    if (result.isNotEmpty()) {
                        val localMedia = result[0]
                        imageSyncViewModel.handlePictureSelectorResult(localMedia, this)
                    }
                }
            }
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

