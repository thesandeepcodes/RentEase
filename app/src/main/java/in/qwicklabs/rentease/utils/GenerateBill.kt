package `in`.qwicklabs.rentease.utils

import android.icu.util.Calendar
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import `in`.qwicklabs.rentease.activities.Rooms
import `in`.qwicklabs.rentease.constants.Constants
import `in`.qwicklabs.rentease.model.Bill
import `in`.qwicklabs.rentease.model.Charge
import `in`.qwicklabs.rentease.model.Meter
import `in`.qwicklabs.rentease.model.MeterReading
import `in`.qwicklabs.rentease.model.Room
import `in`.qwicklabs.rentease.model.Tenant
import `in`.qwicklabs.rentease.model.Transaction
import `in`.qwicklabs.rentease.model.TransactionType
import java.util.UUID

class GenerateBill(private val bill: Bill) {
    var total: Double = 0.0
    var billAmount: Double = 0.0
    var billTotal: Double = 0.0
    var advanced: Double = 0.0
    var discount: Double = 0.0

    private var meterCharge: Double = 0.0
    private var meterReading: Double = 0.0

    init {
        // base rent
        total += bill.baseRent

        // add charges
        bill.charges.forEach { charge ->
            total += charge.amount
        }

        // custom Charges
        bill.customCharges.forEach { customCharge ->
            total += customCharge.value
        }

        // apply discount
        discount = bill.discount
        total -= bill.discount

        // add meter charges
        bill.meters.forEach { meter ->
            val unitCost = meter.unitCost
            var reading = 0.0

            val readings = bill.readings.filter { it.meterId == meter.id }

            if (readings.isNotEmpty()) {
                if (readings.size > 1) {
                    val sortedReadings = readings.sortedBy { it.createdAt.toDate() }

                    reading = sortedReadings[1].value - sortedReadings[0].value
                } else {
                    reading = readings[0].value
                }
            }

            val share = meter.appliedTo[bill.roomId] ?: 100

            total += unitCost * (reading * share) / 100
            meterCharge += unitCost * (reading * share) / 100
            meterReading += reading * share / 100
        }


        // set the Bill Amount
        billAmount = total
        billTotal = total

        // advanced
        advanced = if (bill.advanced > billAmount) {
            billAmount
        } else {
            bill.advanced
        }

        // subtract advanced from bill amount
        billAmount -= advanced
        total -= advanced

        // collected payments
        total -= bill.collected
    }


    companion object {
        fun dues(bills: List<Bill>): Double {
            var previousBillAmount = 0.0

            bills.sortedByDescending { it.createdAt.toDate() }.forEach {
                val generated = GenerateBill(it)

                if (!it.paid) {
                    previousBillAmount += generated.total
                } else {
                    previousBillAmount -= it.collected - generated.billAmount
                }
            }

            val nextAdvanced = bills.firstOrNull()?.nextAdvanced ?: 0.0

            return previousBillAmount - nextAdvanced;
        }

        fun breakdown(bills: List<Bill>): MutableList<Map<String, Long>> {

            val bill = bills.sortedByDescending { it.createdAt.toDate() }[0]
            val generatedBill = GenerateBill(bill)

            val breakDown = mutableListOf<Map<String, Long>>()

            breakDown.add(
                mapOf("Base rent" to bill.baseRent.toLong())
            )

            bill.charges.forEach { charge ->
                breakDown.add(
                    mapOf(charge.name to Math.round(charge.amount))
                )
            }

            breakDown.add(
                mapOf("Electricity" to Math.round(generatedBill.meterCharge))
            )

            breakDown.add(emptyMap())

            bill.customCharges.forEach { customCharge ->
                breakDown.add(
                    mapOf(customCharge.key to Math.round(customCharge.value))
                )
            }

            if (bill.customCharges.isNotEmpty()) breakDown.add(emptyMap())

            breakDown.add(mapOf("Discount" to -1 * Math.round(bill.discount)))

            // previous bills
            var previousBillAmount = 0.0

            if (bills.size > 1) {
                bills.sortedByDescending { it.createdAt.toDate() }.slice(1..<bills.size).forEach {
                    val generated = GenerateBill(it)
                    breakDown.add(
                        mapOf(
                            "${
                                Date.formatTimestamp(
                                    it.createdAt,
                                    "MMMM"
                                )
                            } due" to Math.round(generated.total)
                        )
                    )
                    previousBillAmount += generated.total
                }
            }

            if (bills.isNotEmpty()) breakDown.add(emptyMap())

            val payableAmount =
                if (!bill.paid) previousBillAmount + generatedBill.total else generatedBill.billAmount

            if (bill.collected > 0 && !bill.paid) {
                breakDown.add(mapOf("Partial Paid" to -1 * Math.round(bill.collected)))
                breakDown.add(mapOf())
            }

            if (generatedBill.advanced > 0) {
                breakDown.add(mapOf("Prepayment" to -1 * Math.round(generatedBill.advanced)))
                breakDown.add(mapOf())
            }

            breakDown.add(mapOf("Amount Payable" to Math.round(payableAmount)))

            return breakDown
        }

