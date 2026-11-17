package dev.liquid.launcher.core.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dev.liquid.launcher.core.common.DebugLog

/**
 * C4 (0.9.19): past exam papers ("annales"), one folder per module, inside a folder you chose once (Settings →
 * Study companion). Read through the documents provider, so no storage permission is needed and nothing else on
 * the phone is visible to the launcher.
 */
object Annales {
    data class Doc(val name: String, val uri: Uri, val modified: Long)

    data class Module(val name: String, val docs: List<Doc>)

    private const val DIR = DocumentsContract.Document.MIME_TYPE_DIR

    /**
     * The modules of the chosen [tree]: each sub-folder is a module with the PDFs inside it (and one level further
     * down, for "Module/2024/…"), newest first; PDFs lying at the top are the module "Other". Folders without PDFs
     * are left out. Empty when the folder cannot be read (moved, or access withdrawn).
     */
    fun read(context: Context, tree: Uri): List<Module> = try {
        val root = DocumentsContract.getTreeDocumentId(tree)
        val top = children(context, tree, root)
        val modules = top.filter { it.mime == DIR }.mapNotNull { folder ->
            val inside = children(context, tree, folder.id)
            val docs = inside.filter { it.isPdf } + inside.filter { it.mime == DIR }.flatMap { sub -> children(context, tree, sub.id).filter { it.isPdf } }
            if (docs.isEmpty()) null else Module(folder.name, docs.map { it.doc(tree) }.sortedByDescending { it.modified })
        }.sortedBy { it.name.lowercase() }
        val loose = top.filter { it.isPdf }
        if (loose.isEmpty()) modules else modules + Module("Other", loose.map { it.doc(tree) }.sortedByDescending { it.modified })
    } catch (e: Exception) {
        DebugLog.error("Study", "reading the annales folder failed: $e")
        emptyList()
    }

    /** The modules' names matched loosely to a timetable subject ("Stat Des" finds "stat des", "Stat-Des"). */
    fun matching(modules: List<Module>, subject: String): Module? {
        fun key(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
        val wanted = key(subject)
        if (wanted.isEmpty()) return null
        return modules.firstOrNull { key(it.name) == wanted } ?: modules.firstOrNull { key(it.name).contains(wanted) || wanted.contains(key(it.name)) }
    }

    private class Entry(val id: String, val name: String, val mime: String, val modified: Long) {
        val isPdf: Boolean get() = mime == "application/pdf" || name.endsWith(".pdf", ignoreCase = true)
        fun doc(tree: Uri) = Doc(name.removeSuffix(".pdf").removeSuffix(".PDF"), DocumentsContract.buildDocumentUriUsingTree(tree, id), modified)
    }

    private fun children(context: Context, tree: Uri, parent: String): List<Entry> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
        val out = ArrayList<Entry>()
        context.contentResolver.query(
            uri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            ),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                out += Entry(c.getString(0) ?: continue, c.getString(1).orEmpty(), c.getString(2).orEmpty(), if (c.isNull(3)) 0L else c.getLong(3))
            }
        }
        return out
    }
}
