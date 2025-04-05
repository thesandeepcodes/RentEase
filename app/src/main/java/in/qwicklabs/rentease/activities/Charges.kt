package `in`.qwicklabs.rentease.activities

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.tasks.Task
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.SetOptions
import `in`.qwicklabs.rentease.adaptor.ChargeItemAdaptor
import `in`.qwicklabs.rentease.constants.Constants
import `in`.qwicklabs.rentease.model.Charge
import `in`.qwicklabs.rentease.databinding.ActivityChargesBinding
import `in`.qwicklabs.rentease.databinding.BottomAddChargeBinding
import `in`.qwicklabs.rentease.databinding.ListChargeAttachedCardBinding
import `in`.qwicklabs.rentease.utils.AppUtils
import `in`.qwicklabs.rentease.utils.Loader
import java.util.UUID

class Charges : AppCompatActivity() {
    private lateinit var binding: ActivityChargesBinding
    private lateinit var chargeItemAdaptor: ChargeItemAdaptor
    private lateinit var loader: Loader

    var attachedItems: List<Pair<String, String>> = emptyList()

    private lateinit var bottomSheetDialog: BottomSheetDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityChargesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bottomSheetDialog = BottomSheetDialog(this)
        loader = Loader(this)

        Rooms().getRooms { rooms ->
            attachedItems = listOf("All" to "all") + rooms.map {
                it.name to it.id
            }.toList()

            getCharges { charges ->
                binding.chargeLoader.visibility = View.GONE
                binding.chargeLists.visibility = View.VISIBLE

                if(charges.isEmpty()){
                    binding.chargeNoData.visibility = View.VISIBLE
                }

                chargeItemAdaptor = ChargeItemAdaptor(this, charges.sortedBy { it.name }.toMutableList())
                binding.chargeLists.adapter = chargeItemAdaptor
                binding.chargeLists.setHasFixedSize(true)
                binding.chargeLists.layoutManager = LinearLayoutManager(this)
            }
        }

        binding.chargeAdd.setOnClickListener {
            showChargeDialog()
        }

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    fun getCharges(onComplete: (MutableList<Charge>) -> Unit) {
        val charges = mutableListOf<Charge>()

        Constants.getDbRef().collection(Constants.CHARGE_COLLECTION).get().addOnSuccessListener {
            val docs = it.documents

            for (doc in docs) {
                val charge = doc.toObject(Charge::class.java)
                if (charge !== null) {
                    charges.add(charge)
                }
            }
        }.addOnCompleteListener {
            onComplete(charges)
        }
    }

    fun showChargeDialog(defaultCharge: Charge? = null) {
        if (attachedItems.isEmpty()) return

        bottomSheetDialog = BottomSheetDialog(this)

        val addBinding = BottomAddChargeBinding.inflate(layoutInflater)
        bottomSheetDialog.setContentView(addBinding.root)
        bottomSheetDialog.setCancelable(false)
        bottomSheetDialog.show()

        val attachedTo: MutableList<String> = mutableListOf()

        addBinding.addChargeClose.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        addBinding.addChargeButton.text =
            if (defaultCharge == null) "Add Charge" else "Update Charge"
        addBinding.addChargeTitle.text =
            if (defaultCharge == null) "Add Charge" else "Update Charge"

        val labels = attachedItems.map { it.first }
        val attachAdaptor = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, labels)
        addBinding.autoCompleteTextView.setAdapter(attachAdaptor)

        addBinding.autoCompleteTextView.setOnItemClickListener { parent, _, position, _ ->
            val selectedLabel = parent.getItemAtPosition(position).toString()
            val selectedValue = attachedItems.firstOrNull { it.first == selectedLabel }?.second

            if (selectedValue !== null && !attachedTo.contains(selectedValue) && selectedValue !== "all") {
                attachedTo.add(selectedValue)
            }

            if (selectedValue == "all") {
                attachedTo.clear()
                attachedItems.forEach {
                    if (it.second !== "all") {
                        attachedTo.add(it.second)
                    }
                }
            }

            updateAttachedItem(addBinding, attachedTo, attachedItems)
        }


        addBinding.addChargeButton.setOnClickListener {
            val chargeId = defaultCharge?.id ?: UUID.randomUUID().toString()
            val name = addBinding.addChargeName.text.toString()
            val amount = addBinding.addChargeAmount.text.toString()

            if (name.isNotEmpty() && amount.isNotEmpty()) {
                val charge = Charge(
                    chargeId,
                    name,
                    amount.toDouble(),
                    attachedTo
                )

                loader.dialogTitle.text = "Adding Charge.."
                loader.dialog.show()

                updateCharge(charge).addOnSuccessListener {
                    if (defaultCharge != null) {
                        chargeItemAdaptor.updateCharge(charge)
                    } else {
                        chargeItemAdaptor.addCharge(charge)
                        binding.chargeLists.scrollToPosition(0)
                        binding.chargeNoData.visibility = View.GONE
                    }

                    bottomSheetDialog.dismiss()
                }.addOnFailureListener {
                    MaterialAlertDialogBuilder(this).setTitle("Oops!")
                        .setMessage("Something went wrong while adding the charge. Please try again!")
                        .setPositiveButton("Dismiss") { d, _ ->
                            d.dismiss()
                        }.show()
                }.addOnCompleteListener {
                    loader.dialog.hide()
                }
            } else {
                MaterialAlertDialogBuilder(this).setTitle("Oops!")
                    .setMessage("Please make sure all fields are valid")
                    .setPositiveButton("Dismiss") { d, _ ->
                        d.dismiss()
                    }.show()
            }
        }


        // setting default values
        if (defaultCharge !== null) {
            addBinding.addChargeName.setText(defaultCharge.name)
            addBinding.addChargeAmount.setText(AppUtils.formatDecimal(defaultCharge.amount))

            defaultCharge.appliedTo.forEach {
                attachedTo.add(it)
            }

            updateAttachedItem(addBinding, attachedTo, attachedItems)
        }
    }

    private fun updateAttachedItem(
        addBinding: BottomAddChargeBinding,
        items: MutableList<String>,
        attachedItems: List<Pair<String, String>>
    ) {
        addBinding.addRoomAttachedWrapper.visibility =
            if (items.isNotEmpty()) View.VISIBLE else View.GONE
        addBinding.addRoomAttached.removeAllViews()

        items.forEach { value ->
            val itemView = ListChargeAttachedCardBinding.inflate(layoutInflater)

            itemView.listChargeAttachedName.text =
                attachedItems.firstOrNull { it.second == value }?.first

            itemView.listChargeAttachedRemove.setOnClickListener {
                items.remove(value)
                updateAttachedItem(addBinding, items, attachedItems)
            }

            addBinding.addRoomAttached.addView(itemView.root)
        }

        addBinding.autoCompleteTextView.setAdapter(
            ArrayAdapter(
                this,
                android.R.layout.simple_dropdown_item_1line,
                attachedItems.filter { !items.contains(it.second) }.map { it.first }
            )
        )
    }

    private fun updateCharge(charge: Charge): Task<Void> {
        return Constants.getDbRef().collection(Constants.CHARGE_COLLECTION).document(charge.id)
            .set(charge, SetOptions.merge())
    }
}