package com.grokapp.googlebookos.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import com.grokapp.googlebookos.data.model.FsDirectoryItem
import java.io.File
import java.net.URLDecoder

object StorageHelper {

    fun getExternalStorageRoot(): String {
        val emulated10 = File("/storage/emulated/10")
        if (emulated10.exists()) {
            return "/storage/emulated/10"
        }
        return Environment.getExternalStorageDirectory().absolutePath
    }

    fun getDefaultCodesDir(): String {
        val root = getExternalStorageRoot()
        val codes = File(root, "Codes")
        if (!codes.exists()) {
            codes.mkdirs()
        }
        return codes.absolutePath
    }

    /**
     * Resolves an Android SAF Tree Uri (ACTION_OPEN_DOCUMENT_TREE) to a POSIX file path.
     * Handles primary storage, user profiles (e.g. /storage/emulated/10), and external storage cards.
     */
    fun resolveTreeUriToPath(context: Context, uri: Uri): String? {
        try {
            val docId = DocumentsContract.getTreeDocumentId(uri) ?: return null
            val decodedDocId = URLDecoder.decode(docId, "UTF-8")
            val baseExt = getExternalStorageRoot()

            if (decodedDocId.startsWith("primary:", ignoreCase = true)) {
                val relPath = decodedDocId.substringAfter(":")
                return if (relPath.isEmpty()) baseExt else "$baseExt/$relPath"
            } else if (decodedDocId.startsWith("raw:", ignoreCase = true)) {
                return decodedDocId.substringAfter("raw:")
            } else if (decodedDocId.contains(":")) {
                val parts = decodedDocId.split(":")
                val storageId = parts[0]
                val relPath = if (parts.size > 1) parts[1] else ""
                val candidate = File("/storage/$storageId/$relPath")
                if (candidate.exists() || File("/storage/$storageId").exists()) {
                    return candidate.absolutePath
                }
                return "$baseExt/$relPath"
            } else {
                return "$baseExt/$decodedDocId"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Local fallback for directory listing in case backend is offline.
     */
    fun listLocalDirectories(dirPath: String): List<FsDirectoryItem> {
        val dir = File(dirPath)
        if (!dir.exists() || !dir.isDirectory) return emptyList()

        val files = dir.listFiles() ?: return emptyList()
        return files.filter { it.isDirectory }
            .map { file ->
                val isGit = File(file, ".git").exists()
                FsDirectoryItem(
                    name = file.name,
                    path = file.absolutePath,
                    isHidden = file.name.startsWith("."),
                    isGit = isGit
                )
            }
            .sortedWith(compareBy({ it.isHidden }, { it.name.lowercase() }))
    }

    /**
     * Local fallback for project creation.
     */
    fun createLocalProject(
        targetPath: String,
        template: String = "empty",
        createReadme: Boolean = true
    ): Boolean {
        try {
            val folder = File(targetPath)
            if (!folder.exists()) {
                folder.mkdirs()
            }
            if (createReadme) {
                val readme = File(folder, "README.md")
                if (!readme.exists()) {
                    readme.writeText("# ${folder.name}\n\nProject created with Grok Workbench on Googlebook OS.\n")
                }
            }
            val gitignore = File(folder, ".gitignore")
            if (!gitignore.exists()) {
                gitignore.writeText(".idea/\n*.iml\nbuild/\n.gradle/\n__pycache__/\n*.pyc\nnode_modules/\n.DS_Store\n")
            }
            if (template == "python") {
                val mainPy = File(folder, "main.py")
                if (!mainPy.exists()) {
                    mainPy.writeText("def main():\n    print(\"Hello from Grok Workbench!\")\n\nif __name__ == \"__main__\":\n    main()\n")
                }
            } else if (template == "web") {
                val indexHtml = File(folder, "index.html")
                if (!indexHtml.exists()) {
                    indexHtml.writeText("<!DOCTYPE html>\n<html>\n<head><title>${folder.name}</title></head>\n<body>\n  <h1>${folder.name}</h1>\n</body>\n</html>\n")
                }
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
