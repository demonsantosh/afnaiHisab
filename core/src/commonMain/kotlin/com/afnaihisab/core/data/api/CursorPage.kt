package com.afnaihisab.core.data.api

import kotlinx.serialization.Serializable

/**
 * Cursor-paginated response wrapper for any potentially-unbounded list — expense history,
 * audit log (ADR-0015). Offset pagination is deliberately not used: it skips or repeats rows
 * under the concurrent inserts a shared ledger will have.
 *
 * The cursor is the last item's UUIDv7 id, which is time-sortable, so no separate sequence or
 * timestamp column is needed (`docs/domain-model.md` — "ID strategy").
 *
 * @property nextCursor pass back as the `cursor` query parameter; null means this is the last page.
 */
@Serializable
data class CursorPage<T>(
    val items: List<T>,
    val nextCursor: String? = null,
)
