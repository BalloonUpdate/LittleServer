data class AppConfig(
    val baseDir: File2,
    val configYaml: HashMap<String, Any>,
    val host: String?,
    val port: Int,
    val certificateFile: String,
    val certificatePass: String,
)