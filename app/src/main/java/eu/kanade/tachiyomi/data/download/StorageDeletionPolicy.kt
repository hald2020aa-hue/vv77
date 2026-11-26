package eu.kanade.tachiyomi.data.download

/** Immutable file identities captured when the confirmation is opened. */
data class StorageDeleteTarget(
    val mangaId: Long,
    val chapterId: Long,
    val bytes: Long,
    val modifiedAt: Long,
    val uri: String,
)

data class StorageDeleteCandidate(val target: StorageDeleteTarget, val read: Boolean)

data class StorageDeleteRequest(
    val workIds: Set<Long>,
    val readOnly: Boolean,
    val targets: List<StorageDeleteTarget>,
) {
    val bytes: Long get() = targets.sumOf { it.bytes }
}

/** No IO or database writes: the preview and the executed request share the same file list. */
object StorageDeletionPolicy {
    fun request(
        workIds: Set<Long>,
        readOnly: Boolean,
        candidates: List<StorageDeleteCandidate>,
    ): StorageDeleteRequest {
        val targets = candidates.filter { it.target.mangaId in workIds && (!readOnly || it.read) }.map { it.target }
        return StorageDeleteRequest(targets.map { it.mangaId }.toSet(), readOnly, targets)
    }
}
