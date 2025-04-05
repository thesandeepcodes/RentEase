package `in`.qwicklabs.rentease.activities

import android.icu.util.Calendar
import android.os.Bundle
import android.text.InputType
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.SetOptions
import `in`.qwicklabs.rentease.R
import `in`.qwicklabs.rentease.constants.Constants
import `in`.qwicklabs.rentease.databinding.ActivityViewBillBinding
import `in`.qwicklabs.rentease.databinding.BottomBillAddChargeBinding
import `in`.qwicklabs.rentease.databinding.BottomBillShareTenantsBinding
import `in`.qwicklabs.rentease.databinding.BottomEditFieldBinding
import `in`.qwicklabs.rentease.databinding.ListBillShareTenantProfileBinding
import `in`.qwicklabs.rentease.databinding.ListViewBillCustomChargeBinding
import `in`.qwicklabs.rentease.databinding.ListViewBillDividerBinding
import `in`.qwicklabs.rentease.databinding.ListViewBillItemBinding
import `in`.qwicklabs.rentease.model.Bill
import `in`.qwicklabs.rentease.model.Room
import `in`.qwicklabs.rentease.model.Tenant
import `in`.qwicklabs.rentease.utils.AppUtils
import `in`.qwicklabs.rentease.utils.Date
import `in`.qwicklabs.rentease.utils.GenerateBill
import `in`.qwicklabs.rentease.utils.Loader
import kotlin.math.abs

class ViewBill : AppCompatActivity() {
    private lateinit var binding: ActivityViewBillBinding
    private lateinit var billId: Array<String>
    private lateinit var bills: MutableList<Bill>

    private lateinit var bottomSheetDialog: BottomSheetDialog
    private lateinit var loader: Loader

    private lateinit var roomData: Room

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityViewBillBinding.inflate(layoutInflater)
        billId = intent.getStringArrayExtra("billId") ?: emptyArray<String>()
        loader = Loader(this)

        if (billId.isEmpty()) {
            AppUtils.alert(this, "Oops!", "We didn't find any bill with this bill id")
            return
        }

        setContentView(binding.root)

        fetchBill(billId) {
            binding.viewBillLoader.visibility = View.GONE

            bills = it.sortedByDescending { bi -> bi.createdAt.toDate() }.toMutableList()

            if (bills.isNotEmpty()) {
                binding.viewBillWrapper.visibility = View.VISIBLE

                binding.viewBillMonth.text = Date.formatTimestamp(bills[0].createdAt)
                binding.viewBillRoomName.text = "Loading.."

                getRoomDate(bills[0].roomId) {
                    roomData = it
                    binding.viewBillRoomName.text = "Bill for ${it.name}"
                }

                showBill()
                updateCharge()
            } else {
                AppUtils.alert(this, "Oops!", "We couldn't find any bill with the provided bill id")
            }
        }

        binding.viewBillClose.setOnClickListener { finish() }
        binding.viewBillAddCharge.setOnClickListener { showChargeDialog() }
        binding.viewBillEditDiscount.setOnClickListener { editDiscount() }

