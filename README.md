## 如何使用

```
    1. 在根目录的build.gralde下添加
	allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
	
	buildscript {
	     dependencies {
	         // 添加插件依赖
	         classpath 'com.github.allenymt.PrivacySentry:plugin-sentry:1.2.3'
	     }
	}
	
	allprojects {
        repositories {
            maven { url 'https://jitpack.io' }
        }
    }
```



```
    2. 在项目中的build.gralde下添加
        // 在主项目里添加插件依赖
        apply plugin: 'privacy-sentry-plugin'
        
        dependencies {
            // aar依赖
            def privacyVersion = "1.2.3"
            implementation "com.github.allenymt.PrivacySentry:hook-sentry:$privacyVersion"
            implementation "com.github.allenymt.PrivacySentry:privacy-annotation:$privacyVersion"
	        //如果不想使用库中本身的代理方法，可以不引入这个aar，自己实现
	        //也可以引入，个别方法在自己的类中重写即可
            implementation "com.github.allenymt.PrivacySentry:privacy-proxy:$privacyVersion"
            // 1.2.3 新增类替换，主要是为了hook构造函数的参数
            implementation "com.github.allenymt.PrivacySentry:privacy-replace:$privacyVersion"
        }
        
        // 黑名单配置，可以设置这部分包名不会被修改字节码
        // 项目里如果有引入高德地图，先加黑 blackList = ["com.loc","com.amap.api"], asm的版本有冲突
        // 如果需要生成静态扫描文件， 默认名是replace.json
        privacy {
            blackList = []
            replaceFileName = "replace.json"
	        // 开启hook反射
    	    hookReflex = true
    	    // debug编译默认开启，支持关闭，感谢run的pr
    	    debugEnable = true
    	    // 开启hook 替换类，目前支持file
            hookConstructor = true
        }

```

```
    初始化方法最好在attachBaseContext中第一个调用！！！
```

```
    完成功能的初始化
    PrivacySentryBuilder builder = new PrivacySentryBuilder()
                        // 自定义文件结果的输出名
                        .configResultFileName("buyer_privacy")
                        // 配置游客模式，true打开游客模式，false关闭游客模式
                        .configVisitorModel(false)
                        // 配置写入文件日志 , 线上包这个开关不要打开！！！！，true打开文件输入，false关闭文件输入
                        .enableFileResult(true)
                        // 持续写入文件30分钟
                        .configWatchTime(30 * 60 * 1000)
                        // 文件输出后的回调
                        .configResultCallBack(new PrivacyResultCallBack() {

                            @Override
                            public void onResultCallBack(@NonNull String s) {

                            }
                        });
    // 添加默认结果输出，包含log输出和文件输出
    PrivacySentry.Privacy.INSTANCE.init(application, builder);
```

```
    在隐私协议确认的时候调用，这一步非常重要！，一定要加
    kotlin:PrivacySentry.Privacy.updatePrivacyShow()
    java:PrivacySentry.Privacy.INSTANCE.updatePrivacyShow();
```

