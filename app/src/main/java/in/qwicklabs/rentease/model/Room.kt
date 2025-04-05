package `in`.qwicklabs.rentease.model

data class Room(
    val id: String,
    val name: String,
    val des: String,
    val img: String?,
    val rent: Int,
    val revenue: Double
) {
    constructor(
        id: String,
        name: String,
        des: String,
        img: String?,
        rent: Int,
    ) : this(id, name, des, img, rent, 0.00)

    constructor() : this("", "", "", null, 0, 0.00)
}