        fun delete(bill: Bill, onFailure: (Exception) -> Unit, onComplete: () -> Unit) {
            if (Date.compareMonth(
                    bill.createdAt.toDate(),
                    Calendar.getInstance().time,
                    true
                ) && bill.collected == 0.0
            ) {
                Constants.getDbRef().collection(Constants.BILL_COLLECTION).document(bill.id)
                    .delete()
                    .addOnSuccessListener {
                        onComplete()
                    }.addOnFailureListener { e ->
                        onFailure(e)
                    }
            } else {
                onFailure(Exception("Bill can only be deleted if it is for the latest month and has no collected amount"))
            }
        }

        fun generate(
            room: Room,
            createdAt: Calendar,
            currentBill: Bill? = null,
            onComplete: (bill: Bill?) -> Unit
        ) {
            Constants.getDbRef().collection(Constants.CHARGE_COLLECTION)
                .whereArrayContains("appliedTo", room.id).get()
                .addOnSuccessListener { chargeSnapshot ->
                    val charges = mutableListOf<Charge>()

                    chargeSnapshot.documents.forEach { doc ->
                        val charge = doc.toObject(Charge::class.java)
                        if (charge !== null) charges.add(charge)
                    }

                    Constants.getDbRef().collection(Constants.METER_COLLECTION)
                        .whereGreaterThan("appliedTo.${room.id}", 0).get()
                        .addOnSuccessListener { meterSnapshot ->
                            val meters = mutableListOf<Meter>()

                            meterSnapshot.documents.forEach { doc ->
                                val meter = doc.toObject(Meter::class.java)
                                if (meter !== null) meters.add(meter)
                            }

                            fun onReadingsLoaded(readings: MutableList<MeterReading>) {
                                var collectedPayment = 0.0
                                var advanced = 0.0
                                var nextAdvanced = 0.0
                                var customCharges = mutableMapOf<String, Double>()
                                var canGenerateBill = false

                                fun addBill() {
                                    val bill = Bill(
                                        currentBill?.id ?: UUID.randomUUID().toString(),
                                        false,
                                        collectedPayment,
                                        advanced,
                                        nextAdvanced,
                                        room.id,
                                        charges,
                                        meters,
                                        readings,
                                        room.rent,
                                        customCharges,
                                        0.0,
                                        Timestamp(createdAt.time)
                                    )

                                    if (canGenerateBill) {

                                        val generatedBill = GenerateBill(bill)

                                        if (advanced > generatedBill.billTotal) {
                                            bill.nextAdvanced = advanced - generatedBill.billTotal
                                            bill.advanced = generatedBill.billTotal
                                        }

                                        Constants.getDbRef().collection(Constants.BILL_COLLECTION)
                                            .document(bill.id).set(bill, SetOptions.merge())
                                            .addOnCompleteListener {
                                                onComplete(bill)
                                            }
                                    } else {
                                        // show/call error callback
                                        onComplete(null)
                                    }
                                }

                                if (currentBill == null) {
                                    Constants.getDbRef().collection(Constants.BILL_COLLECTION)
                                        .whereEqualTo("roomId", room.id).get()
                                        .addOnSuccessListener {
                                            if (it.documents.isNotEmpty()) {
                                                val lastBill = it.documents.mapNotNull { bill ->
                                                    bill.toObject(Bill::class.java)
                                                }.maxByOrNull { bill -> bill.createdAt.toDate() }

                                                if (lastBill !== null) {
                                                    canGenerateBill =
                                                        lastBill.createdAt <= Timestamp(createdAt.time)
                                                    collectedPayment = 0.0
                                                    advanced = lastBill.nextAdvanced
                                                }
                                            } else {
                                                canGenerateBill = true
                                            }
                                        }.addOnCompleteListener {
                                            addBill()
                                        }
                                } else {
                                    canGenerateBill = true
                                    collectedPayment = currentBill.collected
                                    customCharges = currentBill.customCharges
                                    advanced = currentBill.advanced
                                    nextAdvanced = currentBill.nextAdvanced

                                    addBill()
                                }
                            }


                            // Inside the meters processing block
                            fun getBound(calendar: Calendar): Pair<Timestamp, Timestamp> {
                                val cal = calendar.clone() as Calendar

                                cal.set(Calendar.DAY_OF_MONTH, 1)
                                cal.set(Calendar.HOUR_OF_DAY, 0)
                                cal.set(Calendar.MINUTE, 0)
                                cal.set(Calendar.SECOND, 0)
                                cal.set(Calendar.MILLISECOND, 0)


                                cal.add(Calendar.MONTH, -1) // start of previous month
                                val startOfMonth = Timestamp(cal.time)

                                cal.add(Calendar.MONTH, 2) // start of next month
                                val startOfNextMonth = Timestamp(cal.time)

                                return Pair(startOfMonth, startOfNextMonth)
                            }

                            if (meters.isEmpty()) {
                                onReadingsLoaded(mutableListOf())
                            } else {
                                val readings = mutableListOf<MeterReading>()
                                var completedFetches = 0

                                meters.forEach { meter ->
                                    val monthBounds = getBound(createdAt)

                                    Constants.getDbRef().collection(Constants.METER_COLLECTION)
                                        .document(meter.id)
                                        .collection(Constants.METER_READING_COLLECTION)
                                        .whereGreaterThanOrEqualTo("createdAt", monthBounds.first)
                                        .whereLessThan("createdAt", monthBounds.second)
                                        .get()
                                        .addOnSuccessListener { querySnapshot ->
                                            querySnapshot.documents.forEach { doc ->
                                                val reading = doc.toObject(MeterReading::class.java)
                                                if (reading != null) {
                                                    readings.add(reading)
                                                }
                                            }
                                        }
                                        .addOnCompleteListener {
                                            completedFetches++
                                            // Check if all fetches are completed
                                            if (completedFetches == meters.size) {
                                                onReadingsLoaded(readings)
                                            }
                                        }
                                }
                            }

                        }
                }
        }

