import org.json.JSONArray
import org.json.JSONObject

sealed class SimpleFileDir(open var name: String)
{
    abstract fun toJson(): JSONObject

//    val isDirectory by lazy { this is SimpleDirectory }
//
//    val isFile by lazy { this is SimpleFile }
//
//    fun asDir() = this as SimpleDirectory
//
//    fun asFile() = this as SimpleFile

    class SimpleFile(
        name: String,
        var length: Long,
        var hash: String,
        var modified: Long,
    ) : SimpleFileDir(name) {
        override fun toJson(): JSONObject
        {
            val obj = JSONObject()

            obj.put("name", name)
            obj.put("length", length)
            obj.put("hash", hash)
            obj.put("modified", modified)

            return obj
        }
    }

    class SimpleDirectory(
        name: String,
        var files: List<SimpleFileDir>,
    ) : SimpleFileDir(name) {

        fun getFile(relativePath: String): SimpleFileDir?
        {
            val split = relativePath.replace("\\", "/").split("/")
            var currentDir = this

            for ((index, name) in split.withIndex())
            {
                val reachEnd = index == split.size - 1
                val current = currentDir.files.firstOrNull { it.name == name } ?: return null

                if (!reachEnd)
                    currentDir = current as SimpleDirectory
                else
                    return current
            }

            return null
        }

        fun removeFile(relativePath: String)
        {
            val split = relativePath.replace("\\", "/").split("/")
            var currentDir = this

            for ((index, name) in split.withIndex())
            {
                val reachEnd = index == split.size - 1
                val current = currentDir.files.first { it.name == name }

                if (!reachEnd) {
                    currentDir = current as SimpleDirectory
                } else {
                    currentDir.files = currentDir.files.filter { it.name != name }
                }
            }
        }

//        fun containsFile(relativePath: String): Boolean = getFile(relativePath) != null

        override fun toJson(): JSONObject
        {
            val obj = JSONObject()
            obj.put("name", name)

            val cs = mutableListOf<JSONObject>()
            for (child in files)
                cs += child.toJson()

            obj.put("files", cs)

            return obj
        }
    }

    companion object {
        fun fromJsonArray(raw: JSONArray, name: String): SimpleFileDir
        {
            fun parseAsLong(number: Any): Long = (number as? Int)?.toLong() ?: number as Long

            fun gen(f: JSONObject): SimpleFileDir
            {
                val name = f["name"] as String
                return if(f.has("files")) {
                    val files = f["files"] as JSONArray
                    SimpleDirectory(name, files.map { gen(it as JSONObject) })
                } else {
                    val length = parseAsLong(f["length"])
                    val hash = f["hash"] as String
                    val modified = parseAsLong(f["modified"])
                    SimpleFile(name, length, hash, modified)
                }
            }

            return SimpleDirectory(name = name, files = raw.map { gen(it as JSONObject) })
        }

        fun fromRealFile(file: File2): SimpleFileDir
        {
            return if (file.isDirectory) {
                SimpleDirectory(file.name, files = file.files.map { fromRealFile(it) })
            } else {
                SimpleFile(file.name, length = file.length, hash = file.sha1, modified = file.modified)
            }
        }
    }
}
