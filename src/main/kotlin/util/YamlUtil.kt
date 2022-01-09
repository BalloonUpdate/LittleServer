package util

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml

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
        return Yaml(opt).dumpAs(obj, null, DumperOptions.FlowStyle.BLOCK)
    }
}