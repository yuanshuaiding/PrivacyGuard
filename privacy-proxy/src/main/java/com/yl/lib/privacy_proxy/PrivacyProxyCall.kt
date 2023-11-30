package com.yl.lib.privacy_proxy

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.content.*
import android.content.ClipDescription.MIMETYPE_TEXT_PLAIN
import android.content.pm.*
import android.database.sqlite.SQLiteOpenHelper
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.DhcpInfo
import android.net.NetworkInfo
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.telephony.CellInfo
import android.telephony.TelephonyManager
import android.util.Base64
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.umeng.analytics.CoreProtocol
import com.umeng.commonsdk.utils.UMUtils
import com.yl.lib.privacy_annotation.MethodInvokeOpcode
import com.yl.lib.privacy_annotation.PrivacyClassProxy
import com.yl.lib.privacy_annotation.PrivacyMethodProxy
import com.yl.lib.sentry.hook.PrivacySentry
import com.yl.lib.sentry.hook.cache.CachePrivacyManager
import com.yl.lib.sentry.hook.cache.CacheUtils
import com.yl.lib.sentry.hook.util.PrivacyClipBoardManager
import com.yl.lib.sentry.hook.util.PrivacyLog
import com.yl.lib.sentry.hook.util.PrivacyProxyUtil.Util.doFilePrinter
import com.yl.lib.sentry.hook.util.PrivacyUtil
import java.io.File
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.Executor

/**
 * @author yulun
 * @since 2021-12-22 14:23
 * 大部分敏感api拦截代理
 */
@Keep
open class PrivacyProxyCall {

