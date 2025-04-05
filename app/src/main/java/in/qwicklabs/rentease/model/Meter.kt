package `in`.qwicklabs.rentease.model

import com.google.firebase.Timestamp

data class Meter(
    val id: String,
    val name: String,
    val unitCost: Float,
    val appliedTo: Map<String, Int>,
    val createdAt: Timestamp,
){
    constructor(): this("", "", 0f, emptyMap(), Timestamp.now())
}
