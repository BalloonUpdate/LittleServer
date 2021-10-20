package util

import java.io.FileNotFoundException
import java.net.URLDecoder
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.Manifest

object ManifestUtil
{
    val version: String get() = manifest["Application-Version"]?.run {
        if(this[0]=='v') this.substring(1) else this
    } ?: "0.0.0"
    val author: String get() = manifest["Author"] ?: "<no-author>"
    val website: String get() = manifest["Website"] ?: "<no-website>"
    val gitCommit: String get() = manifest["Git-Commit"] ?: "<development>"
    val compileTime: String get() = manifest["Compile-Time"] ?: "<no compile time>"
    val compileTimeMs: Long get() = manifest["Compile-Time-Ms"]?.toLong() ?: 0L

    /**
     * 读取版本信息（程序打包成Jar后才有效）
     * @return Application版本号，如果为打包成Jar则返回null
     */
    val manifest: Map<String, String> get()
    {
        return try {
            (originManifest as Map<Attributes.Name, String>)
                .filterValues { it.isNotEmpty() }.mapKeys { it.key.toString() }
        } catch (e: FileNotFoundException) {
            mapOf()
        }
    }

    val originManifest: Attributes get()
    {
        if(!isPackaged)
            throw FileNotFoundException("This Jar has not been packaged yet")

        JarFile(jarFile.path).use { jar ->
            jar.getInputStream(jar.getJarEntry("META-INF/MANIFEST.MF")).use {
                return Manifest(it).mainAttributes
            }
        }
    }

    /**
     * 程序是否被打包
     */
    @JvmStatic
    val isPackaged: Boolean
        get() = javaClass.getResource("").protocol != "file"

    /**
     * 获取当前Jar文件路径（仅打包后有效）
     */
    @JvmStatic
    val jarFile: FileObj
        get() = FileObj(URLDecoder.decode(this.javaClass.protectionDomain.codeSource.location.file, "UTF-8"))
}