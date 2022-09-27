/**
 * 用于缓存文件生成的hash值的一个Map，已经生成的hash可以直接获取到，不用重复生成了，可以提升效率
 *
 * @param base 基本目录，用来配合relativePath来定位具体文件
 */
class HashCacher(val base: File2)
{
    private val cache = mutableMapOf<String, String>()

    fun getHash(relativePath: String, useSha1: Boolean): String
    {
        val key = (if (useSha1) "sha1|" else "crc32|") + relativePath

        if (key !in cache)
        {
            val file = base + relativePath
            cache[key] = if (useSha1) file.sha1 else file.crc32
            println(cache[key])
        }

        return cache[key]!!
    }
}