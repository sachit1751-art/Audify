package com.music.audify.download

/** One physical downloaded file. Multiple song ids may point at the same URI. */
internal data class StorageBudgetFile(
    val uri: String,
    val videoIds: List<String>,
    val title: String,
    val artist: String,
    val sizeBytes: Long,
    val addedAtMillis: Long,
)

internal data class StorageBudgetPlan(
    val usedBytes: Long,
    val projectedBytes: Long,
    val budgetBytes: Long,
    val requiredFreeBytes: Long,
    val oldestFiles: List<StorageBudgetFile>,
)

internal data class DownloadStoragePrompt(
    val songTitle: String,
    val expectedBytes: Long?,
    val reason: String?,
    val plan: StorageBudgetPlan?,
    val unknown: Boolean = true,
    val files: List<StorageBudgetFile> = emptyList(),
)

internal sealed interface StorageBudgetDecision {
    data object Allowed : StorageBudgetDecision
    data object SizeUnknown : StorageBudgetDecision
    data class CannotFit(val oldestFiles: List<StorageBudgetFile>) : StorageBudgetDecision
    data class RequiresConfirmation(val plan: StorageBudgetPlan) : StorageBudgetDecision
}

/**
 * Decide before creating a destination. Every input size must be measured from
 * the actual file or resolved source URL; a missing measurement is never guessed.
 */
internal fun evaluateStorageBudget(
    files: List<StorageBudgetFile>?,
    projectedBytes: Long?,
    budgetBytes: Long,
    reservedBytes: Long = 0L,
): StorageBudgetDecision {
    if (budgetBytes <= 0L) return StorageBudgetDecision.Allowed // unlimited
    if (projectedBytes == null || projectedBytes <= 0L || files == null) {
        return StorageBudgetDecision.SizeUnknown
    }
    val usedBytes = files.fold(0L) { total, file ->
        if (file.sizeBytes < 0L || Long.MAX_VALUE - total < file.sizeBytes) {
            return StorageBudgetDecision.SizeUnknown
        }
        total + file.sizeBytes
    }
    if (Long.MAX_VALUE - usedBytes < projectedBytes) return StorageBudgetDecision.SizeUnknown
    if (reservedBytes < 0L || Long.MAX_VALUE - usedBytes < reservedBytes) return StorageBudgetDecision.SizeUnknown
    val accountedBytes = usedBytes + reservedBytes
    if (Long.MAX_VALUE - accountedBytes < projectedBytes) return StorageBudgetDecision.SizeUnknown
    val required = accountedBytes + projectedBytes - budgetBytes
    if (required <= 0L) return StorageBudgetDecision.Allowed

    var freed = 0L
    val candidates = mutableListOf<StorageBudgetFile>()
    files.sortedWith(compareBy<StorageBudgetFile> { it.addedAtMillis }.thenBy { it.uri })
        .forEach { file ->
            if (freed < required) {
                candidates += file
                if (Long.MAX_VALUE - freed < file.sizeBytes) return StorageBudgetDecision.SizeUnknown
                freed += file.sizeBytes
            }
        }
    if (freed < required) return StorageBudgetDecision.CannotFit(candidates)
    return StorageBudgetDecision.RequiresConfirmation(
        StorageBudgetPlan(usedBytes, projectedBytes, budgetBytes, required, candidates),
    )
}

internal fun formatStorageBytes(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
}

internal fun formatStorageBudget(bytes: Long): String =
    if (bytes <= 0L) "Unlimited" else formatStorageBytes(bytes)
