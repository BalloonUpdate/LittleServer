package util

import LittleServerMain
import com.esotericsoftware.yamlbeans.YamlReader
import com.esotericsoftware.yamlbeans.YamlWriter
import java.io.ByteArrayOutputStream
import java.io.PrintWriter

object YamlUtil
{
    fun <T> fromYaml(yaml: String): T
    {
        val reader = YamlReader(yaml)
        return reader.read().also { reader.close() } as T
    }

    fun toYaml(obj: Any): String
    {
        val buf = ByteArrayOutputStream()
        val writer = YamlWriter(PrintWriter(buf))
        writer.config.setClassTag("FileObject", LittleServerMain.FileStructure::class.java)
//        writer.config.setPropertyElementType(LittleServerMain.FileStructure::class.java, "children", LittleServerMain.FileStructure::class.java)
        writer.write(obj)
        writer.close()
        return buf.toByteArray().decodeToString()
    }
}