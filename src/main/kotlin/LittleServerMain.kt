import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.error.YAMLException
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.BindException
import java.security.KeyStore
import java.text.SimpleDateFormat
import java.util.*
import javax.net.ssl.KeyManagerFactory
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.LinkedHashMap
import kotlin.system.exitProcess


class LittleServerMain(
    host: String?,
    port: Int,
    val performanceMode: Boolean,
    val baseDir: FileObj,
    val configYaml: HashMap<String, Any>
) : NanoHTTPD(host, port) {
    private val fmt = SimpleDateFormat("YYYY-MM-dd HH:mm:ss")
    private var structureInfoCache: String? = null
    val cacheGeneratingLock = Any()

    /**
     * 服务主函数
     */
    override fun serve(session: IHTTPSession): Response
    {
        val timestamp = fmt.format(System.currentTimeMillis())
        val timePoint = System.currentTimeMillis()
        val res: NanoHTTPD.Response
        synchronized(cacheGeneratingLock) { res = serve2(session) }
        val timeSpent = System.currentTimeMillis() - timePoint
        val statusCode = res.status.requestStatus
        val uri = session.uri
        val ip: String = session.javaClass.getDeclaredField("remoteIp").also { it.isAccessible = true }.get(session) as String

        println(String.format("[ %s ] %3s | %-15s | %s (%dms)", timestamp, statusCode, ip, uri, timeSpent))

        return res
    }

    /**
     * 服务具体处理过程
     */
    fun serve2(session: IHTTPSession): Response
    {
        try {
            var uri = session.uri

            // Remove URL arguments
            uri = uri.trim().replace(File.separatorChar, '/')
            uri = if ('?' in uri) uri.substring(0, uri.indexOf('?')) else uri

            // Prohibit getting out of current directory
            if ("../" in uri)
                return ResponseHelper.buildForbiddenResponse("Won't serve ../ for security reasons.")

            // 返回目录结构信息
            val regex = Regex("(?<=^/)[^/]+(?=\\.json\$)")
            val dir = if(regex.find(uri) != null) FileObj(regex.find(uri)!!.value) else null

            // Rewrite
            if(uri == "/index.json") // 返回index信息
            {
                val ne = LinkedHashMap<String, Any>()
                ne["update"] = "res"
                ne.putAll(configYaml)
                ne.remove("address")
                ne.remove("host")
                ne.remove("port")
                ne.remove("performance-mode")
                ne.remove("jks-certificate-file")
                ne.remove("jks-certificate-pass")
                return ResponseHelper.buildJsonTextResponse(JSONObject(ne).toString(4))
            } else if (performanceMode && uri == "/res.json") {
                return ResponseHelper.buildJsonTextResponse(structureInfoCache!!)
            } else if (dir != null && dir.exists && dir.isDirectory) { // 返回目录结构信息
                return ResponseHelper.buildJsonTextResponse(JSONArray(generateDirectoryStructure(dir)).toString())
            } else { // 下载文件
                val file = baseDir + uri.substring(1)

                if(!file.exists)
                    return ResponseHelper.buildNotFoundResponse(uri)

                if(file.isDirectory)
                    return ResponseHelper.buildForbiddenResponse("Directory is unable to show")

                if(file.isFile)
                    return ResponseHelper.buildFileResponse(file)

                return ResponseHelper.buildPlainTextResponse(uri)
            }
        } catch (e: Exception) {
//            e.printStackTrace()
            return ResponseHelper.buildInternalErrorResponse(e.stackTraceToString())
        }
    }

    fun generateDirectoryStructure(directory: FileObj): ArrayList<AbstractSimpleFileObject>
    {
        val ds = ArrayList<AbstractSimpleFileObject>()
        if(directory.exists && directory.isDirectory)
        {
            for (file in directory)
            {
                if(file.isFile)
                    ds += SimpleFileObject(file.name, length = file.length, hash = file.sha1, modified = file.modified / 1000)
                if(file.isDirectory)
                    ds += SimpleDirectoryObject(file.name, children = generateDirectoryStructure(file))
            }
        }
        return ds
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val baseDir = FileObj(System.getProperty("user.dir"))
            val configFile = baseDir + "config.yml"

            if(!baseDir.exists)
            {
                println("找不到工作目录: ${baseDir.path}")
                exitProcess(1)
            }

            if(!configFile.exists)
            {
                println("找不到配置文件: ${configFile.path}")
                exitProcess(1)
            }

            val server: LittleServerMain
            try {
                val configYaml = Yaml().load(configFile.content) as HashMap<String, Any>

                val host = configYaml["host"]?.run { this as String } ?: "0.0.0.0"
                val port = configYaml["port"]?.run { this as Int } ?: 8850
                val performanceMode = configYaml["performance-mode"]?.run { this as Boolean } ?: false
                val certificateFile = configYaml["jks-certificate-file"]?.run { this as String } ?: ""
                val certificatePass = configYaml["jks-certificate-pass"]?.run { this as String } ?: ""

                server = LittleServerMain(host, port, performanceMode, baseDir, configYaml)

                if (certificateFile.isNotEmpty() && certificatePass.isNotEmpty())
                {
                    if (FileObj(certificateFile).exists)
                    {
                        val keystore = KeyStore.getInstance(KeyStore.getDefaultType())
                        val keystoreStream = FileInputStream(certificateFile)

                        try {
                            keystore.load(keystoreStream, certificatePass.toCharArray())
                        } catch (e: IOException) {
                            println("SSL证书密码不正确")
                            exitProcess(1)
                        }

                        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
                        keyManagerFactory.init(keystore, certificatePass.toCharArray())
                        val factory = makeSSLSocketFactory(keystore, keyManagerFactory)
                        server.makeSecure(factory, null)
                        println("SSL证书已加载")
                    } else {
                        println("SSL证书文件找不到: $certificateFile")
                        exitProcess(1)
                    }
                }

                server.start(SOCKET_READ_TIMEOUT, false)
                println("Listening on: $host:$port")
                println("启动成功! API地址: http://"+(if(host == "0.0.0.0") "127.0.0.1" else host)+":$port/index.json (从外网访问请使用对应的外网IP/域名)")

                if (performanceMode)
                {
                    println("高性能模式已经开启，正在生成res目录的缓存...")
                    server.structureInfoCache = JSONArray(server.generateDirectoryStructure(baseDir + "res")).toString()
                    println("缓存生成完毕，如果需要刷新缓存需要重启程序或者输入reload")
                }

                println()
                println("使用提示1：更新规则和res目录下的文件均需要在程序关闭时修改，在运行时修改是不会生效的！")
                println("使用提示2：显示的所有报错信息都不用管，直接忽略就好！")
                println("使用提示3：输入指令stop或者s可以退出程序，输入reload或者r可以重新生成res目录的缓存")
            } catch (e: YAMLException) {
                println("配置文件读取出错(格式不正确)，位置和原因: ${e.cause?.message}")
                exitProcess(1)
            } catch (e: BindException) {
                println("端口监听失败，可能是端口冲突，原因: ${e.message}")
                exitProcess(1)
            }

            // 读取控制台输入
            val scanner = Scanner(System.`in`)
            while (true)
            {
                val line = scanner.nextLine()
                if (line == "stop" || line == "s")
                {
                    exitProcess(1)
                } else if (line == "reload" || line == "r") {
                    synchronized(server.cacheGeneratingLock) {
                        println("正在重新生成res目录的缓存...")
                        server.structureInfoCache = JSONArray(server.generateDirectoryStructure(baseDir + "res")).toString()
                        println("缓存生成完毕")
                    }
                }

            }
        }
    }
}
