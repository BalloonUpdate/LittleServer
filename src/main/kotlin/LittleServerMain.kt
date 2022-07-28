import com.sun.jna.Platform
import fi.iki.elonen.NanoHTTPD
import jna.Kernel32
import org.json.JSONArray
import org.json.JSONObject
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.error.YAMLException
import util.FileObj
import util.ManifestUtil
import java.io.File
import java.net.BindException
import java.text.SimpleDateFormat
import kotlin.system.exitProcess


class LittleServerMain(
    host: String?,
    port: Int,
    val plainHttpServer: Boolean,
    val baseDir: FileObj,
    val configYaml: HashMap<String, Any>
) : NanoHTTPD(host, port) {
    private val fmt = SimpleDateFormat("YYYY-MM-dd HH:mm:ss")

    init {
        println("文件更新助手服务端单文件版-${ManifestUtil.version} (${ManifestUtil.gitCommit.substring(0, 8)})")
        println("Listening on: $host:$port")
        start(SOCKET_READ_TIMEOUT, false)
        println("启动成功! API地址: http://"+(if(host == "0.0.0.0") "127.0.0.1" else host)+":$port/index.json (从外网访问请使用对应的外网IP/域名)")

        Thread {
            if(Platform.isWindows())
                Kernel32.Ins.SetConsoleTitleA("单文件服务端 ${ManifestUtil.version}")
        }.start()
    }

    /**
     * 服务主函数
     */
    override fun serve(session: IHTTPSession): Response
    {
        val timestamp = fmt.format(System.currentTimeMillis())
        val timePoint = System.currentTimeMillis()
        val res = serve2(session)
        val timeSpent = System.currentTimeMillis() - timePoint
        val statusCode = res.status.requestStatus
        val uri = session.uri
        val ip: String = session.javaClass.getDeclaredField("remoteIp").also { it.isAccessible = true }.get(session) as String

        if (res.status != Response.Status.INTERNAL_ERROR)
            println(String.format("[ %s ] %3s | %-15s | %s (%dms)", timestamp, statusCode, ip, uri, timeSpent))

        return res
    }

    /**
     * 服务处理过程
     */
    @Suppress("NOTHING_TO_INLINE")
    inline fun serve2(session: IHTTPSession): Response
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
            if(!plainHttpServer && uri == "/index.json") // 返回index信息
            {
                val ne = LinkedHashMap<String, Any>()
                ne["update"] = "res"
                ne.putAll(configYaml)
                ne.remove("host")
                ne.remove("port")
                return ResponseHelper.buildJsonTextResponse(JSONObject(ne).toString(4))
            } else if (!plainHttpServer && dir != null && dir.exists && dir.isDirectory) { // 返回目录结构信息
                return ResponseHelper.buildJsonTextResponse(JSONArray(hashDir(dir)).toString())
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

    fun hashDir(directory: FileObj): ArrayList<AbstractSimpleFileObject>
    {
        val ds = ArrayList<AbstractSimpleFileObject>()
        if(directory.exists && directory.isDirectory)
        {
            for (file in directory)
            {
                if(file.isFile)
                    ds += SimpleFileObject(file.name, length = file.length, hash = file.sha1, modified = file.modified / 1000)
                if(file.isDirectory)
                    ds += SimpleDirectoryObject(file.name, children = hashDir(file))
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

            try {
                val configYaml = Yaml().load(configFile.content) as HashMap<String, Any>

                val host = configYaml["host"]?.run { this as String } ?: "0.0.0.0"
                val port = configYaml["port"]?.run { this as Int } ?: 8850
                val plainHttpServer = configYaml["plain-http-server"]?.run { this as Boolean } ?: false

                LittleServerMain(host, port, plainHttpServer, baseDir, configYaml)
            } catch (e: YAMLException) {
                println("配置文件读取出错(格式不正确)，位置和原因: ${e.cause?.message}")
                exitProcess(1)
            } catch (e: BindException) {
                println("端口监听失败，可能是端口冲突，原因: ${e.message}")
                exitProcess(1)
            }
        }
    }
}
