data class AppConfig(
    val baseDir: File2,
    val configYaml: HashMap<String, Any>,
    val host: String?,
    val port: Int,
    val performanceMode: Boolean,
    val certificateFile: String,
    val certificatePass: String,
)