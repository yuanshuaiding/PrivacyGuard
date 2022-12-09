package com.yl.lib.privacy_proxy

import android.os.Parcel

import android.os.Parcelable




/**
 * @Description: parcelable对象序列化工具类
 * @Author: Eric
 * @Email: yuanshuaiding@163.com
 * @CreateDate: 2022/12/9 9:57
 * @Version: 1.0
 * 参考：https://blog.csdn.net/jielundewode/article/details/78342191
 */
object ParcelableUtil {

    fun marshall(parceable: Parcelable): ByteArray? {
        val parcel = Parcel.obtain()
        parcel.setDataPosition(0)
        parceable.writeToParcel(parcel, 0)
        val bytes = parcel.marshall()
        parcel.recycle()
        return bytes
    }

    fun unmarshall(bytes: ByteArray): Parcel? {
        val parcel = Parcel.obtain()
        parcel.unmarshall(bytes, 0, bytes.size)
        parcel.setDataPosition(0)
        return parcel
    }

}