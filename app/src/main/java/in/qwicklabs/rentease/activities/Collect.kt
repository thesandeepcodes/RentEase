package `in`.qwicklabs.rentease.activities

import android.app.DatePickerDialog
import android.icu.util.Calendar
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import `in`.qwicklabs.rentease.constants.Constants
import `in`.qwicklabs.rentease.databinding.ActivityCollectBinding
import `in`.qwicklabs.rentease.model.Room
import `in`.qwicklabs.rentease.model.Tenant
import `in`.qwicklabs.rentease.utils.AppUtils
import `in`.qwicklabs.rentease.utils.GenerateBill
import `in`.qwicklabs.rentease.utils.Loader
import java.util.Date

class Collect : AppCompatActivity() {
    private lateinit var binding: ActivityCollectBinding
    private lateinit var rooms: List<Room>
    private var tenants: List<Tenant> = emptyList()

    private var date: Date = Timestamp.now().toDate()

    private lateinit var loader: Loader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityCollectBinding.inflate(layoutInflater)
        loader = Loader(this)

        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        Rooms().getRooms {
            rooms = it

            binding.collectRoomSelect.setAdapter(
                ArrayAdapter(
                    this,
                    android.R.layout.simple_dropdown_item_1line,
                    rooms.map { r -> r.name }
                )
            )

            binding.collectRoomSelect.setOnItemClickListener { parent, _, position, _ ->
                val room = rooms.firstOrNull { room ->
                    parent.getItemAtPosition(position).toString() == room.name
                }

                if (room !== null) {

                    loader.dialogTitle.text = "Fetching.."
                    loader.dialog.show()

                    Constants.getDbRef().collection(Constants.TENANT_COLLECTION)
                        .whereEqualTo("roomId", room.id).whereEqualTo("active", true).get().addOnSuccessListener { snapshots ->
                        tenants =
                            snapshots.documents.mapNotNull { t -> t.toObject(Tenant::class.java) }

                        if (tenants.isNotEmpty()) {
                            binding.collectTenantSelectLayout.visibility = View.VISIBLE
                            binding.collectTenantSelect.setAdapter(
                                ArrayAdapter(
                                    this,
                                    android.R.layout.simple_dropdown_item_1line,
                                    tenants.map { t -> t.name })
                            )
                        } else {
                            AppUtils.alert(
                                this,
                                "No Tenants",
                                "No tenants attached to room: ${room.name}"
                            )
                        }
                    }.addOnFailureListener {
                        AppUtils.alert(this, "Error", "Unable to fetch tenants. Please try again")
                    }.addOnCompleteListener {
                        loader.dialog.dismiss()
                    }
                }
            }

            binding.collectDate.setText(`in`.qwicklabs.rentease.utils.Date.formatTimestamp(Timestamp(date), "MMM dd, yyyy"))

            binding.collectDate.setOnClickListener {
                val picker = DatePickerDialog(this)

                picker.setOnDateSetListener { _, year, month, dayOfMonth ->
                    val calendar = Calendar.getInstance()
                    calendar.set(year, month, dayOfMonth, 0, 0, 0)

                    date = calendar.time

                    binding.collectDate.setText(
                        `in`.qwicklabs.rentease.utils.Date.formatTimestamp(
                            Timestamp(calendar.time),
                            "MMM dd, yyyy"
                        )
                    )
                }

                picker.show()
            }

            binding.collectButton.setOnClickListener { collect() }
        }
    }

    private fun collect() {
        val room = rooms.firstOrNull { it.name == binding.collectRoomSelect.text.toString() }
        val amount = binding.collectAmount.text.toString()
        val tenant = tenants.firstOrNull { it.name == binding.collectTenantSelect.text.toString() }

        if (room == null || amount.isEmpty() || date == null || tenant == null) {
            AppUtils.alert(
                this,
                "Wait!",
                "All fields are mandatory. Please make sure you have provided all the fields"
            )
            return
        }

        if (amount.toDouble() < 0) {
            AppUtils.alert(this, "Wait!", "Amount should be positive number")
            return
        }

        loader.dialogTitle.text = "Collecting.."
        loader.dialog.show()

        GenerateBill.collectBill(amount.toDouble(), room, tenant, date, { e ->
            loader.dialog.dismiss()
            AppUtils.alert(this, "Oops", "Unable to collect payment: ${e.message}")
        }) {
            loader.dialog.dismiss()
            finish()
        }
    }
}