package com.tnibler.cryptomator_android.util


private val regexExtension = Regex("^(.+)\\.([^. ]+)$")
private val regexFileNumber = Regex("""^(.+) \(([0-9]+)\)$""")
fun alternativeName (name: String): String {
    val extensionMatch = regexExtension.matchEntire(name)
    if (extensionMatch != null) {
        val nameWithoutExtension = extensionMatch.groupValues[1]
        val extension = extensionMatch.groupValues[2]
        val numberMatch = regexFileNumber.find(nameWithoutExtension)
        if (numberMatch != null) {
            val number = numberMatch.groupValues[2].toInt()
            val nameWithoutNumber = numberMatch.groupValues[1]
            return "$nameWithoutNumber (${number + 1}).$extension"
        }
        else {
            return "$nameWithoutExtension (1).$extension"
        }
    }
    else {
        val numberMatch = regexFileNumber.find(name)
        if (numberMatch != null) {
            val number = numberMatch.groupValues[2].toInt()
            val nameWithoutNumber = numberMatch.groupValues[1]
            return "$nameWithoutNumber (${number + 1})"
        }
        else {
            return "$name (1)"
        }
    }
}
