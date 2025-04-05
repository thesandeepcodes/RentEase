package `in`.qwicklabs.rentease.model

import com.google.firebase.Timestamp

data class Transaction(
    val id: String,
    val type: TransactionType,
    val name: String?,
    val billId: String?,
    val roomId: String?,
    val tenantId: String?,
    val createdAt: Timestamp,
    val amount: Double,
){
    constructor(): this("", TransactionType.REGULAR, null, "", "", "", Timestamp.now(), 0.0)
}

enum class TransactionType{
    REGULAR,
    EXPENSE
}