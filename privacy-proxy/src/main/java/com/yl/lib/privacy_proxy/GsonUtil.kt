package com.yl.lib.privacy_proxy

import com.google.gson.Gson

/**
 * @Description:gson解析工具类
 * @Author: Eric
 * @Email: yuanshuaiding@163.com
 * @CreateDate: 2022/12/1 10:38
 * @Version: 1.0
 */
internal class GsonUtils private constructor() {
    init {
        throw UnsupportedOperationException("This class does not support instantiation.")
    }

    companion object {
        fun <T> jsonToClass(json: String?, classOfT: Class<T>): T? {
            return try {
                Gson().fromJson(json, classOfT)
            } catch (var4: Exception) {
                println("json to class【" + classOfT + "】 解析失败  " + var4.message)
                try {
                    com.alibaba.fastjson2.JSON.parseObject(json, classOfT)
                } catch (fe: Exception) {
                    println("json to class【" + classOfT + "】 fastjson2解析失败  " + fe.message)
                    null
                }
            }
        }

        fun toJson(obj: Any?): String {
            var jsonStr = ""
            try {
                jsonStr = Gson().toJson(obj)
            } catch (var3: Exception) {
                var3.printStackTrace()
            }
            return jsonStr
        }
    }
}