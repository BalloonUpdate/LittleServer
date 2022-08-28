import org.json.JSONArray
import org.json.JSONObject

class VirtualFile
{
    var name: String

    var length: Long
    var hash: String
    var modified: Long

    var files: MutableList<VirtualFile>

    @get:JvmName("isFile")
    val isFile get() = length != -1L

    @get:JvmName("isDirectory")
    val isDirectory get() = !isFile

    constructor(name: String, length: Long, hash: String, modified: Long)
    {
        this.name = name
        this.length = length
        this.hash = hash
        this.modified = modified
        files = mutableListOf()
    }

    constructor(name: String, files: List<VirtualFile>)
    {
        this.name = name
        this.length = -1
        this.hash = "not a file"
        this.modified = -1
        this.files = files.toMutableList()
    }

    fun getFile(relativePath: String): VirtualFile?
    {
        if (!isDirectory)
            throw NotADirectoryException(name)

        val split = relativePath.replace("\\", "/").split("/")
        var currentDir = this

        for ((index, name) in split.withIndex())
        {
            val reachEnd = index == split.size - 1
            val current = currentDir.files.firstOrNull { it.name == name } ?: return null
            if (!reachEnd) currentDir = current else return current
        }

        return null
    }

    fun removeFile(relativePath: String)
    {
        if (!isDirectory)
            throw NotADirectoryException(name)

        val split = relativePath.replace("\\", "/").split("/")
        var currentDir = this

        for ((index, name) in split.withIndex())
        {
            val reachEnd = index == split.size - 1
            val current = currentDir.files.first { it.name == name }
            if (!reachEnd) currentDir = current else currentDir.files.removeIf { it.name == name }
        }
    }

    fun toJsonObject(): JSONObject
    {
        val json = JSONObject()
        json.put("name", name)

        if (isFile)
        {
            json.put("length", length)
            json.put("hash", hash)
            json.put("modified", modified)
        } else {
            val files = JSONArray()
            for (child in this.files)
                files.put(child.toJsonObject())
            json.put("children", files)
        }

        return json
    }

//    fun asDirectory(): DirectoryData
//    {
//        if (!isDirectory)
//            throw NotADirectoryException(name)
//
//        return DirectoryData(name, files)
//    }

//    fun asFile(): FileData
//    {
//        if (!isFile)
//            throw NotAFileException(name)
//
//        return FileData(name, length, hash, modified)
//    }

    companion object {
        @JvmStatic
        fun fromJsonArray(jsonArray: JSONArray, name: String): VirtualFile
        {
            fun parseAsLong(number: Any): Long = (number as? Int)?.toLong() ?: number as Long

            fun gen(f: JSONObject): VirtualFile
            {
                val name = f["name"] as String
                return if(f.has("children")) {
                    val files = f["children"] as JSONArray
                    VirtualFile(name, files.map { gen(it as JSONObject) })
                } else {
                    val length = parseAsLong(f["length"])
                    val hash = f["hash"] as String
                    val modified = parseAsLong(f["modified"])
                    VirtualFile(name, length, hash, modified)
                }
            }

            return VirtualFile(name = name, files = jsonArray.map { gen(it as JSONObject) })
        }

        @JvmStatic
        fun fromRealFile(file: File2): VirtualFile
        {
            return if (file.isDirectory) {
                VirtualFile(file.name, files = file.files.map { fromRealFile(it) })
            } else {
                VirtualFile(file.name, length = file.length, hash = file.sha1, modified = file.modified)
            }
        }
    }

//    class FileData(val name: String, var length: Long, var hash: String, var modified: Long)

//    class DirectoryData(val name: String, var files: List<VirtualFile>)

    class NotADirectoryException(name: String) : Exception("the file named '$name' is not a directory, is a file.")

    class NotAFileException(name: String) : Exception("the file named '$name' is not a file, is a directory.")
}