/**
 * 用于缓存文件生成的hash值的一个Map，已经生成的hash可以直接获取到，不用重复生成了，可以提升效率
 *
 * @param base 基本目录，用来配合relativePath来定位具体文件
 */
class HashCacher(val base: File2)
{
    private val cache = mutableMapOf<String, String>()

    fun getHash(relativePath: String): String
    {
        if (relativePath !in cache)
        {
            val file = base + relativePath
            cache[relativePath] = file.sha1
        }

        return cache[relativePath]!!
    }
}