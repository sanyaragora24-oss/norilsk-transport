package com.example.data.local

import kotlinx.serialization.Serializable

/** Группа избранного, напр. «На работу», «На дачу». */
@Serializable
data class FavoriteGroup(
    val id: String,
    val name: String,
    val stopIds: List<String> = emptyList(),
    val routeIds: List<String> = emptyList()
)