    // kotlin里实际解析的是这个PrivacyProxyCall$Proxy 内部类
    @PrivacyClassProxy
    @Keep
    object Proxy {
        // 这个方法的注册放在了PrivacyProxyCall2中，提供了一个java注册的例子
        @PrivacyMethodProxy(
            originalClass = ActivityManager::class,
            originalMethod = "getRunningTasks",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun getRunningTasks(
            manager: ActivityManager,
            maxNum: Int
        ): List<ActivityManager.RunningTaskInfo?>? {
            doFilePrinter("getRunningTasks", methodDocumentDesc = "当前运行中的任务")
            if (PrivacySentry.Privacy.getBuilder()
                    ?.isVisitorModel() == true || PrivacySentry.Privacy.getBuilder()
                    ?.isForbiddenAPI("getRunningTasks") == true
            ) {
                return emptyList()
            }
            return manager.getRunningTasks(maxNum)
        }

        @JvmStatic
        @PrivacyMethodProxy(
            originalClass = ActivityManager::class,
            originalMethod = "getRecentTasks",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        fun getRecentTasks(
            manager: ActivityManager,
            maxNum: Int,
            flags: Int
        ): List<ActivityManager.RecentTaskInfo>? {
            doFilePrinter("getRecentTasks", methodDocumentDesc = "最近运行中的任务")
            if (PrivacySentry.Privacy.getBuilder()
                    ?.isVisitorModel() == true || PrivacySentry.Privacy.getBuilder()
                    ?.isForbiddenAPI("getRecentTasks") == true
            ) {
                return emptyList()
            }
            return manager.getRecentTasks(maxNum, flags)
        }


        @PrivacyMethodProxy(
            originalClass = ActivityManager::class,
            originalMethod = "getRunningAppProcesses",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun getRunningAppProcesses(manager: ActivityManager): List<ActivityManager.RunningAppProcessInfo> {
            doFilePrinter("getRunningAppProcesses", methodDocumentDesc = "当前运行中的进程")
            if (PrivacySentry.Privacy.getBuilder()
                    ?.isVisitorModel() == true || PrivacySentry.Privacy.getBuilder()
                    ?.isForbiddenAPI("getRunningAppProcesses") == true
            ) {
                return emptyList()
            }

            var appProcess: List<ActivityManager.RunningAppProcessInfo> = emptyList()
            try {
                // 线上三星11和12的机子 有上报，量不大
                appProcess = manager.runningAppProcesses
            } catch (e: Throwable) {
                e.printStackTrace()
            }
            return appProcess
        }

        @PrivacyMethodProxy(
            originalClass = PackageManager::class,
            originalMethod = "getInstalledPackages",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun getInstalledPackages(manager: PackageManager, flags: Int): List<PackageInfo> {
            doFilePrinter(
                "getInstalledPackages",
                methodDocumentDesc = "安装包列表-getInstalledPackages-${flags}"
            )
            if (PrivacySentry.Privacy.getBuilder()
                    ?.isVisitorModel() == true || PrivacySentry.Privacy.getBuilder()
                    ?.isForbiddenAPI("getInstalledPackages") == true
            ) {
                return emptyList()
            }
            return manager.getInstalledPackages(flags)
        }

        @RequiresApi(Build.VERSION_CODES.O)
        @PrivacyMethodProxy(
            originalClass = PackageManager::class,
            originalMethod = "getPackageInfo",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun getPackageInfo(
            manager: PackageManager,
            versionedPackage: VersionedPackage,
            flags: Int
        ): PackageInfo? {

            if (PrivacySentry.Privacy.getBuilder()
                    ?.isVisitorModel() == true || PrivacySentry.Privacy.getBuilder()
                    ?.isForbiddenAPI("getPackageInfo") == true
            ) {
                doFilePrinter(
                    "getPackageInfo",
                    methodDocumentDesc = "安装包-getPackageInfo-$versionedPackage",
                    bVisitorModel = true
                )
                return null
            }

            // 增加缓存
            try {
                val value = CachePrivacyManager.Manager.loadWithTimeCache(
                    "getPackageInfo-$flags-${versionedPackage.packageName}",
                    "getPackageInfo",
                    "NameNotFoundException",
                    String::class,
                    duration = CacheUtils.Utils.MINUTE * 30
                ) {
                    doFilePrinter(
                        "getPackageInfo-$flags-${versionedPackage.packageName}",
                        methodDocumentDesc = "安装包-getPackageInfo-$flags-$versionedPackage"
                    )
                    val p = manager.getPackageInfo(versionedPackage, flags)
                    val byte = ParcelableUtil.marshall(p)
                    Base64.encodeToString(byte, 0)
                }
                if ("NameNotFoundException" == value) {
                    return null
                }
                val parcel = ParcelableUtil.unmarshall(Base64.decode(value, 0))
                val pkg = PackageInfo.CREATOR.createFromParcel(parcel)
                if (pkg != null && !pkg.packageName.isNullOrEmpty()) {
                    PrivacyLog.i("getPackageInfo-$flags-$versionedPackage :成功从缓存获取对象")
                    return pkg
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            return manager.getPackageInfo(versionedPackage, flags)
        }

        @PrivacyMethodProxy(
            originalClass = PackageManager::class,
            originalMethod = "getPackageInfo",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun getPackageInfo(
            manager: PackageManager,
            packageName: String,
            flags: Int
        ): PackageInfo? {
            if (PrivacySentry.Privacy.getBuilder()
                    ?.isVisitorModel() == true || PrivacySentry.Privacy.getBuilder()
                    ?.isForbiddenAPI("getPackageInfo") == true
            ) {
                doFilePrinter(
                    "getPackageInfo-$flags",
                    methodDocumentDesc = "安装包-getPackageInfo-$flags-${packageName}",
                    bVisitorModel = true
                )
                return null
            }

            // 增加缓存
            try {
                val value = CachePrivacyManager.Manager.loadWithTimeCache(
                    "getPackageInfo-$flags-${packageName}",
                    "getPackageInfo",
                    "NameNotFoundException",
                    String::class,
                    duration = CacheUtils.Utils.MINUTE * 30
                ) {
                    doFilePrinter(
                        "getPackageInfo-$flags-${packageName}",
                        methodDocumentDesc = "安装包-getPackageInfo-$flags"
                    )
                    val p = manager.getPackageInfo(packageName, flags)
                    val byte = ParcelableUtil.marshall(p)
                    Base64.encodeToString(byte, 0)
                }
                if ("NameNotFoundException" == value) {
                    return null
                }
                val parcel = ParcelableUtil.unmarshall(Base64.decode(value, 0))
                val pkg = PackageInfo.CREATOR.createFromParcel(parcel)
                if (pkg != null && !pkg.packageName.isNullOrEmpty()) {
                    PrivacyLog.i("getPackageInfo-$flags-${packageName} :成功从缓存获取对象")
                    return pkg
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            return manager.getPackageInfo(packageName, flags)
        }

        @PrivacyMethodProxy(
            originalClass = PackageManager::class,
            originalMethod = "getInstalledPackagesAsUser",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun getInstalledPackagesAsUser(
            manager: PackageManager,
            flags: Int,
            userId: Int
        ): List<PackageInfo> {
            doFilePrinter(
                "getInstalledPackagesAsUser",
                methodDocumentDesc = "安装包-getInstalledPackagesAsUser"
            )
            return getInstalledPackages(manager, flags);
        }

        @PrivacyMethodProxy(
            originalClass = PackageManager::class,
            originalMethod = "getInstalledApplications",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun getInstalledApplications(manager: PackageManager, flags: Int): List<ApplicationInfo> {
            doFilePrinter(
                "getInstalledApplications",
                methodDocumentDesc = "安装包-getInstalledApplications"
            )
            if (PrivacySentry.Privacy.getBuilder()
                    ?.isVisitorModel() == true || PrivacySentry.Privacy.getBuilder()
                    ?.isForbiddenAPI("getInstalledApplications") == true
            ) {
                return emptyList()
            }
            return manager.getInstalledApplications(flags)
        }

        @PrivacyMethodProxy(
            originalClass = PackageManager::class,
            originalMethod = "getInstalledApplicationsAsUser",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun getInstalledApplicationsAsUser(
            manager: PackageManager, flags: Int,
            userId: Int
        ): List<ApplicationInfo> {
            doFilePrinter(
                "getInstalledApplicationsAsUser",
                methodDocumentDesc = "安装包-getInstalledApplicationsAsUser"
            )
            return getInstalledApplications(manager, flags);
        }


        // 这个方法比较特殊，是否合规完全取决于intent参数
        // 如果指定了自己的包名，那可以认为是合规的，因为是查自己APP的AC
        // 如果没有指定包名，那就是查询了其他APP的Ac，这不合规
        // 思考，直接在SDK里拦截肯定不合适，对于业务方来说太黑盒了，如果触发bug开发会崩溃的，所以我们只打日志为业务方提供信息
        @PrivacyMethodProxy(
            originalClass = PackageManager::class,
            originalMethod = "queryIntentActivities",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun queryIntentActivities(
            manager: PackageManager,
            intent: Intent,
            flags: Int
        ): List<ResolveInfo> {
            var paramBuilder = StringBuilder()
            var legal = true
            intent?.also {
                intent?.categories?.also {
                    paramBuilder.append("-categories:").append(it.toString()).append("\n")
                }
                intent?.`package`?.also {
                    paramBuilder.append("-packageName:").append(it).append("\n")
                }
                intent?.data?.also {
                    paramBuilder.append("-data:").append(it.toString()).append("\n")
                }
                intent?.component?.packageName?.also {
                    paramBuilder.append("-packageName:").append(it).append("\n")
                }
            }

            if (paramBuilder.isEmpty()) {
                legal = false
            }

            // 不指定包名，我们认为这个查询不合法
            if (!paramBuilder.contains("packageName")) {
                legal = false
            }
            paramBuilder.append("-合法查询:${legal}").append("\n")
            doFilePrinter(
                "queryIntentActivities",
                methodDocumentDesc = "读安装列表-queryIntentActivities${paramBuilder?.toString()}"
            )
            if (PrivacySentry.Privacy.getBuilder()
                    ?.isVisitorModel() == true || PrivacySentry.Privacy.getBuilder()
                    ?.isForbiddenAPI("queryIntentActivities") == true
            ) {
                return emptyList()
            }
            return manager.queryIntentActivities(intent, flags)
        }

        @PrivacyMethodProxy(
            originalClass = PackageManager::class,
            originalMethod = "queryIntentActivityOptions",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun queryIntentActivityOptions(
            manager: PackageManager,
            caller: ComponentName?,
            specifics: Array<Intent?>?,
            intent: Intent,
            flags: Int
        ): List<ResolveInfo> {
            doFilePrinter(
                "queryIntentActivityOptions",
                methodDocumentDesc = "读安装列表-queryIntentActivityOptions"
            )
            if (PrivacySentry.Privacy.getBuilder()
                    ?.isVisitorModel() == true || PrivacySentry.Privacy.getBuilder()
                    ?.isForbiddenAPI("queryIntentActivityOptions") == true
            ) {
                return emptyList()
            }
            return manager.queryIntentActivityOptions(caller, specifics, intent, flags)
        }


        /**
         * 基站信息，需要开启定位
         */
        @JvmStatic
        @SuppressLint("MissingPermission")
        @PrivacyMethodProxy(
            originalClass = TelephonyManager::class,
            originalMethod = "getAllCellInfo",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        fun getAllCellInfo(manager: TelephonyManager): List<CellInfo>? {
            doFilePrinter("getAllCellInfo", methodDocumentDesc = "定位-基站信息")
            if (PrivacySentry.Privacy.getBuilder()
                    ?.isVisitorModel() == true || PrivacySentry.Privacy.getBuilder()
                    ?.isForbiddenAPI("getAllCellInfo") == true
            ) {
                return emptyList()
            }
            return manager.getAllCellInfo()
        }

        @PrivacyMethodProxy(
            originalClass = ClipboardManager::class,
            originalMethod = "getPrimaryClip",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun getPrimaryClip(manager: ClipboardManager): ClipData? {
            if (PrivacySentry.Privacy.getBuilder()
                    ?.isVisitorModel() == true || PrivacySentry.Privacy.getBuilder()
                    ?.isForbiddenAPI("getPrimaryClip") == true
            ) {
                return ClipData.newPlainText("Label", "")
            }
            if (!PrivacyClipBoardManager.isReadClipboardEnable()) {
                doFilePrinter("getPrimaryClip", "读取系统剪贴板关闭")
                return ClipData.newPlainText("Label", "")
            }

            doFilePrinter("getPrimaryClip", "剪贴板内容-getPrimaryClip")
            return manager.primaryClip
        }

        @PrivacyMethodProxy(
            originalClass = ClipboardManager::class,
            originalMethod = "getPrimaryClipDescription",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun getPrimaryClipDescription(manager: ClipboardManager): ClipDescription? {
            if (PrivacySentry.Privacy.getBuilder()
                    ?.isVisitorModel() == true || PrivacySentry.Privacy.getBuilder()
                    ?.isForbiddenAPI("getPrimaryClipDescription") == true
            ) {
                return ClipDescription("", arrayOf(MIMETYPE_TEXT_PLAIN))
            }

            if (!PrivacyClipBoardManager.isReadClipboardEnable()) {
                doFilePrinter("getPrimaryClipDescription", "读取系统剪贴板关闭")
                return ClipDescription("", arrayOf(MIMETYPE_TEXT_PLAIN))
            }

            doFilePrinter("getPrimaryClipDescription", "剪贴板内容-getPrimaryClipDescription")
            return manager.primaryClipDescription
        }

        @PrivacyMethodProxy(
            originalClass = ClipboardManager::class,
            originalMethod = "getText",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun getText(manager: ClipboardManager): CharSequence? {

            if (PrivacySentry.Privacy.getBuilder()
                    ?.isVisitorModel() == true || PrivacySentry.Privacy.getBuilder()
                    ?.isForbiddenAPI("getText") == true
            ) {
                return ""
            }

            if (!PrivacyClipBoardManager.isReadClipboardEnable()) {
                doFilePrinter("getText", "读取系统剪贴板关闭")
                return ""
            }
            doFilePrinter("getText", "剪贴板内容-getText")
            return manager.text
        }

        @PrivacyMethodProxy(
            originalClass = ClipboardManager::class,
            originalMethod = "setPrimaryClip",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun setPrimaryClip(manager: ClipboardManager, clip: ClipData) {
            doFilePrinter("setPrimaryClip", "设置剪贴板内容-setPrimaryClip")
            if (PrivacySentry.Privacy.getBuilder()
                    ?.isVisitorModel() == true || PrivacySentry.Privacy.getBuilder()
                    ?.isForbiddenAPI("setPrimaryClip") == true
            ) {
                return
            }
            manager.setPrimaryClip(clip)
        }

        @PrivacyMethodProxy(
            originalClass = ClipboardManager::class,
            originalMethod = "setText",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun setText(manager: ClipboardManager, clip: CharSequence) {
            doFilePrinter("setText", "设置剪贴板内容-setText")
            if (PrivacySentry.Privacy.getBuilder()
                    ?.isVisitorModel() == true || PrivacySentry.Privacy.getBuilder()
                    ?.isForbiddenAPI("setText") == true
            ) {
                return
            }
            manager.text = clip
        }

        /**
         * WIFI的SSID
         */
        @JvmStatic
        @PrivacyMethodProxy(
            originalClass = WifiInfo::class,
            originalMethod = "getSSID",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        fun getSSID(manager: WifiInfo): String? {
            if (PrivacySentry.Privacy.getBuilder()
                    ?.isVisitorModel() == true || PrivacySentry.Privacy.getBuilder()
                    ?.isForbiddenAPI("getSSID") == true
            ) {
                doFilePrinter("getSSID", "SSID", bVisitorModel = true)
                return ""
            }

            var key = "getSSID"
            doFilePrinter("getSSID", "SSID")
            return CachePrivacyManager.Manager.loadWithTimeCache(
                key,
                "getSSID",
                "",
                String::class,
                duration = CacheUtils.Utils.MINUTE * 5
            ) { manager.ssid }
            return manager.ssid
        }

        /**
         * WIFI的SSID
         */
        @JvmStatic
        @PrivacyMethodProxy(
            originalClass = WifiInfo::class,
            originalMethod = "getBSSID",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        fun getBSSID(manager: WifiInfo): String? {

            if (PrivacySentry.Privacy.getBuilder()
                    ?.isVisitorModel() == true || PrivacySentry.Privacy.getBuilder()
                    ?.isForbiddenAPI("getBSSID") == true
            ) {
                doFilePrinter("getBSSID", "getBSSID", bVisitorModel = true)
                return ""
            }

            var key = "getBSSID"
            doFilePrinter("getBSSID", "getBSSID")
            return CachePrivacyManager.Manager.loadWithTimeCache(
                key,
                "getBSSID",
                "",
                String::class,
                duration = CacheUtils.Utils.MINUTE * 5
            ) { manager.ssid }
            return manager.bssid
        }

        /**
         * WIFI扫描结果
         */
        @JvmStatic
        @PrivacyMethodProxy(
            originalClass = WifiManager::class,
            originalMethod = "getScanResults",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        fun getScanResults(manager: WifiManager): List<*> {
            if (PrivacySentry.Privacy.getBuilder()
                    ?.isVisitorModel() == true || PrivacySentry.Privacy.getBuilder()
                    ?.isForbiddenAPI("getScanResults") == true
            ) {
                doFilePrinter("getScanResults", "WIFI扫描结果", bVisitorModel = true)
                return emptyList<ScanResult>()
            }

            var key = "getScanResults"
            return CachePrivacyManager.Manager.loadWithTimeCache(
                key,
                "getScanResults",
                emptyList<ScanResult>(),
                List::class,
                duration = CacheUtils.Utils.MINUTE * 5
            ) { manager.scanResults }
        }

        /**
         * WIFI扫描结果
         */
        @JvmStatic
        @PrivacyMethodProxy(
            originalClass = WifiManager::class,
            originalMethod = "isWifiEnabled",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        fun isWifiEnabled(manager: WifiManager): Boolean {
            if (PrivacySentry.Privacy.getBuilder()
                    ?.isVisitorModel() == true || PrivacySentry.Privacy.getBuilder()
                    ?.isForbiddenAPI("isWifiEnabled") == true
            ) {
                doFilePrinter("isWifiEnabled", "读取WiFi状态", bVisitorModel = true)
                return true
            }

            var key = "isWifiEnabled"
            return CachePrivacyManager.Manager.loadWithTimeCache(
                key,
                "isWifiEnabled",
                true,
                Boolean::class,
                duration = CacheUtils.Utils.MINUTE * 5
            ) { manager.isWifiEnabled }
        }


        /**
         * DHCP信息
         */
        @JvmStatic
        @PrivacyMethodProxy(
            originalClass = WifiManager::class,
            originalMethod = "getDhcpInfo",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        fun getDhcpInfo(manager: WifiManager): DhcpInfo? {
            doFilePrinter("getDhcpInfo", "DHCP地址")
            if (PrivacySentry.Privacy.getBuilder()
                    ?.isVisitorModel() == true || PrivacySentry.Privacy.getBuilder()
                    ?.isForbiddenAPI("getDhcpInfo") == true
            ) {
                return null
            }
            return manager.getDhcpInfo()
        }

        /**
         * DHCP信息
         */
        @SuppressLint("MissingPermission")
        @JvmStatic
        @PrivacyMethodProxy(
            originalClass = WifiManager::class,
            originalMethod = "getConfiguredNetworks",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        fun getConfiguredNetworks(manager: WifiManager): List<WifiConfiguration>? {
            doFilePrinter("getConfiguredNetworks", "前台用户配置的所有网络的列表")
            if (PrivacySentry.Privacy.getBuilder()
                    ?.isVisitorModel() == true || PrivacySentry.Privacy.getBuilder()
                    ?.isForbiddenAPI("getConfiguredNetworks") == true
            ) {
                return emptyList()
            }
            return manager.getConfiguredNetworks()
        }


        /**
         * 位置信息
         */
        @JvmStatic
        @SuppressLint("MissingPermission")
        @PrivacyMethodProxy(
            originalClass = LocationManager::class,
            originalMethod = "getLastKnownLocation",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        fun getLastKnownLocation(
            manager: LocationManager, provider: String
        ): Location? {
            val key = "getLastKnownLocation"
            if (PrivacySentry.Privacy.getBuilder()
                    ?.isVisitorModel() == true || PrivacySentry.Privacy.getBuilder()
                    ?.isForbiddenAPI("getLastKnownLocation") == true
            ) {
                doFilePrinter("getLastKnownLocation", "上一次的位置信息", bVisitorModel = true)
                // 这里直接写空可能有风险
                return null
            }

            val locationStr = CachePrivacyManager.Manager.loadWithTimeCache(
                key,
                "上一次的位置信息",
                "",
                String::class,
            ) { PrivacyUtil.Util.formatLocation(manager.getLastKnownLocation(provider)) }

            var location: Location?
            locationStr.also {
                location = PrivacyUtil.Util.formatLocation(it)
            }
            return location
        }


        @SuppressLint("MissingPermission")
        @JvmStatic
        @PrivacyMethodProxy(
            originalClass = LocationManager::class,
            originalMethod = "requestLocationUpdates",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        fun requestLocationUpdates(
            manager: LocationManager, provider: String, minTime: Long, minDistance: Float,
            listener: LocationListener
        ) {
            doFilePrinter("requestLocationUpdates", "监视精细行动轨迹")
            if (PrivacySentry.Privacy.getBuilder()
                    ?.isVisitorModel() == true || PrivacySentry.Privacy.getBuilder()
                    ?.isForbiddenAPI("requestLocationUpdates") == true
            ) {
                return
            }
            manager.requestLocationUpdates(provider, minTime, minDistance, listener)
        }


        var objectMacLock = Object()
        var objectHardMacLock = Object()
        var objectSNLock = Object()
        var objectAndroidIdLock = Object()
        var objectExternalStorageDirectoryLock = Object()


        @PrivacyMethodProxy(
            originalClass = WifiInfo::class,
            originalMethod = "getMacAddress",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun getMacAddress(manager: WifiInfo): String? {
            var key = "WifiInfo-getMacAddress"

            if (PrivacySentry.Privacy.getBuilder()
                    ?.isVisitorModel() == true || PrivacySentry.Privacy.getBuilder()
                    ?.isForbiddenAPI("getMacAddress") == true
            ) {
                doFilePrinter(
                    key,
                    "mac地址-getMacAddress",
                    bVisitorModel = true
                )
                return ""
            }

            synchronized(objectMacLock) {
                return CachePrivacyManager.Manager.loadWithDiskCache(
                    key,
                    "mac地址-getMacAddress",
                    "",
                    String::class,
                    { manager.getMacAddress() },
                )
            }
        }


        @PrivacyMethodProxy(
            originalClass = NetworkInterface::class,
            originalMethod = "getHardwareAddress",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun getHardwareAddress(manager: NetworkInterface): ByteArray? {
            var key = "NetworkInterface-getHardwareAddress"

            if (PrivacySentry.Privacy.getBuilder()
                    ?.isVisitorModel() == true || PrivacySentry.Privacy.getBuilder()
                    ?.isForbiddenAPI("getHardwareAddress") == true
            ) {
                doFilePrinter(
                    key,
                    "mac地址-getHardwareAddress",
                    bVisitorModel = true
                )
                return ByteArray(1)
            }
            synchronized(objectHardMacLock) {
                return CachePrivacyManager.Manager.loadWithDiskCache(
                    key,
                    "mac地址-getHardwareAddress",
                    "",
                    String::class,
                ) { manager.hardwareAddress.toString() }.toByteArray()
            }
        }

        var objectBluetoothLock = Object()

        @PrivacyMethodProxy(
            originalClass = BluetoothAdapter::class,
            originalMethod = "getAddress",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun getAddress(manager: BluetoothAdapter): String? {
            var key = "BluetoothAdapter-getAddress"

            if (PrivacySentry.Privacy.getBuilder()
                    ?.isVisitorModel() == true || PrivacySentry.Privacy.getBuilder()
                    ?.isForbiddenAPI("getAddress") == true
            ) {
                doFilePrinter(key, "蓝牙地址-getAddress", bVisitorModel = true)
                return ""
            }
            synchronized(objectBluetoothLock) {
                return CachePrivacyManager.Manager.loadWithMemoryCache(
                    key,
                    "蓝牙地址-getAddress",
                    "",
                    String::class,
                ) { manager.address }
            }
        }


        @PrivacyMethodProxy(
            originalClass = Inet4Address::class,
            originalMethod = "getAddress",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun getAddress(manager: Inet4Address): ByteArray? {
            var key = "ip地址-getAddress"

            if (PrivacySentry.Privacy.getBuilder()
                    ?.isVisitorModel() == true || PrivacySentry.Privacy.getBuilder()
                    ?.isForbiddenAPI("getAddress") == true
            ) {
                doFilePrinter(key, "ip地址-getAddress", bVisitorModel = true)
                return ByteArray(1)
            }
            var address = manager.address
            doFilePrinter(
                key,
                "ip地址-getAddress-${manager.hostName ?: ""} , address is ${address ?: ""}"
            )
            return address
        }

        @PrivacyMethodProxy(
            originalClass = InetAddress::class,
            originalMethod = "getAddress",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun getAddress(manager: InetAddress): ByteArray? {
            var key = "ip地址-getAddress"

            if (PrivacySentry.Privacy.getBuilder()
                    ?.isVisitorModel() == true || PrivacySentry.Privacy.getBuilder()
                    ?.isForbiddenAPI("getAddress") == true
            ) {
                doFilePrinter(key, "ip地址-getAddress", bVisitorModel = true)
                return ByteArray(1)
            }
            var address = manager.address
            doFilePrinter(
                key,
                "ip地址-getAddress-${manager.hostName ?: ""} , address is ${address ?: ""} "
            )
            return address
        }

        @PrivacyMethodProxy(
            originalClass = Inet4Address::class,
            originalMethod = "getHostAddress",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun getHostAddress(manager: Inet4Address): String? {
            var key = "ip地址-getHostAddress"

            if (PrivacySentry.Privacy.getBuilder()
                    ?.isVisitorModel() == true || PrivacySentry.Privacy.getBuilder()
                    ?.isForbiddenAPI("getHostAddress") == true
            ) {
                doFilePrinter(key, "ip地址-getHostAddress", bVisitorModel = true)
                return ""
            }

            var address = manager.hostAddress
            doFilePrinter(
                key,
                "ip地址-getHostAddress-${manager.hostName ?: ""} , address is ${address ?: ""}"
            )
            return address
        }

        @PrivacyMethodProxy(
            originalClass = Inet6Address::class,
            originalMethod = "getHostAddress",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun getHostAddress(manager: Inet6Address): String? {
            var key = "ip地址-getHostAddress-inet6"

            if (PrivacySentry.Privacy.getBuilder()
                    ?.isVisitorModel() == true || PrivacySentry.Privacy.getBuilder()
                    ?.isForbiddenAPI("getHostAddress") == true
            ) {
                doFilePrinter(key, "ip地址-getHostAddress-inet6", bVisitorModel = true)
                return ""
            }

            var address = manager.hostAddress
            doFilePrinter(
                key,
                "ip地址-getHostAddress-inet6-${manager.hostName ?: ""} , address is ${address ?: ""}"
            )
            return address
        }

        @PrivacyMethodProxy(
            originalClass = InetAddress::class,
            originalMethod = "getHostAddress",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        @JvmStatic
        fun getHostAddress(manager: InetAddress): String? {
            var key = "ip地址-getHostAddress"

            if (PrivacySentry.Privacy.getBuilder()
                    ?.isVisitorModel() == true || PrivacySentry.Privacy.getBuilder()
                    ?.isForbiddenAPI("getHostAddress") == true
            ) {
                doFilePrinter(key, "ip地址-getHostAddress", bVisitorModel = true)
                return ""
            }

            var address = manager.hostAddress
            doFilePrinter(
                key,
                "ip地址-getHostAddress-${manager.hostName ?: ""} , address is ${address ?: ""}"
            )
            return address
        }

        @PrivacyMethodProxy(
            originalClass = Settings.Secure::class,
            originalMethod = "getString",
            originalOpcode = MethodInvokeOpcode.INVOKESTATIC
        )
        @JvmStatic
        fun getString(contentResolver: ContentResolver?, type: String?): String? {
            var key = "Secure-getString-$type"
            if (!"android_id".equals(type)) {
                return Settings.Secure.getString(
                    contentResolver,
                    type
                )
            }
            if (PrivacySentry.Privacy.getBuilder()
                    ?.isVisitorModel() == true || PrivacySentry.Privacy.getBuilder()
                    ?.isForbiddenAPI("getString") == true
            ) {
                doFilePrinter(
                    "getString",
                    "系统信息",
                    args = type,
                    bVisitorModel = true
                )
                return ""
            }
            synchronized(objectAndroidIdLock) {
                return CachePrivacyManager.Manager.loadWithDiskCache(
                    key,
                    "getString-系统信息",
                    "",
                    String::class,
                ) {
                    Settings.Secure.getString(
                        contentResolver,
                        type
                    )
                }
            }
        }


        @PrivacyMethodProxy(
            originalClass = Settings.System::class,
            originalMethod = "getString",
            originalOpcode = MethodInvokeOpcode.INVOKESTATIC
        )
        @JvmStatic
        fun getStringSystem(contentResolver: ContentResolver?, type: String?): String? {
            return getString(contentResolver, type)
        }

        @PrivacyMethodProxy(
            originalClass = android.os.Build::class,
            originalMethod = "getSerial",
            originalOpcode = MethodInvokeOpcode.INVOKESTATIC
        )
        @JvmStatic
        fun getSerial(): String? {
            var result = ""
            var key = "getSerial"
            if (PrivacySentry.Privacy.getBuilder()
                    ?.isVisitorModel() == true || PrivacySentry.Privacy.getBuilder()
                    ?.isForbiddenAPI("getSerial") == true
            ) {
                doFilePrinter("getSerial", "Serial", bVisitorModel = true)
                return ""
            }
            synchronized(objectSNLock) {
                return CachePrivacyManager.Manager.loadWithDiskCache(
                    key,
                    "getSerial",
                    "",
                    String::class,
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        Build.getSerial()
                    } else {
                        Build.SERIAL
                    }
                }
            }
        }

        @PrivacyMethodProxy(
            originalClass = android.os.Environment::class,
            originalMethod = "getExternalStorageDirectory",
            originalOpcode = MethodInvokeOpcode.INVOKESTATIC
        )
        @JvmStatic
        fun getExternalStorageDirectory(): File? {
            var result: File? = null
            var key = "externalStorageDirectory"
            if (PrivacySentry.Privacy.getBuilder()
                    ?.isVisitorModel() == true || PrivacySentry.Privacy.getBuilder()
                    ?.isForbiddenAPI("getExternalStorageDirectory") == true
            ) {
                doFilePrinter("getExternalStorageDirectory", key, bVisitorModel = true)
            }
            synchronized(objectExternalStorageDirectoryLock) {
                result = CachePrivacyManager.Manager.loadWithMemoryCache<File>(
                    key,
                    "getExternalStorageDirectory",
                    File(""),
                    File::class,
                ) {
                    Environment.getExternalStorageDirectory()
                }
            }
            return result
        }

        // 拦截获取系统设备，简直离谱，这个也不能重复获取
        @JvmStatic
        fun getBrand(): String? {
            PrivacyLog.i("getBrand")
            var key = "getBrand"
            return CachePrivacyManager.Manager.loadWithMemoryCache(
                key,
                "getBrand",
                "",
                String::class,
            ) {
                PrivacyLog.i("getBrand Value")
                Build.BRAND
            }
        }

        // 拦截经纬度-getLatitude
        @JvmStatic
        @PrivacyMethodProxy(
            originalClass = Location::class,
            originalMethod = "getLatitude",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        fun getLatitude(location: Location): Double {
            if (PrivacySentry.Privacy.getBuilder()
                    ?.isVisitorModel() == true || PrivacySentry.Privacy.getBuilder()
                    ?.isForbiddenAPI("getLatitude") == true
            ) {
                doFilePrinter("getLatitude", "经纬度latitude", bVisitorModel = true)
                return 0.0
            }

            val key = "getLatitude"
            doFilePrinter("getLatitude", "经纬度latitude")
            return CachePrivacyManager.Manager.loadWithTimeCache(
                key,
                "getLatitude",
                0.0,
                Double::class,
                duration = CacheUtils.Utils.MINUTE * 5
            ) {
                location.latitude
            }
        }

        // 拦截经纬度-getLongitude
        @JvmStatic
        @PrivacyMethodProxy(
            originalClass = Location::class,
            originalMethod = "getLongitude",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        fun getLongitude(location: Location): Double {
            if (PrivacySentry.Privacy.getBuilder()
                    ?.isVisitorModel() == true || PrivacySentry.Privacy.getBuilder()
                    ?.isForbiddenAPI("getLongitude") == true
            ) {
                doFilePrinter("getLongitude", "经纬度longitude", bVisitorModel = true)
                return 0.0
            }

            val key = "getLongitude"
            doFilePrinter("getLongitude", "经纬度longitude")
            return CachePrivacyManager.Manager.loadWithTimeCache(
                key,
                "getLongitude",
                0.0,
                Double::class,
                duration = CacheUtils.Utils.MINUTE * 5
            ) {
                location.longitude
            }
        }

        // 拦截网络状态-getActiveNetworkInfo
        @JvmStatic
        @PrivacyMethodProxy(
            originalClass = ConnectivityManager::class,
            originalMethod = "getActiveNetworkInfo",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        fun getActiveNetworkInfo(connectivityManager: ConnectivityManager): NetworkInfo? {
            // 不拦截，只是缓存
            // if (PrivacySentry.Privacy.getBuilder()
            //         ?.isVisitorModel() == true || PrivacySentry.Privacy.getBuilder()
            //         ?.isForbiddenAPI("getActiveNetworkInfo") == true
            // ) {
            //     doFilePrinter("getActiveNetworkInfo", "获取网络状态对象", bVisitorModel = true)
            //     return null
            // }

            // 增加缓存
            try {
                val value = CachePrivacyManager.Manager.loadWithTimeCache(
                    "getActiveNetworkInfo",
                    "获取网络状态对象",
                    "NoNetworkInfo",
                    String::class,
                    duration = CacheUtils.Utils.MINUTE * 5
                ) {
                    doFilePrinter(
                        "getActiveNetworkInfo",
                        methodDocumentDesc = "获取网络状态对象"
                    )
                    val p = connectivityManager.activeNetworkInfo
                    val byte = p?.let { ParcelableUtil.marshall(it) }
                    Base64.encodeToString(byte, 0)
                }
                val parcel = ParcelableUtil.unmarshall(Base64.decode(value, 0))
                val networkInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    NetworkInfo.CREATOR.createFromParcel(parcel)
                } else {
                    connectivityManager.activeNetworkInfo
                }
                if (networkInfo != null) {
                    PrivacyLog.i("getActiveNetworkInfo :成功从缓存获取对象")
                    return networkInfo
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            return connectivityManager.activeNetworkInfo
        }

        // 监听服务启动-startService
        @JvmStatic
        @PrivacyMethodProxy(
            originalClass = Context::class,
            originalMethod = "startService",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        fun startService(context: Context, intent: Intent): ComponentName? {
            if (PrivacySentry.Privacy.getBuilder()
                    ?.isVisitorModel() == true
            ) {
                // 判断禁用intent里是否含有指定服务名称
                val cmp = intent.component?.flattenToShortString()
                if (cmp?.let { PrivacySentry.Privacy.getBuilder()?.isForbiddenAPI(it) } == true) {
                    doFilePrinter(
                        "startService",
                        "拦截启动服务：$intent",
                        bVisitorModel = true
                    )
                    return null
                }
            }
            PrivacyLog.i("startService :监听到启动服务-->$intent")
            return context.startService(intent)
        }

        // 监听服务绑定-bindService
        @JvmStatic
        @PrivacyMethodProxy(
            originalClass = Context::class,
            originalMethod = "bindService",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        fun bindService(
            context: Context,
            intent: Intent,
            conn: ServiceConnection,
            flags: Int
        ): Boolean? {
            if (PrivacySentry.Privacy.getBuilder()
                    ?.isVisitorModel() == true
            ) {
                // 判断禁用intent里是否含有指定服务名称
                val cmp = intent.component?.flattenToShortString()
                val act = intent.action
                if (cmp?.let { PrivacySentry.Privacy.getBuilder()?.isForbiddenAPI(it) } == true
                    || act?.let { PrivacySentry.Privacy.getBuilder()?.isForbiddenAPI(it)} == true) {
                    doFilePrinter(
                        "bindService",
                        "拦截绑定服务：$intent",
                        bVisitorModel = true
                    )
                    return null
                }
            }
            PrivacyLog.i("bindService :监听到绑定服务-->$intent")
            return context.bindService(intent, conn, flags)
        }

        @JvmStatic
        @PrivacyMethodProxy(
            originalClass = Context::class,
            originalMethod = "bindService",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        fun bindService(
            context: Context,
            intent: Intent,
            flags: Int,
            executor: Executor,
            conn: ServiceConnection
        ): Boolean? {
            if (PrivacySentry.Privacy.getBuilder()
                    ?.isVisitorModel() == true
            ) {
                // 判断禁用intent里是否含有指定服务名称
                val cmp = intent.component?.flattenToShortString()
                val act = intent.action
                if (cmp?.let { PrivacySentry.Privacy.getBuilder()?.isForbiddenAPI(it) } == true
                    || act?.let { PrivacySentry.Privacy.getBuilder()?.isForbiddenAPI(it)} == true) {
                    doFilePrinter(
                        "bindService",
                        "拦截绑定服务：$intent",
                        bVisitorModel = true
                    )
                    return null
                }
            }
            PrivacyLog.i("bindService :监听到绑定服务-->$intent")
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.bindService(intent, flags, executor, conn)
            } else {
                context.bindService(intent, conn, flags)
            }
        }

        @JvmStatic
        @PrivacyMethodProxy(
            originalClass = AppCompatActivity::class,
            originalMethod = "bindService",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        fun bindService(
            context: AppCompatActivity,
            intent: Intent,
            conn: ServiceConnection,
            flags: Int
        ): Boolean? {
            if (PrivacySentry.Privacy.getBuilder()
                    ?.isVisitorModel() == true
            ) {
                // 判断禁用intent里是否含有指定服务名称
                val cmp = intent.component?.flattenToShortString()
                val act = intent.action
                if (cmp?.let { PrivacySentry.Privacy.getBuilder()?.isForbiddenAPI(it) } == true
                    || act?.let { PrivacySentry.Privacy.getBuilder()?.isForbiddenAPI(it)} == true) {
                    doFilePrinter(
                        "bindService",
                        "拦截绑定服务：$intent",
                        bVisitorModel = true
                    )
                    return null
                }
            }
            PrivacyLog.i("bindService :监听到绑定服务-->$intent")
            return context.bindService(intent, conn, flags)
        }

        @JvmStatic
        @PrivacyMethodProxy(
            originalClass = AppCompatActivity::class,
            originalMethod = "bindService",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        fun bindService(
            context: AppCompatActivity,
            intent: Intent,
            flags: Int,
            executor: Executor,
            conn: ServiceConnection
        ): Boolean? {
            if (PrivacySentry.Privacy.getBuilder()
                    ?.isVisitorModel() == true
            ) {
                // 判断禁用intent里是否含有指定服务名称
                val cmp = intent.component?.flattenToShortString()
                val act = intent.action
                if (cmp?.let { PrivacySentry.Privacy.getBuilder()?.isForbiddenAPI(it) } == true
                    || act?.let { PrivacySentry.Privacy.getBuilder()?.isForbiddenAPI(it)} == true) {
                    doFilePrinter(
                        "bindService",
                        "拦截绑定服务：$intent",
                        bVisitorModel = true
                    )
                    return null
                }
            }
            PrivacyLog.i("bindService :监听到绑定服务-->$intent")
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.bindService(intent, flags, executor, conn)
            } else {
                context.bindService(intent, conn, flags)
            }
        }

        // 拦截友盟SDK相关方法-getSystemProperty
        @JvmStatic
        @PrivacyMethodProxy(
            originalClass = UMUtils::class,
            originalMethod = "getSystemProperty",
            originalOpcode = MethodInvokeOpcode.INVOKESTATIC
        )
        fun getSystemProperty(v0: String?, v1: String?): String? {
            if (PrivacySentry.Privacy.getBuilder()
                    ?.isVisitorModel() == true || PrivacySentry.Privacy.getBuilder()
                    ?.isForbiddenAPI("getSystemProperty") == true
            ) {
                doFilePrinter(
                    "getSystemProperty",
                    "友盟SDK获取系统属性：$v0,$v1",
                    bVisitorModel = true
                )
                return v1
            }

            doFilePrinter("getSystemProperty", "友盟SDK获取系统属性")
            return UMUtils.getSystemProperty(v0, v1)
        }

        // 拦截友盟SDK相关方法-workEvent
        @JvmStatic
        @PrivacyMethodProxy(
            originalClass = CoreProtocol::class,
            originalMethod = "workEvent",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        fun workEvent(protocol: CoreProtocol, var1: Any?, var2: Int?) {
            if (PrivacySentry.Privacy.getBuilder()
                    ?.isVisitorModel() == true || PrivacySentry.Privacy.getBuilder()
                    ?.isForbiddenAPI("workEvent") == true
            ) {
                doFilePrinter("workEvent", "友盟SDK执行任务接口", bVisitorModel = true)
                return
            }

            doFilePrinter("workEvent", "友盟SDK执行任务接口调用")
            protocol.workEvent(var1, var2 ?: 0);
        }

        // 拦截友盟SDK相关方法-workEvent
        @JvmStatic
        @PrivacyMethodProxy(
            originalClass = SQLiteOpenHelper::class,
            originalMethod = "getWritableDatabase",
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL
        )
        fun getWritableDatabase(dbHelper: SQLiteOpenHelper) {
            if (PrivacySentry.Privacy.getBuilder()
                    ?.isVisitorModel() == true || PrivacySentry.Privacy.getBuilder()
                    ?.isForbiddenAPI("getWritableDatabase") == true
            ) {
                doFilePrinter("getWritableDatabase", "获取可读写的数据库", bVisitorModel = true)
                return
            }

            doFilePrinter("getWritableDatabase", "获取可读写的数据库")
            dbHelper.writableDatabase
        }
    }
}