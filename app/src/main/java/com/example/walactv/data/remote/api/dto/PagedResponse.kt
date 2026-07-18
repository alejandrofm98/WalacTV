package com.example.walactv.data.remote.api.dto

import com.google.gson.annotations.SerializedName

/**
 * Base class for paginated API responses.
 *
 * Contains pagination fields common to multiple DTOs (CatalogPageResponse,
 * SearchResponse, etc.). Gson deserializes flat pagination fields from JSON
 * into these inherited properties without needing a custom TypeAdapter.
 */
open class PagedResponse(
    @SerializedName("page") val page: Int = 1,
    @SerializedName("page_size") val pageSize: Int = 0,
    @SerializedName("pages") val pages: Int = 0,
    @SerializedName("total") val total: Int = 0,
    @SerializedName("has_next") val hasNext: Boolean = false,
    @SerializedName("has_prev") val hasPrev: Boolean = false,
)
