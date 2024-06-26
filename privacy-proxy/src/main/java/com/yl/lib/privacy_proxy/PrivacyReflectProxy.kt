package com.yl.lib.privacy_proxy

import android.bluetooth.BluetoothAdapter
import android.net.wifi.WifiInfo
import android.telephony.TelephonyManager
import android.text.TextUtils
import android.util.Log
import androidx.annotation.Keep
import com.yl.lib.privacy_annotation.MethodInvokeOpcode
import com.yl.lib.privacy_annotation.PrivacyClassProxy
import com.yl.lib.privacy_annotation.PrivacyMethodProxy
import com.yl.lib.sentry.hook.util.PrivacyProxyUtil
import java.lang.reflect.Method
import java.net.NetworkInterface

/**
 * @author yulun
 * @since 2022-06-17 17:56
 * 代理反射
 */
@Keep
open class PrivacyReflectProxy {

    @Keep
    @PrivacyClassProxy
    object ReflectProxy {

        //成员方法反射调用代理
        @PrivacyMethodProxy(
            originalClass = Method::class,
            originalMethod = "invoke",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun invoke(
            method: Method,
            obj: Any?,
            vararg args: Any?
        ): Any? {
            if (obj is WifiInfo) {
                if ("getMacAddress" == method.name) {
                    if (args.isEmpty()) return PrivacyProxyCall.Proxy.getMacAddress(obj)
                }
            }

            if (obj is TelephonyManager) {
                if ("getMeid" == method.name && args.isEmpty()) {
                    return PrivacyTelephonyProxy.TelephonyProxy.getMeid(obj)
                }
                if ("getMeid" == method.name && args.size == 1 && args[0] is Int) {
                    return PrivacyTelephonyProxy.TelephonyProxy.getMeid(
                        obj,
                        args[0] as Int
                    )
                }
                if ("getDeviceId" == method.name && args.isEmpty()) {
                    return PrivacyTelephonyProxy.TelephonyProxy.getDeviceId(obj)
                }
                if ("getDeviceId" == method.name && args.size == 1 && args[0] is Int) {
                    return PrivacyTelephonyProxy.TelephonyProxy.getDeviceId(
                        obj,
                        args[0] as Int
                    )
                }
                if ("getSubscriberId" == method.name && args.isEmpty()) {
                    return PrivacyTelephonyProxy.TelephonyProxy.getSubscriberId(obj)
                }
                if ("getSubscriberId" == method.name && args.size == 1 && args.get(0) is Int) {
                    return PrivacyTelephonyProxy.TelephonyProxy.getSubscriberId(
                        obj,
                        args[0] as Int
                    )
                }
                if ("getImei" == method.name && args.isEmpty()) {
                    return PrivacyTelephonyProxy.TelephonyProxy.getImei(obj)
                }
                if ("getImei" == method.name && args.size == 1 && args[0] is Int) {
                    return PrivacyTelephonyProxy.TelephonyProxy.getImei(
                        obj,
                        args[0] as Int
                    )
                }
                if ("getSimSerialNumber" == method.name && args.isEmpty()) {
                    return PrivacyTelephonyProxy.TelephonyProxy.getSimSerialNumber(obj)
                }
                if ("getSimSerialNumber" == method.name && args.size == 1 && args[0] is Int) {
                    return PrivacyTelephonyProxy.TelephonyProxy.getSimSerialNumber(
                        obj,
                        args[0] as Int
                    )
                }
            }

            if (obj is NetworkInterface) {
                if ("getHardwareAddress" == method.name && args.isEmpty()) {
                    return PrivacyProxyCall.Proxy.getHardwareAddress(obj)
                }
            }

            if (obj is BluetoothAdapter) {
                if ("getAddress" == method.name && args.isEmpty()) {
                    return PrivacyProxyCall.Proxy.getAddress(obj)
                }
            }

            //未拦截的反射，在此处打印
            PrivacyProxyUtil.Util.doFilePrinter(
                method.name,
                "未代理的方法${method.name}：args=$args",
                bVisitorModel = false
            )

            return method.invoke(obj, *args)
        }
    }
}