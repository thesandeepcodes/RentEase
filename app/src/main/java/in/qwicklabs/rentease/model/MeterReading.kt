package `in`.qwicklabs.rentease.model

import com.google.firebase.Timestamp

data class MeterReading(
    val id: String,
    val meterId: String,
    val value: Double,
    val createdAt: Timestamp
){
    constructor(): this("", "", 0.0, Timestamp.now())
}
