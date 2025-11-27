package com.baji.demo.model

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable

/**
 * 简单的视频信息数据类，用于替代 EasyPhotos 的 Photo 对象
 */
data class VideoInfo(
    val path: String, 
    val name: String, 
    val uri: Uri
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readParcelable(Uri::class.java.classLoader) ?: Uri.EMPTY
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(path)
        parcel.writeString(name)
        parcel.writeParcelable(uri, flags)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<VideoInfo> {
        override fun createFromParcel(parcel: Parcel): VideoInfo {
            return VideoInfo(parcel)
        }

        override fun newArray(size: Int): Array<VideoInfo?> {
            return arrayOfNulls(size)
        }
    }
}
