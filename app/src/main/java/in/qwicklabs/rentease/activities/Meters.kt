package `in`.qwicklabs.rentease.activities

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.SeekBar
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.tasks.Task
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.Timestamp
import com.google.firebase.firestore.SetOptions
import `in`.qwicklabs.rentease.adaptor.MeterItemAdaptor
import `in`.qwicklabs.rentease.constants.Constants
import `in`.qwicklabs.rentease.databinding.ActivityMetersBinding
import `in`.qwicklabs.rentease.databinding.BottomAddMeterBinding
import `in`.qwicklabs.rentease.databinding.DialogMeterSeekbarBinding
import `in`.qwicklabs.rentease.databinding.ListMeterAttachedCardBinding
import `in`.qwicklabs.rentease.model.Meter
import `in`.qwicklabs.rentease.utils.AppUtils
import `in`.qwicklabs.rentease.utils.Loader
import java.util.UUID

class Meters : AppCompatActivity() {
    private lateinit var binding: ActivityMetersBinding
    private lateinit var meterItemAdaptor: MeterItemAdaptor
    private lateinit var loader: Loader

    var attachedItems: List<Pair<String, String>> = emptyList()

    private lateinit var bottomSheetDialog: BottomSheetDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMetersBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bottomSheetDialog = BottomSheetDialog(this)
        loader = Loader(this)

        Rooms().getRooms { rooms ->
            attachedItems = listOf("All" to "all") + rooms.map {
                it.name to it.id
            }.toList()

            getMeters { meters ->
                binding.meterLoader.visibility = View.GONE
                binding.meterLists.visibility = View.VISIBLE

                meterItemAdaptor = MeterItemAdaptor(this, meters.sortedBy { it.name }.toMutableList())
                binding.meterLists.adapter = meterItemAdaptor
                binding.meterLists.setHasFixedSize(true)
                binding.meterLists.layoutManager = LinearLayoutManager(this)
            }
        }

        binding.meterAdd.setOnClickListener {
            showMeterDialog()
        }

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun getMeters(onComplete: (MutableList<Meter>) -> Unit) {
        val meters = mutableListOf<Meter>()

        Constants.getDbRef().collection(Constants.METER_COLLECTION).get().addOnSuccessListener {
            val docs = it.documents

            for (doc in docs) {
                val meter = doc.toObject(Meter::class.java)
                if (meter !== null) {
                    meters.add(meter)
                }
            }
        }.addOnCompleteListener {
            onComplete(meters)
        }
    }

    fun showMeterDialog(defaultMeter: Meter? = null) {
        if (attachedItems.isEmpty()) return

        bottomSheetDialog = BottomSheetDialog(this)

        val addBinding = BottomAddMeterBinding.inflate(layoutInflater)
        bottomSheetDialog.setContentView(addBinding.root)
        bottomSheetDialog.setCancelable(false)
        bottomSheetDialog.show()

        val attachedTo: MutableMap<String, Int> = mutableMapOf()

        addBinding.addMeterClose.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        addBinding.addMeterButton.text =
            if (defaultMeter == null) "Add Meter" else "Update Meter"
        addBinding.addMeterTitle.text =
            if (defaultMeter == null) "Add Meter" else "Update Meter"

        val labels = attachedItems.map { it.first }
        val attachAdaptor = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, labels)
        addBinding.autoCompleteTextView.setAdapter(attachAdaptor)

        addBinding.autoCompleteTextView.setOnItemClickListener { parent, _, position, _ ->
            val selectedLabel = parent.getItemAtPosition(position).toString()
            val selectedValue = attachedItems.firstOrNull { it.first == selectedLabel }?.second

            if (selectedValue !== null && !attachedTo.contains(selectedValue) && selectedValue !== "all") {
                attachedTo[selectedValue] = 100/(attachedItems.size - 1)
            }

            if (selectedValue == "all") {
                attachedTo.clear()

                attachedItems.forEach {
                    if (it.second !== "all") {
                        attachedTo[it.second] = 100/(attachedItems.size - 1)
                    }
                }
            }

            updateAttachedItem(addBinding, attachedTo, attachedItems)
        }


        addBinding.addMeterButton.setOnClickListener {
            val meterId = defaultMeter?.id ?: UUID.randomUUID().toString()
            val name = addBinding.addMeterName.text.toString()
            val unit = addBinding.addMeterUnit.text.toString()

            if (name.isNotEmpty() && unit.isNotEmpty()) {
                val meter = Meter(
                    meterId,
                    name,
                    unit.toFloatOrNull() ?: 0f,
                    attachedTo,
                    Timestamp.now()
                )

                loader.dialogTitle.text = "Adding Meter.."
                loader.dialog.show()

                updateMeter(meter).addOnSuccessListener {
                    if (defaultMeter != null) {
                        meterItemAdaptor.updateMeter(meter)
                    } else {
                        meterItemAdaptor.addMeter(meter)
                        binding.meterLists.scrollToPosition(0)
                    }

                    bottomSheetDialog.dismiss()
                }.addOnFailureListener {
                    MaterialAlertDialogBuilder(this).setTitle("Oops!")
                        .setMessage("Something went wrong while adding the meter. Please try again!")
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
        if (defaultMeter !== null) {
            addBinding.addMeterName.setText(defaultMeter.name)
            addBinding.addMeterUnit.setText(AppUtils.formatDecimal(defaultMeter.unitCost))

            defaultMeter.appliedTo.forEach { (key, value) ->
                attachedTo[key] = value
            }

            updateAttachedItem(addBinding, attachedTo, attachedItems)
        }
    }

    private fun updateAttachedItem(
        addBinding: BottomAddMeterBinding,
        items: MutableMap<String, Int>,
        attachedItems: List<Pair<String, String>>
    ) {
        addBinding.addRoomAttachedWrapper.visibility =
            if (items.isNotEmpty()) View.VISIBLE else View.GONE
        addBinding.addRoomAttached.removeAllViews()

        items.forEach { (id, share) ->
            val itemView = ListMeterAttachedCardBinding.inflate(layoutInflater)

            itemView.listMeterAttachedName.text =
                attachedItems.firstOrNull { it.second == id }?.first
            itemView.listMeterAttachedShare.setText(items[id].toString())

            val seekBarDialog = Dialog(this)

            itemView.listMeterAttachedShare.setOnClickListener {
                val meterSeekbar = DialogMeterSeekbarBinding.inflate(layoutInflater)
                meterSeekbar.meterShareProgress.text = "Share $share"
                meterSeekbar.meterShareRoomName.text = attachedItems.firstOrNull { it.second == id }?.first

                items[id]?.let {
                    meterSeekbar.meterShareSeekbar.progress = it
                    meterSeekbar.meterShareSeekbar.max = 100 - (items.filter {item -> item.key !== id }.values.reduceOrNull{ acc, i -> acc + i } ?: 0)
                }

                meterSeekbar.meterShareSeekbar.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        meterSeekbar.meterShareProgress.text = "Share $progress"
                        items[id] = progress
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    }

                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        updateAttachedItem(addBinding, items, attachedItems)
                    }
                })

                seekBarDialog.setContentView(meterSeekbar.root)
                seekBarDialog.show()
            }

            itemView.listMeterAttachedRemove.setOnClickListener {
                items.remove(id)
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

    private fun updateMeter(meter: Meter): Task<Void> {
        return Constants.getDbRef().collection(Constants.METER_COLLECTION).document(meter.id)
            .set(meter)
    }
}