        fun generateAll(createdAt: Calendar, onComplete: () -> Unit = {}) {
            Rooms().getRooms { rooms ->
                rooms.forEachIndexed { roomIndex, room ->
                    generate(room, createdAt) {
                        if (roomIndex == rooms.size - 1) {
                            onComplete()
                        }
                    }
                }
            }
        }

        fun collectBill(
            amount: Double,
            room: Room,
            tenant: Tenant,
            date: java.util.Date,
            onFailure: (e: Exception) -> Unit,
            onComplete: () -> Unit
        ) {
            Constants.getDbRef().collection(Constants.BILL_COLLECTION)
                .whereEqualTo("roomId", room.id)
                .get()
                .addOnSuccessListener { result ->
                    var totalAmount = amount
                    val batch = FirebaseFirestore.getInstance().batch()

                    val bills =
                        result.documents.mapNotNull { bill -> bill.toObject(Bill::class.java) }
                            .filter { !it.paid }.sortedBy { it.createdAt.toDate() }.toMutableList()
                    val allBills =
                        result.documents.mapNotNull { bill -> bill.toObject(Bill::class.java) }
                            .sortedByDescending { it.createdAt.toDate() }

                    if (bills.isEmpty()) bills.add(allBills[0])

                    if (bills.isNotEmpty()) {
                        for ((index, bill) in bills.withIndex()) {
                            val generatedBill = GenerateBill(bill)

                            var transactionAmount: Double

                            val updatedBill =
                                if (totalAmount >= generatedBill.total && !bill.paid) {
                                    if (bills.size > index + 1) {
                                        transactionAmount = generatedBill.total
                                        totalAmount -= generatedBill.total
                                        bill.copy(collected = generatedBill.billAmount, paid = true)
                                    } else {
                                        transactionAmount = totalAmount
                                        totalAmount = 0.0

                                        val nextAdvanced =
                                            transactionAmount - generatedBill.billAmount

                                        bill.copy(
                                            collected = generatedBill.billAmount,
                                            nextAdvanced = nextAdvanced,
                                            paid = true
                                        )
                                    }
                                } else {
                                    transactionAmount = totalAmount
                                    totalAmount = 0.0

                                    if (!bill.paid) {
                                        bill.copy(collected = bill.collected + transactionAmount)
                                    } else {
                                        bill.copy(nextAdvanced = bill.nextAdvanced + transactionAmount)
                                    }
                                }

                            batch.set(
                                Constants.getDbRef().collection(Constants.BILL_COLLECTION)
                                    .document(updatedBill.id), updatedBill, SetOptions.merge()
                            )


                            val transaction = Transaction(
                                UUID.randomUUID().toString(),
                                TransactionType.REGULAR,
                                null,
                                bill.id,
                                room.id,
                                tenant.id,
                                Timestamp(date),
                                transactionAmount
                            )

                            batch.set(
                                Constants.getDbRef().collection(Constants.TRANSACTION_COLLECTION)
                                    .document(transaction.id), transaction
                            )

                            if (totalAmount <= 0) break
                        }

                        batch.set(
                            Constants.getDbRef().collection(Constants.ROOM_COLLECTION)
                                .document(room.id),
                            mapOf("revenue" to (amount + room.revenue)),
                            SetOptions.merge()
                        )

                        batch.commit().addOnSuccessListener { onComplete() }
                            .addOnFailureListener { e -> onFailure(e) }
                    } else {
                        onFailure(Exception("No Bills associated with this room ${room.name}"))
                    }
                }.addOnFailureListener { e -> onFailure(e) }
        }

