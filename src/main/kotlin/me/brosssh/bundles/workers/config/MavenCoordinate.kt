package me.brosssh.bundles.workers.config

data class MavenCoordinate(
    val artifact: String,
    val version: String
) {
    val value: String
        get() = "$artifact:$version"

    companion object {
        fun parse(coordinate: String, path: String): MavenCoordinate {
            val parts = coordinate.split(":")
            require(parts.size == 3 && parts.all(String::isNotEmpty)) {
                "$path must use group:name:version"
            }
            return MavenCoordinate(
                artifact = parts.take(2).joinToString(":"),
                version = parts[2]
            )
        }
    }
}
