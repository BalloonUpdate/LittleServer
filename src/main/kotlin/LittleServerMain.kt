import com.esotericsoftware.yamlbeans.YamlException
import com.sun.jna.Platform
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.SimpleWebServer
import jna.Kernel32
import util.FileObj
import util.ManifestUtil
import util.YamlUtil
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.BindException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.system.exitProcess


class LittleServerMain(
    host: String?,
    port: Int,
    val baseDir: FileObj,
    val configYaml: LinkedHashMap<String, Any>
) : NanoHTTPD(host, port) {
    private val fmt = SimpleDateFormat("YYYY-MM-dd HH:mm:ss")

    init {
        println("正在启动文件更新助手服务端单文件版v${ManifestUtil.version}-${ManifestUtil.gitCommit}")
        println("Listening on: $host:$port")

        start(SOCKET_READ_TIMEOUT, false)

        println("API地址: http://"+(if(host == "0.0.0.0") "127.0.0.1" else host)+":$port/index.yml (从外网访问请使用对应的外网IP/域名)")
        println("启动成功!")

        Thread {
            if(Platform.isWindows())
                Kernel32.Ins.SetConsoleTitleA("文件更新助手服务端单文件版v${ManifestUtil.version}")
        }.start()
    }

    override fun serve(session: IHTTPSession): Response
    {
        val timestamp = fmt.format(System.currentTimeMillis())
        val timePoint = System.currentTimeMillis()
        val res = serve2(session)
        val timeSpent = System.currentTimeMillis() - timePoint
        val statusCode = res.status.requestStatus
        val uri = session.uri
        val ip: String = session.javaClass.getDeclaredField("remoteIp").also { it.isAccessible = true }.get(session) as String

        println(String.format("[ %s ] %3s | %-15s | %s (%dms)", timestamp, statusCode, ip, uri, timeSpent))
        return res
    }

    fun serve2(session: IHTTPSession): Response
    {
        try {
            var uri = session.uri

            // Remove URL arguments
            uri = uri.trim().replace(File.separatorChar, '/')
            uri = if ('?' in uri) uri.substring(0, uri.indexOf('?')) else uri

            // Prohibit getting out of current directory
            if ("../" in uri) {
                return getForbiddenResponse("Won't serve ../ for security reasons.")
            }

            // 返回目录结构信息
            val regex = Regex("(?<=^/)[^/]+(?=\\.yml\$)")
            val dir = if(regex.find(uri) != null) FileObj(regex.find(uri)!!.value) else null

            // Rewrite
            if(uri == "/index.yml") // 返回index信息
            {
                val ne = LinkedHashMap<String, Any>()
                ne["update"] = "res"
                ne.putAll(configYaml)
                ne.remove("host")
                ne.remove("port")
                return getYamlResponse(ne)
            } else if (dir != null && dir.exists && dir.isDirectory) { // 返回目录结构信息
                return getYamlResponse(hashDir(dir))
            } else { // 下载文件
                val file = baseDir + uri.substring(1)

                if(!file.exists)
                    return getNotFoundResponse(uri)

                if(file.isDirectory)
                    return getForbiddenResponse("Directory is unable to show")

                if(file.isFile)
                    return getFileResponse(file)

                return getPlainTextResponse(uri)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return getInternalErrorResponse(e.stackTraceToString())
        }
    }

    fun getFileResponse(file: FileObj): Response
    {
        return try {
            newFixedLengthResponse(
                Response.Status.OK,
                "application/octet-stream",
                FileInputStream(file._file),
                file.length.toInt().toLong()
            ).also {
                it.addHeader("Content-Length", file.length.toString())
            }
        } catch (e: IOException) {
            getForbiddenResponse("Reading file failed.")
        }
    }

    fun getPlainTextResponse(text: String): Response {
        return newFixedLengthResponse(
            Response.Status.OK,
            MIME_PLAINTEXT,
            text
        )
    }

    fun getYamlResponse(yaml: Any): Response {
        return newFixedLengthResponse(
            Response.Status.OK,
            MIME_PLAINTEXT,
            reindent(YamlUtil.toYaml(yaml).replace(Regex("!.+\\r?\\n *(?= ?\\w)"), ""))// 去掉Yaml类型标记
        )
    }

    fun reindent(str: String): String
    {
        fun countSpace(text: String, cb: (count: Int, char: Char) -> Unit)
        {
            var stopChar: Char = ' '
            var count = 0
            for (c in text)
                if(c == ' ') {
                    count += 1
                } else {
                    stopChar = c
                    break
                }
            cb(count, stopChar)
        }

        val sb = StringBuffer()
        for (line in str.split("\n"))
        {
            countSpace(line) { count, char ->
                if(count > 0 && count % 2 == 1)
                {
                    if(char=='-')
                        sb.append(" $line")
                    else
                        sb.append(line.substring(1))
                } else {
                    sb.append(line)
                }
            }

        }
        return sb.toString()
    }

    fun getForbiddenResponse(s: String): Response
    {
        return newFixedLengthResponse(
            Response.Status.FORBIDDEN,
            MIME_PLAINTEXT,
            "FORBIDDEN: $s"
        )
    }

    fun getInternalErrorResponse(s: String): Response
    {
        return newFixedLengthResponse(
            Response.Status.INTERNAL_ERROR,
            MIME_PLAINTEXT,
            "INTERNAL ERROR: $s"
        )
    }

    fun getNotFoundResponse(path: String): Response
    {
        return newFixedLengthResponse(
            Response.Status.NOT_FOUND,
            MIME_PLAINTEXT,
            "Error 404, file not found: $path"
        )
    }

    fun hashDir(directory: FileObj): ArrayList<FileStructure>
    {
        val ds = ArrayList<FileStructure>()
        if(directory.exists && directory.isDirectory)
        {
            for (file in directory)
            {
                if(file.isFile)
                    ds += FileStructure(file.name, length = file.length, hash = file.sha1)
                if(file.isDirectory)
                    ds += FileStructure(file.name, children = hashDir(file))
            }
        }
        return ds
    }

    data class FileStructure(
        var name: String = "",
        var length: Long = -1,
        var hash: String = "",
        var children: ArrayList<FileStructure>? = null
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            var baseDir = FileObj(System.getProperty("user.dir"))
            var configFile = baseDir + "config.yml"

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
                val configYaml = YamlUtil.fromYaml<LinkedHashMap<String, Any>>(configFile.content)

                val host = configYaml["host"]?.run { this as String } ?: "0.0.0.0"
                val port = configYaml["port"]?.run { Integer.valueOf(this as String) } ?: 8850

                LittleServerMain(host, port, baseDir, configYaml)
            } catch (e: YamlException) {
                println("配置文件读取出错(格式不正确)，位置和原因: ${e.cause?.message}")
                exitProcess(1)
            } catch (e: BindException) {
                println("端口监听失败，可能是端口冲突，原因: ${e.message}")
                exitProcess(1)
            }
        }
    }
}
