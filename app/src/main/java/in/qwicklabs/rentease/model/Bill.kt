package `in`.qwicklabs.rentease.model

import com.google.firebase.Timestamp

data class Bill(
    val id: String,
    val paid: Boolean,
    val collected: Double,
    var advanced: Double,
    var nextAdvanced: Double,
    val roomId: String,
    val charges: List<Charge>,
    val meters: List<Meter>,
    val readings: List<MeterReading>,
    val baseRent: Int,
    val customCharges: MutableMap<String, Double>,
    var discount: Double,
    val createdAt: Timestamp,
) {
    constructor() : this(
        "",
        false,
        0.0,
        0.0,
        0.0,
        "",
        emptyList(),
        emptyList(),
        emptyList(),
        0,
        mutableMapOf(),
        0.0,
        Timestamp.now()
    )
}

sealed class BillItem {
    data class NormalItem(val data: Bill) : BillItem()
    data class MergedItem(val data: List<Bill>) : BillItem()
}