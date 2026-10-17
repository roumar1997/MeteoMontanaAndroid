package com.meteomontana.android.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class RestrictionDto(
    val id: String,
    val schoolId: String,
    val sectorBlockId: String? = null,
    val blockIds: List<String> = emptyList(),
    val kind: String,
    val reason: String,
    val startsOn: String? = null,
    val endsOn: String? = null,
    val yearly: Boolean = false,
    val permanent: Boolean = false,
    val sourceName: String = "",
    val sourceUrl: String? = null,
    val verifiedOn: String? = null,
)

@Serializable
data class RestrictionInputDto(
    val schoolId: String,
    val sectorBlockId: String? = null,
    val blockIds: List<String> = emptyList(),
    val kind: String,
    val reason: String,
    val startsOn: String? = null,
    val endsOn: String? = null,
    val yearly: Boolean = false,
    val permanent: Boolean = false,
    val sourceName: String,
    val sourceUrl: String? = null,
    val verifiedOn: String? = null,
)