```
    关闭游客模式
    PrivacySentry.Privacy.INSTANCE.closeVisitorModel();
```
```
    支持自定义配置hook函数
    /**
 * @author yulun
 * @since 2022-01-13 17:57
 * 主要是两个注解PrivacyClassProxy和PrivacyMethodProxy，PrivacyClassProxy代表要解析的类，PrivacyMethodProxy代表要hook的方法配置
 */
@Keep
open class PrivacyProxyResolver {
     
    // kotlin里实际解析的是这个PrivacyProxyCall$Proxy 内部类
    @PrivacyClassProxy
    @Keep
    object Proxy {
 
        // 查询
        @SuppressLint("MissingPermission")
        @PrivacyMethodProxy(
            originalClass = ContentResolver::class,   // hook的方法所在的类名
            originalMethod = "query",   // hook的方法名
            originalOpcode = MethodInvokeOpcode.INVOKEVIRTUAL //hook的方法调用，一般是静态调用和实例调用
        )
        @JvmStatic
        fun query(
            contentResolver: ContentResolver?, //实例调用的方法需要把声明调用对象，我们默认把对象参数放在第一位
            uri: Uri,
            projection: Array<String?>?, selection: String?,
            selectionArgs: Array<String?>?, sortOrder: String?
        ): Cursor? {
            doFilePrinter("query", "查询服务: ${uriToLog(uri)}") // 输入日志到文件
            if (PrivacySentry.Privacy.getBuilder()?.isVisitorModel() == true) { //游客模式开关
                return null
            }
            return contentResolver?.query(uri, projection, selection, selectionArgs, sortOrder)
        }
  
        @RequiresApi(Build.VERSION_CODES.O)
        @PrivacyMethodProxy(
            originalClass = android.os.Build::class,
            originalMethod = "getSerial",
            originalOpcode = MethodInvokeOpcode.INVOKESTATIC //静态调用
        )
        @JvmStatic
        fun getSerial(): String? {
            var result = ""
            try {
                doFilePrinter("getSerial", "读取Serial")
                if (PrivacySentry.Privacy.getBuilder()?.isVisitorModel() == true) {
                return ""
                }
            result = Build.getSerial()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        return result
        }
    }
}

```

```
    支持多进程，多进程产出的文件名前缀默认增加进程名
```


```
    如何配置替换一个类
    可以参考源码中PrivacyFile的配置，使用PrivacyClassReplace注解，originClass代表你要替换的类，注意要继承originClass的所有构造函数
    可以配置 hookConstructor = false关闭这个功能
/**
 * @author yulun
 * @since 2022-11-18 15:01
 * 代理File的构造方法，如果是自定义的file类，需要业务方单独配置自行处理
 */
@PrivacyClassReplace(originClass = File.class)
public class PrivacyFile extends File {

    public PrivacyFile(@NonNull String pathname) {
        super(pathname);
        record(pathname);
    }

    public PrivacyFile(@Nullable String parent, @NonNull String child) {
        super(parent, child);
        record(parent + child);
    }

    public PrivacyFile(@Nullable File parent, @NonNull String child) {
        super(parent, child);
        record(parent.getPath() + child);
    }

    public PrivacyFile(@NonNull URI uri) {
        super(uri);
        record(uri.toString());
    }

    private void record(String path) {
        PrivacyProxyUtil.Util.INSTANCE.doFilePrinter("PrivacyFile", "访问文件", "path is " + path, PrivacySentry.Privacy.INSTANCE.getBuilder().isVisitorModel(), false);
    }
}


```


## 隐私方法调用结果产出
-     支持hook调用堆栈至文件，默认的时间为1分钟，支持自定义设置时间。
-     排查结果可参考目录下的demo_result.xls，排查结果支持两个维度查看，第一是结合隐私协议的展示时机和敏感方法的调用时机，第二是统计所有敏感函数的调用次数
-     排查结果可观察日志，结果文件会在 /storage/emulated/0/Android/data/yourPackgeName/cache/xx.xls，需要手动执行下adb pull

## 基本原理
-     编译期注解+hook方案，第一个transform收集需要拦截的敏感函数，第二个transform替换敏感函数，运行期收集日志，同时支持游客模式
-     为什么不用xposed等框架？ 因为想做本地自动化定期排查，第三方hook框架外部依赖性太大
-     为什么不搞基于lint的排查方式？ 工信部对于运行期 敏感函数的调用时机和次数都有限制，代码扫描解决不了这些问题


## 支持的hook函数列表

支持hook以下功能函数：

- 支持敏感字段缓存(磁盘缓存、带有时间限制的磁盘缓存、内存缓存)

- hook替换类 (构造函数)

- 当前运行进程和任务

- 系统剪贴板服务

- 读取设备应用列表

- 读取 Android SN(Serial,包括方法和变量)，系统设备号

- 读写联系人、日历、本机号码

- 获取定位、基站信息、wifi信息

- Mac 地址、IP 地址

- 读取 IMEI(DeviceId)、MEID、IMSI、ADID(AndroidID)

- 手机可用传感器,传感器注册，传感器列表

- 权限请求