        binding.viewBillShare.setOnClickListener {
            loader.dialogTitle.text = "Fetching.."
            loader.dialog.show()

            getTenants(bills[0].roomId) { tenants ->
                loader.dialog.dismiss()
                showShareDialog(tenants)
            }
        }
    }

    private fun showShareDialog(tenants: List<Tenant>) {
        bottomSheetDialog = BottomSheetDialog(this)
        val content = BottomBillShareTenantsBinding.inflate(layoutInflater)

        content.billShareTenants.removeAllViews()

        if (tenants.isEmpty()) {
            AppUtils.alert(
                this,
                "Oops!",
                "No tenants associated with this bill. The room doesn't have any tenants yet!"
            )
            return
        }

        tenants.forEach { tenant ->
            val tenantView = ListBillShareTenantProfileBinding.inflate(
                layoutInflater,
                content.billShareTenants,
                false
            )
            Glide.with(tenantView.billShareTenantImage).load(tenant.profile)
                .into(tenantView.billShareTenantImage)

            tenantView.billShareTenantName.text = tenant.name

            tenantView.root.setOnClickListener {
                val file = AppUtils.captureView(this, binding.viewBillCard)
                AppUtils.shareFileToWa(
                    this,
                    file,
                    "Dear *${tenant.name}*, \n\nYour rent bill for  *${Date.formatTimestamp(bills[0].createdAt)}* is attached. Kindly make the payment at your earliest convenience. \n\nLet me know if you have any questions. \n\nThank you!",
                    "91${tenant.phone}"
                )
            }

            content.billShareTenants.addView(tenantView.root)
        }

        content.billShareClose.setOnClickListener { bottomSheetDialog.dismiss() }
        bottomSheetDialog.setContentView(content.root)
        bottomSheetDialog.show()
    }

    private fun editDiscount() {
        bottomSheetDialog = BottomSheetDialog(this)
        val content = BottomEditFieldBinding.inflate(layoutInflater)

        content.fieldEditClose.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        content.fieldEditField.inputType =
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        content.fieldEditField.setText(Math.round(bills[0].discount).toString())
        content.fieldEditTitle.text = "Add Discount"
        content.fieldEditFieldLayout.hint = "Discount"

        content.fieldEditButton.setOnClickListener {
            val discount = content.fieldEditField.text.toString()

            if(discount.isNotEmpty()){
                loader.dialogTitle.text = "Adding.."
                loader.dialog.show()
                bills[0].discount = discount.toDouble()

                updateBill(bills[0]) {
                    loader.dialog.dismiss()
                    showBill()
                    bottomSheetDialog.dismiss()
                }
            }
        }

        bottomSheetDialog.setContentView(content.root)
        bottomSheetDialog.show()
    }

    private fun showChargeDialog() {
        bottomSheetDialog = BottomSheetDialog(this)

        val content = BottomBillAddChargeBinding.inflate(layoutInflater)

        content.billChargeClose.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        content.billChargeButton.setOnClickListener {
            val name = content.billChargeName.text.toString()
            val amount = content.billChargeAmount.text.toString()

            if (name.isNotEmpty() && amount.isNotEmpty()) {
                if (bills.isNotEmpty()) {
                    val bill = bills[0]
                    bill.customCharges[name] = amount.toDouble()

                    loader.dialogTitle.text = "Adding Charge"
                    loader.dialog.show()

                    updateBill(bill) {
                        updateCharge()
                        showBill()
                        bottomSheetDialog.dismiss()
                        loader.dialog.dismiss()
                    }
                }
            }
        }

        bottomSheetDialog.setContentView(content.root)
        bottomSheetDialog.show()
    }

    private fun updateCharge() {
        binding.viewBillCustomCharges.removeAllViews()
        val bill = bills[0]

        if (Date.compareMonth(
                bill.createdAt.toDate(),
                Calendar.getInstance().time,
                true
            ) && !bill.paid
        ) {
            bill.customCharges.forEach { charge ->
                val customCharge = ListViewBillCustomChargeBinding.inflate(
                    layoutInflater,
                    binding.viewBillCustomCharges,
                    false
                )
                customCharge.billChargeName.text = charge.key
                customCharge.billChargeAmount.text = charge.value.toString()

                customCharge.billChargeRemove.setOnClickListener {
                    bill.customCharges.remove(charge.key)

                    loader.dialogTitle.text = "Removing Charge"
                    loader.dialog.show()

                    updateBill(bill) {
                        loader.dialog.dismiss()

                        binding.viewBillCustomCharges.removeView(customCharge.root)
                        showBill()
                    }
                }

                binding.viewBillCustomCharges.addView(customCharge.root)
            }
        }
    }

    private fun showBill() {
        if (bills.isNotEmpty()) {
            val bill = bills[0]

            if(bill.paid){
                binding.viewBillTitle.visibility = View.GONE
                binding.viewBillPaid.visibility = View.VISIBLE
            }

            if (Date.compareMonth(
                    bill.createdAt.toDate(),
                    Calendar.getInstance().time,
                    true
                ) && !bill.paid
            ) {
                binding.viewBillRegenerate.visibility = View.VISIBLE
                binding.viewBillDelete.visibility = View.VISIBLE
                binding.viewBillOptions.visibility = View.VISIBLE

                binding.viewBillRegenerate.setOnClickListener {
                    val created = Calendar.getInstance()
                    created.time = bill.createdAt.toDate()

                    loader.dialogTitle.text = "Regenrating.."
                    loader.dialog.show()

                    GenerateBill.generate(roomData, created, bill) { updatedBill ->
                        if (updatedBill !== null) {
                            bills[0] = updatedBill

                            showBill()
                            updateCharge()
                        } else {
                            AppUtils.alert(
                                this,
                                "Oops!",
                                "Oops! We encountered an error while regenerating this bill. Please make sure no bills have been generated after this month"
                            )
                        }

                        loader.dialog.dismiss()

                    }
                }

                binding.viewBillDelete.setOnClickListener {
                    MaterialAlertDialogBuilder(this).setTitle("Delete Bill?")
                        .setMessage("Are you sure you want to delete this bill")
                        .setNegativeButton("No"){d,_->d.dismiss()}
                        .setPositiveButton("Yes, Delete"){d,_->
                            d.dismiss()

                            loader.dialogTitle.text = "Deleting.."
                            loader.dialog.show()

                            GenerateBill.delete(bill, {e->
                                AppUtils.alert(this, "Oops", "Unable to delete bill: ${e.message}")
                                loader.dialog.dismiss()
                            }){
                                loader.dialog.dismiss()
                                finish()
                            }
                        }.show()
                }
            }

            // show bill
            val listWrapper = binding.viewBillLists

            val breakdowns = GenerateBill.breakdown(bills)

            listWrapper.removeAllViews()
            breakdowns.forEach { breakdown ->
                val divider = ListViewBillDividerBinding.inflate(layoutInflater, listWrapper, false)

                if (breakdown.isNotEmpty()) {
                    val key = breakdown.keys.first()
                    val value = breakdown.values.first()

                    val list = ListViewBillItemBinding.inflate(layoutInflater, listWrapper, false)


                    list.listBillLabel.text = key
                    list.listBillAmount.text = if(value >= 0) "₹$value" else "(-) ₹${abs(value)}"

                    if(value < 0) list.listBillAmount.setTextColor(ContextCompat.getColor(this, R.color.colorDanger))

                    listWrapper.addView(list.root)
                } else {
                    listWrapper.addView(divider.root)
                }
            }
        }
    }

    private fun fetchBill(ids: Array<String>, onComplete: (bills: List<Bill>) -> Unit) {
        val db = Constants.getDbRef().collection(Constants.BILL_COLLECTION)

        if (ids.isEmpty()) {
            onComplete(emptyList())
            return
        }

        val chunks = ids.toList().chunked(10)
        val allBills = mutableListOf<Bill>()
        var completedQueries = 0

        for (chunk in chunks) {
            db.whereIn("id", chunk)
                .get()
                .addOnSuccessListener { result ->
                    allBills.addAll(result.documents.mapNotNull { it.toObject(Bill::class.java) })
                }
                .addOnCompleteListener {
                    completedQueries++

                    if (completedQueries == chunks.size) {
                        onComplete(allBills)
                    }
                }
        }
    }

    private fun getRoomDate(roomId: String, onComplete: (room: Room) -> Unit) {
        Constants.getDbRef().collection(Constants.ROOM_COLLECTION).whereEqualTo("id", roomId).get()
            .addOnSuccessListener { snapshots ->
                val room = snapshots.documents.mapNotNull { it.toObject(Room::class.java) }

                if (room.isNotEmpty()) {
                    onComplete(room[0])
                }
            }
    }

    private fun updateBill(newBill: Bill, onComplete: () -> Unit) {
        Constants.getDbRef().collection(Constants.BILL_COLLECTION).document(newBill.id)
            .set(newBill, SetOptions.merge()).addOnCompleteListener {
                onComplete()
            }
    }

    private fun getTenants(roomId: String, onComplete: (tenants: List<Tenant>) -> Unit) {
        Constants.getDbRef().collection(Constants.TENANT_COLLECTION).whereEqualTo("roomId", roomId)
            .whereEqualTo("active", true).get().addOnSuccessListener {
            onComplete(it.documents.mapNotNull { tenant -> tenant.toObject(Tenant::class.java) })
        }
    }
}