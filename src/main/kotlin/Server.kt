import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import kotlin.collections.ArrayList
import kotlin.collections.LinkedHashMap


class Server(val config: AppConfig) : NanoHTTPD(config.host, config.port)
{
    private val fmt = SimpleDateFormat("YYYY-MM-dd HH:mm:ss")
    private var structureInfoCache: String? = null
    private val cacheGeneratingLock = Any()

    /**
     * 服务主函数
     */
    override fun serve(session: IHTTPSession): Response
    {
        val timestamp = fmt.format(System.currentTimeMillis())
        val timePoint = System.currentTimeMillis()
        val res: Response = serve2(session)
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
            // Remove URL arguments
            val uri = session.uri.trim().replace(File.separatorChar, '/')
            val path = if ('?' in uri) uri.substring(0, uri.indexOf('?')) else uri

            // Prohibit getting out of current directory
            if ("../" in path)
                return ResponseHelper.buildForbiddenResponse("Won't serve ../ for security reasons.")

            // 返回目录结构信息
            val dir = Regex("(?<=^/)[^/]+(?=\\.json\$)").find(path)?.run { File2(this.value) }

            // Rewrite
            if(path == "/index.json") // 返回index信息
            {
                val ne = LinkedHashMap<String, Any>()
                ne["update"] = "res"
                ne.putAll(config.configYaml)
                ne.remove("address")
                ne.remove("host")
                ne.remove("port")
                ne.remove("performance-mode")
                ne.remove("jks-certificate-file")
                ne.remove("jks-certificate-pass")
                return ResponseHelper.buildJsonTextResponse(JSONObject(ne).toString(4))
            } else if (path == "/res.json") {
                val response: Response

                if (!config.performanceMode)
                    regenDirStructureInfoCache()

                synchronized(cacheGeneratingLock) {
                    response = ResponseHelper.buildJsonTextResponse(structureInfoCache!!)
                }

                return response
            } else if (dir != null) { // 返回目录结构信息
                return ResponseHelper.buildForbiddenResponse("Directory is unable to show")
            } else if (!path.startsWith("/res/")) { // 下载文件
                return ResponseHelper.buildForbiddenResponse("File is unable to show")
            } else {
                val file = config.baseDir + path.substring(1)

                if(!file.exists)
                    return ResponseHelper.buildNotFoundResponse(path)

                if(file.isFile)
                    return ResponseHelper.buildFileResponse(file)

                return ResponseHelper.buildPlainTextResponse(path)
            }
        } catch (e: Exception) {
            return ResponseHelper.buildInternalErrorResponse(e.stackTraceToString())
        }
    }

    /**
     * 生成res目录文件结构信息并缓存
     */
    fun regenDirStructureInfoCache()
    {
        synchronized(cacheGeneratingLock) {
            structureInfoCache = JSONArray(genDirStructureInfo(config.baseDir + "res")).toString()
        }
    }

    /**
     * 生成文件结构信息
     */
    private fun genDirStructureInfo(directory: File2): List<SimpleFileDir>
    {
        fun getDirname(path: String): String? = path.lastIndexOf("/").run { if (this == -1) null else path.substring(0, this) }
        fun getBasename(path: String): String = path.lastIndexOf("/").run { if (this == -1) path else path.substring(this + 1) }

        val baseDir = config.baseDir + "res"
        val hashCache = HashCache(baseDir)
        val diCacheFile = File2("cache.json")
        val diCache = diCacheFile.run { if (config.performanceMode && exists) SimpleFileDir.fromJsonArray(JSONArray(content), "no_name") as SimpleFileDir.SimpleDirectory else null }
        val diff = diCache?.run { FileDiff(this, directory, baseDir, hashCache).compare() }

        if (diff != null)
        {
            for (f in diff.oldFiles)
            {
//                println("oldFiles: $f")
                diCache.removeFile(f)
            }

            for (f in diff.oldFolders)
            {
//                println("oldFolders: $f")
                diCache.removeFile(f)
            }

            for (f in diff.newFolders)
            {
//                println("newFolders: $f")
                val parent = getDirname(f)
                val filename = getBasename(f)

                val dir = if (parent != null) diCache.getFile(parent) as SimpleFileDir.SimpleDirectory else diCache
                dir.files += SimpleFileDir.SimpleDirectory(filename, listOf())
            }

            for (f in diff.newFiles)
            {
//                println("newFiles: $f")
                println("检测到文件变动，结构文件缓存已更新: $f")
                val parent = getDirname(f)
                val filename = getBasename(f)

                val dir = if (parent != null) diCache.getFile(parent) as SimpleFileDir.SimpleDirectory else diCache
                val file = baseDir + f
                val length = file.length
                val modified = file.modified
                val hash = hashCache.getHash(f)

                dir.files += SimpleFileDir.SimpleFile(filename, length, hash, modified)
            }
        }

        val result = diCache?.files ?: (SimpleFileDir.fromRealFile(directory) as SimpleFileDir.SimpleDirectory).files

        if (config.performanceMode && (diff == null || diff.hasDifferences()))
        {
            val jsonArray = JSONArray()

            for (j in result)
                jsonArray.put(j.toJson())

            diCacheFile.content = jsonArray.toString(4)
        }

        return result
    }
}
