package `in`.qwicklabs.rentease.model

data class Charge(
    val id: String,
    val name: String,
    val amount: Double,
    val appliedTo: List<String>
){
    constructor(): this("", "", 0.0, emptyList())
}