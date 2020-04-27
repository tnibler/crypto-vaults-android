package com.tnibler.cryptomator_android.util

import androidx.documentfile.provider.DocumentFile
import com.tnibler.cryptomator_android.vault.exception.*

const val MIME_TYPE = "x-cryptomator"

fun DocumentFile.findOrCreateFile(fileName: String): DocumentFile {
    val f = findFile(fileName)
    if (f?.isDirectory == true) {
        throw RuntimeException("Directory with same name exists!")
    }
    return f ?: createFile(MIME_TYPE, fileName) ?: throw RuntimeException("failed to create file!")
}

fun DocumentFile.findOrCreateDirectory(directoryName: String): DocumentFile {
    val f = findFile(directoryName)
    if (f?.isFile == true) {
        throw RuntimeException("File with same name exists!")
    }
    return f ?: createDirectory(directoryName) ?: throw RuntimeException("failed to create directory!")
}

fun DocumentFile.findDirectory(directoryName: String): DocumentFile {
    val f = findFile(directoryName) ?: throw RuntimeException("no such directory: $directoryName in document $uri")
    if (f.isFile) {
        throw RuntimeException("File with same name as wanted directory $directoryName exists!")
    }
    return f
}

fun DocumentFile.hasChildDirectory(directoryName: String): Boolean {
    val f = findFile(directoryName)
    if (f?.isDirectory == true)
        return true
    return false
}

fun DocumentFile.hasChildFile(fileName: String): Boolean {
    val f = findFile(fileName)
    if (f?.isFile == true)
        return true
    return false
}

fun DocumentFile.checkIsDirectory() {
    if (!isDirectory) {
        throw NotDirectoryException(uri)
    }
}

fun DocumentFile.checkIsFile() {
    if (!isFile) {
        throw NotFileException()
    }
}

fun DocumentFile.checkNotExists(childName: String) {
    if (findFile(childName) != null) {
        throw ChildAlreadyExistsException()
    }
}

fun DocumentFile.getFile(vararg paths: String): DocumentFile {
    if (paths.isEmpty()) {
        throw IllegalArgumentException("Path can't be empty!")
    }
    val c = findFile(paths.first()) ?: throw FileNotFoundException(
        this,
        paths.first()
    )
    if (paths.size == 1) {
        return c
    }
    else {
        return c.getFile(*paths.drop(1).toTypedArray())
    }
}

/**
 * Creates nested directories and returns deepest one
 */
fun DocumentFile.createNestedDirectories(vararg dirs: String): DocumentFile {
    var d = this
    for (dir in dirs) {
        d = d.createDirectory(dir) ?: throw ErrorCreatingDirectory()
    }
    return d
}