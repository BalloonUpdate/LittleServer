/**
 * 文件差异对比类
 *
 * @param current 要进行对比的目录
 * @param contrast 对比时使用的对照目录
 * @param base 基准目录，用来计算相对路径
 * @param hashCacher hash缓存对象
 */
class FileDiff(val current: VirtualFile, val contrast: File2, val base: File2, val hashCacher: HashCacher, val useSha1: Boolean)
{
    private val differences: Difference = Difference()

    /** 扫描需要下载的文件(不包括被删除的)
     * @param dir 要拿来进行对比的目录
     * @param contrast 要拿来进行对比的目录
     * @param base 基准目录，用于计算相对路径
     */
    private fun findNews(dir: VirtualFile, contrast: File2, base: File2) {
        for (c in contrast.files)
        {
            val corresponding = dir.getFile(c.name) // 此文件可能不存在

            if(corresponding == null) // 如果文件不存在的话，就不用校验了，可以直接进行下载
            {
                markAsNew(VirtualFile.fromRealFile(c, useSha1), c)
                continue
            }

            // 文件存在的话要进行进一步判断
            if(c.isDirectory) // 远程文件是一个目录
            {
                if(corresponding.isFile) // 本地文件和远程文件的文件类型对不上
                {
                    markAsOld(corresponding, contrast.relativizedBy(base))
                    markAsNew(corresponding, c)
                } else { // 本地文件和远程文件都是目录，则进行进一步判断
                    findNews(corresponding, c, base)
                }
            } else if(c.isFile) { // 远程文件是一个文件
                if(corresponding.isFile) // 本地文件和远程文件都是文件，则对比校验
                {
                    if (!compareSingleFile(corresponding, c))
                    {
                        markAsOld(corresponding, contrast.relativizedBy(base))
                        markAsNew(corresponding, c)
                    }
                } else { // 本地文件是一个目录
                    markAsOld(corresponding, contrast.relativizedBy(base))
                    markAsNew(corresponding, c)
                }
            }
        }
    }

    /** 扫描需要删除的文件
     * @param dir 要拿来进行对比的目录
     * @param contrast 要拿来进行对比的目录
     * @param base 基准目录，用于计算相对路径
     */
    private fun findOlds(dir: VirtualFile, contrast: File2, base: File2)
    {
        for (f in dir.files)
        {
            val corresponding = contrast + f.name // 尝试获取对应远程文件

            if(corresponding.exists) // 如果远程文件存在
            {
                if(f.isDirectory && corresponding.isDirectory)
                    findOlds(f, corresponding, base)
            } else { // 远程文件不存在，就直接删掉好了
                markAsOld(f, contrast.relativizedBy(base))
            }
        }
    }

    /**
     * 对比两个路径相同的文件是否一致
     */
    private fun compareSingleFile(a: VirtualFile, b: File2): Boolean
    {
        if(a.modified == b.modified)
            return true

        return if(hashCacher.getHash(b.relativizedBy(base), useSha1) != a.hash) {
            false
        } else {
            b._file.setLastModified(a.modified)
            true
        }
    }

    /**
     * 将一个文件文件或者目录标记为旧文件
     */
    private fun markAsOld(existing: VirtualFile, directory: String)
    {
        var path = directory + (if (directory.isNotEmpty()) "/" else "") + existing.name
        path = if (path.startsWith("./")) path.substring(2) else path

        if(existing.isDirectory)
        {
            for (f in existing.files)
            {
                if(f.isDirectory)
                    markAsOld(f, path)
                else {
                    var path = path + "/" + f.name
                    path = if (path.startsWith("./")) path.substring(2) else path

                    differences.oldFiles += path
                }
            }

            differences.oldFolders += path
        } else {
            differences.oldFiles += path
        }
    }

    /**
     * 将一个文件文件或者目录标记为新文件
     */
    private fun markAsNew(missing: VirtualFile, contrast: File2)
    {
        if(missing.isDirectory)
        {
            differences.newFolders += contrast.relativizedBy(base)
            for (n in missing.files)
                markAsNew(n, contrast + n.name)
        } else if (missing.isFile){
            differences.newFiles += contrast.relativizedBy(base)
        }
    }

    /**
     * 对比文件差异
     */
    fun compare(): Difference
    {
        findNews(current, contrast, base)
        findOlds(current, contrast, base)
        return differences
    }


    /**
     * 计算出来的文件差异结果
     */
    class Difference (
        val oldFolders: MutableList<String> = mutableListOf(),
        val oldFiles: MutableList<String> = mutableListOf(),
        val newFolders: MutableList<String> = mutableListOf(),
        val newFiles: MutableList<String> = mutableListOf()
    ) {
        fun hasDifferences(): Boolean
        {
            return oldFolders.size + oldFiles.size + newFolders.size + newFiles.size > 0
        }

        operator fun plusAssign(other: Difference)
        {
            oldFolders += other.oldFolders
            oldFiles += other.oldFiles
            newFolders += other.newFolders
            newFiles += other.newFiles
        }
    }
}