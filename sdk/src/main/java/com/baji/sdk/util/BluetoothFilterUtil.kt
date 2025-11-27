package com.baji.sdk.util

import android.util.Log
import android.util.SparseArray
import com.legend.mywatch.sdk.mywatchsdklib.android.bluetooth.BluetoothHelper
import com.legend.mywatch.sdk.mywatchsdklib.android.bluetooth.WatchBluetoothRecordFilterTools

/**
 * 蓝牙设备过滤工具类
 * 用于过滤扫描到的蓝牙设备，只保留电子吧唧设备
 */
object BluetoothFilterUtil {
    private const val TAG = "BluetoothFilterUtil"
    
    /**
     * 目标设备特征值（0xAA01）
     */
    private const val TARGET_FEATURE = 0xAA01
    
    /**
     * 电子吧唧设备类型
     */
    private const val DEVICE_TYPE_BAJI = 3
    
    /**
     * 检查扫描结果是否为电子吧唧设备
     * 
     * @param manufacturerData 制造商数据（从ScanResult.scanRecord.manufacturerSpecificData获取）
     * @return true表示是电子吧唧设备，false表示不是
     */
    fun isBajiDevice(manufacturerData: SparseArray<ByteArray?>?): Boolean {
        if (manufacturerData == null || manufacturerData.size() == 0) {
            return false
        }
        
        // 先使用WatchBluetoothRecordFilterTools检查设备特征
        val isTargetDevice = WatchBluetoothRecordFilterTools.isFindFeature(
            manufacturerData,
            TARGET_FEATURE,
            false // 使用用户定制版本（精确匹配）
        )
        
        if (!isTargetDevice) {
            Log.d(TAG, "设备特征不匹配，过滤掉")
            return false
        }
        
        // 使用BluetoothHelper解析设备信息
        val advertiseData = manufacturerData.valueAt(0)
        if (advertiseData == null) {
            Log.d(TAG, "制造商数据为空，过滤掉")
            return false
        }
        
        val recordInfo = BluetoothHelper.parseRecordInfo(advertiseData)
        
        // 检查设备类型是否为3（电子吧唧）
        val deviceType = recordInfo?.getDeviceType()
        if (deviceType != DEVICE_TYPE_BAJI) {
            Log.d(TAG, "设备类型不匹配，过滤掉。设备类型: $deviceType，期望: $DEVICE_TYPE_BAJI")
            return false
        }
        
        return true
    }
    
    /**
     * 从扫描结果中提取制造商数据并检查是否为电子吧唧设备
     * 
     * @param scanRecord 扫描记录（从ScanResult.scanRecord获取）
     * @return true表示是电子吧唧设备，false表示不是
     */
    fun isBajiDeviceFromScanRecord(scanRecord: Any?): Boolean {
        return try {
            // 使用反射获取manufacturerSpecificData
            val method = scanRecord?.javaClass?.getMethod("getManufacturerSpecificData")
            val manufacturerData = method?.invoke(scanRecord) as? SparseArray<ByteArray?>
            isBajiDevice(manufacturerData)
        } catch (e: Exception) {
            Log.e(TAG, "获取制造商数据失败: ${e.message}", e)
            false
        }
    }
    
    /**
     * 检查设备名称是否有效
     * 过滤掉名称为null、空字符串或"未知设备"的设备
     * 
     * @param deviceName 设备名称
     * @return true表示设备名称有效，false表示无效
     */
    fun isValidDeviceName(deviceName: String?): Boolean {
        if (deviceName.isNullOrBlank()) {
            return false
        }
        
        // 过滤掉"未知设备"
        if (deviceName == "未知设备" || deviceName.equals("Unknown Device", ignoreCase = true)) {
            return false
        }
        
        return true
    }
    
    /**
     * 综合检查：检查设备是否为有效的电子吧唧设备
     * 
     * @param manufacturerData 制造商数据
     * @param deviceName 设备名称
     * @return true表示是有效的电子吧唧设备，false表示不是
     */
    fun isValidBajiDevice(manufacturerData: SparseArray<ByteArray?>?, deviceName: String?): Boolean {
        // 先检查设备名称
        if (!isValidDeviceName(deviceName)) {
            Log.d(TAG, "设备名称无效，过滤掉: $deviceName")
            return false
        }
        
        // 再检查设备特征和类型
        if (!isBajiDevice(manufacturerData)) {
            return false
        }
        
        return true
    }
}

