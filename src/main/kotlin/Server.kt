import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import kotlin.collections.LinkedHashMap


class Server(val config: AppConfig) : NanoHTTPD(config.host, config.port)
{
    private val fmt = SimpleDateFormat("YYYY-MM-dd HH:mm:ss")
    private var structureInfoCache: String? = null

    /**
     * 服务主函数
     */
    override fun serve(session: IHTTPSession): Response
    {
        val timestamp = fmt.format(System.currentTimeMillis())
        val start = System.currentTimeMillis()
        val res: Response = handleRequest(session)
        val elapsed = System.currentTimeMillis() - start
        val statusCode = res.status.requestStatus
        val uri = session.uri
        val ip: String = session.javaClass.getDeclaredField("remoteIp").also { it.isAccessible = true }.get(session) as String

        println(String.format("[ %s ] %3s | %-15s | %s (%dms)", timestamp, statusCode, ip, uri, elapsed))

        return res
    }

    /**
     * 服务具体处理过程
     */
    fun handleRequest(session: IHTTPSession): Response
    {
        try {
            // Remove URL arguments
            val uri = session.uri.trim().replace(File.separatorChar, '/')
            val path = if ('?' in uri) uri.substring(0, uri.indexOf('?')) else uri

            // Prohibit getting out of current directory
            if ("../" in path)
                return ResponseHelper.buildForbiddenResponse("Won't serve ../ for security reasons.")

            // if request on a directory
            val dir = Regex("(?<=^/)[^/]+(?=\\.json\$)").find(path)?.run { File2(this.value) }

            // Rewrite
            if(path == "/index.json") // 返回index信息
            {
                val ne = LinkedHashMap<String, Any>()
                ne["update"] = "res"
                ne.putAll(config.configYaml.filter { it.key == "common_mode" || it.key == "once_mode" })
                return ResponseHelper.buildJsonTextResponse(JSONObject(ne).toString(4))
            } else if (path == "/res.json") {
                regenResCache()
                return ResponseHelper.buildJsonTextResponse(structureInfoCache!!)
            } else if (dir != null) { // 禁止访问任何目录
                return ResponseHelper.buildForbiddenResponse("Directory is unable to show")
            } else if (!path.startsWith("/res/")) { // 不能访问res目录以外的文件
                return ResponseHelper.buildForbiddenResponse("File is unable to show")
            } else {
                // 下载res里的文件

                val file = config.baseDir + path.substring(1)

                if(!file.exists)
                    return ResponseHelper.buildNotFoundResponse(path)

                if(file.isFile)
                    return ResponseHelper.buildFileResponse(file)

                // 100%不会执行到这里
                return ResponseHelper.buildPlainTextResponse(path)
            }
        } catch (e: Exception) {
            return ResponseHelper.buildInternalErrorResponse(e.stackTraceToString())
        }
    }

    /**
     * 生成res目录缓存
     */
    private fun regenResCache()
    {
        structureInfoCache = JSONArray().also { j -> genCache(config.baseDir + "res").forEach { j.put(it.toJsonObject()) } }.toString()
    }

    /**
     * 生成文件结构信息
     */
    private fun genCache(directory: File2): List<VirtualFile>
    {
        fun getDirname(path: String): String? = path.lastIndexOf("/").run { if (this == -1) null else path.substring(0, this) }
        fun getBasename(path: String): String = path.lastIndexOf("/").run { if (this == -1) path else path.substring(this + 1) }

        val baseDir = config.baseDir + "res"
        val hashCacher = HashCacher(baseDir)
        val cacheFile = File2("cache.json")
        val cache = cacheFile.run { if (exists) VirtualFile.fromJsonArray(JSONArray(content), "no_name") else null }
        val diff = cache?.run { FileDiff(this, directory, baseDir, hashCacher).compare() }

        // 更新res.json缓存
        if (diff?.hasDifferences() == true)
        {
            for (f in diff.oldFiles)
            {
                cache.removeFile(f)
            }

            for (f in diff.oldFolders)
            {
                cache.removeFile(f)
            }

            for (f in diff.newFolders)
            {
                val parent = getDirname(f)
                val filename = getBasename(f)

                val dir = if (parent != null) cache.getFile(parent)!! else cache
                dir.files += VirtualFile(filename, listOf())
            }

            for (f in diff.newFiles)
            {
                println("updated: $f")
                val parent = getDirname(f)
                val filename = getBasename(f)

                val dir = if (parent != null) cache.getFile(parent)!! else cache
                val file = baseDir + f
                val length = file.length
                val modified = file.modified
                val hash = hashCacher.getHash(f)

                dir.files += VirtualFile(filename, length, hash, modified)
            }
        }

        val result = cache?.files ?: VirtualFile.fromRealFile(directory).files

        // 落盘
        if (diff == null || diff.hasDifferences())
            cacheFile.content = JSONArray().also { result.forEach { j -> it.put(j.toJsonObject()) } }.toString(4)

        return result
    }
}
