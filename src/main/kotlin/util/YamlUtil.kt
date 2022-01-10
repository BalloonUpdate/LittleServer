package util

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.nodes.Tag

object YamlUtil
{
    fun fromYaml(yaml: String): HashMap<String, Any>
    {
        return Yaml().load(yaml)
    }

    fun toYaml(obj: Any): String
    {
        val opt = DumperOptions()
        opt.lineBreak = DumperOptions.LineBreak.WIN
//        opt.indentWithIndicator = true
//        opt.indicatorIndent = 2

        return Yaml(opt).dumpAs(obj, null, DumperOptions.FlowStyle.BLOCK).replace(Regex("(?<=\\w:)(?=(\r|\n))"), " ")
    }
}