class HashCache(val source: File2)
{
    private val cache = mutableMapOf<String, String>()

    fun getHash(relativePath: String): String
    {
        if (relativePath !in cache)
        {
            val file = source + relativePath
            cache[relativePath] = file.sha1
        }

        return cache[relativePath]!!
    }
}