        fun deleteTransaction(
            transaction: Transaction,
            onFailure: (Exception) -> Unit,
            onComplete: () -> Unit
        ) {
            val transRef = Constants.getDbRef().collection(Constants.TRANSACTION_COLLECTION)
                .document(transaction.id)

            when (transaction.type) {
                TransactionType.EXPENSE -> {
                    Constants.getDbRef().collection(Constants.EXPENSE).document(Constants.EXPENSE)
                        .get().addOnSuccessListener { snapshot ->
                            val e = snapshot.getDouble("expense") ?: 0.0

                            Constants.getDbRef().collection(Constants.EXPENSE)
                                .document(Constants.EXPENSE).set(
                                    mapOf(
                                        "expense" to (e - transaction.amount)
                                    )
                                ).addOnSuccessListener {
                                    transRef.delete().addOnSuccessListener {
                                        onComplete()
                                    }.addOnFailureListener { e ->
                                        onFailure(e)
                                    }
                                }.addOnFailureListener { error ->
                                    onFailure(error)
                                }
                        }.addOnFailureListener { e ->
                            onFailure(e)
                        }
                }

                TransactionType.REGULAR -> {
                    if (transaction.billId == null || transaction.roomId == null) {
                        onFailure(Exception("No Bill Id is associated with this transaction"))
                        return
                    }

                    Constants.getDbRef().collection(Constants.BILL_COLLECTION)
                        .document(transaction.billId).get().addOnSuccessListener {
                            val bill = it.toObject(Bill::class.java)

                            if (bill !== null) {
                                val remainingCollection =
                                    (bill.collected + bill.nextAdvanced) - transaction.amount
                                val generatedBill = GenerateBill(bill)
                                val paid = remainingCollection >= generatedBill.billAmount

                                val nextAdvanced =
                                    if (remainingCollection >= generatedBill.billAmount) remainingCollection - generatedBill.billAmount else 0.0
                                val updatedBill = bill.copy(
                                    paid = paid,
                                    collected = remainingCollection,
                                    nextAdvanced = nextAdvanced
                                )

                                val batch = FirebaseFirestore.getInstance().batch()
                                batch.delete(transRef)
                                batch.set(
                                    Constants.getDbRef().collection(Constants.BILL_COLLECTION)
                                        .document(transaction.billId), updatedBill
                                )

                                Constants.getDbRef().collection(Constants.ROOM_COLLECTION)
                                    .document(transaction.roomId).get()
                                    .addOnSuccessListener { roomSnapshots ->
                                        val room = roomSnapshots.toObject(Room::class.java)

                                        if (room !== null) {
                                            val updatedRoom =
                                                room.copy(revenue = room.revenue - transaction.amount)
                                            batch.set(
                                                Constants.getDbRef()
                                                    .collection(Constants.ROOM_COLLECTION)
                                                    .document(transaction.roomId), updatedRoom
                                            )

                                            batch.commit().addOnSuccessListener {
                                                onComplete()
                                            }.addOnFailureListener { e ->
                                                onFailure(e)
                                            }
                                        } else {
                                            onFailure(Exception("Unable to fetch details of Room attached to this transaction "))
                                        }
                                    }.addOnFailureListener { e ->
                                        onFailure(e)
                                    }
                            } else {
                                onFailure(Exception("Unable to fetch bill details for the provided transaction"))
                            }
                        }.addOnFailureListener { e ->
                            onFailure(e)
                        }
                }
            }
        }
    }
}