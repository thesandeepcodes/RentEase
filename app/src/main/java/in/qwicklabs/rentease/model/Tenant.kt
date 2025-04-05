package `in`.qwicklabs.rentease.model

import com.google.firebase.Timestamp
import java.time.LocalDate

data class Tenant(
    val id: String,
    val name: String,
    val roomId: String,
    val phone: String,
    val description: String,
    val profile: String?,
    val documents: Map<String, String>,
    val active: Boolean,
    val startDate: Timestamp,
    val leaveDate: Timestamp
){
    constructor(id: String, name: String, roomId: String, phone: String, description: String): this(id, name, roomId, phone, description, null, emptyMap(), true, Timestamp.now(), Timestamp.now())

    constructor(): this("", "", "", "", "", null, emptyMap(), true, Timestamp.now(), Timestamp.now())
}
