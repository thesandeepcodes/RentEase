package `in`.qwicklabs.rentease.activities

import android.app.DatePickerDialog
import android.icu.util.Calendar
import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import `in`.qwicklabs.rentease.R
import `in`.qwicklabs.rentease.adaptor.MeterReadingItemAdaptor
import `in`.qwicklabs.rentease.constants.Constants
import `in`.qwicklabs.rentease.model.MeterReading
import `in`.qwicklabs.rentease.databinding.ActivityMeterReadingBinding
import `in`.qwicklabs.rentease.databinding.BottomMeterReadingBinding
import `in`.qwicklabs.rentease.utils.AppUtils
import `in`.qwicklabs.rentease.utils.Date
import `in`.qwicklabs.rentease.utils.Loader
import java.util.UUID

class MeterReadingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMeterReadingBinding
    private lateinit var meterId: String
    private lateinit var loader: Loader
    private lateinit var bottomSheetDialog: BottomSheetDialog

    private lateinit var readingAdapter: MeterReadingItemAdaptor
    private lateinit var readings: MutableList<MeterReading>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMeterReadingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loader = Loader(this)

        meterId = intent.getStringExtra("meterId") ?: ""
        val meterName = intent.getStringExtra("meterName") ?: "Readings"
        binding.toolbar.title = meterName

        if(meterId.isEmpty()){
            AppUtils.alert(this, "Oops!", "Could not find readings for unknown meter")
            return
        }

        loader.dialogTitle.text = "Fetching Readings.."
        loader.dialog.show()

        getReadings {
            readings = it

            readingAdapter = MeterReadingItemAdaptor(this, readings)
            binding.meterReadingList.adapter = readingAdapter
            binding.meterReadingList.layoutManager = LinearLayoutManager(this)
            binding.meterReadingList.setHasFixedSize(true)

            loader.dialog.hide()

            activateFilters()
        }

        binding.readingAdd.setOnClickListener {
            updateReading()
        }

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun getReadings(onComplete: (readings: MutableList<MeterReading>) -> Unit){
        val readings = mutableListOf<MeterReading>()

        Constants.getDbRef().collection(Constants.METER_COLLECTION).document(meterId).collection(Constants.METER_READING_COLLECTION).orderBy("createdAt", Query.Direction.DESCENDING).get().addOnSuccessListener {
            val docs = it.documents

            for (doc in docs) {
                val meter = doc.toObject(MeterReading::class.java)
                if (meter !== null) {
                    readings.add(meter)
                }
            }
        }.addOnCompleteListener {
            onComplete(readings)
        }
    }

    fun updateReading(defaultMeterReading: MeterReading? = null){
        bottomSheetDialog = BottomSheetDialog(this)

        val readingBinding = BottomMeterReadingBinding.inflate(layoutInflater)
        bottomSheetDialog.setContentView(readingBinding.root)
        bottomSheetDialog.setCancelable(false)
        bottomSheetDialog.show()

        var readingDate: Timestamp = Timestamp.now()
        var datePicker = DatePickerDialog(this)

        readingBinding.meterReadingClose.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        readingBinding.meterReadingDate.setOnClickListener {
            datePicker.show()
        }

        datePicker.setOnDateSetListener { _, year, month, dayOfMonth ->
            val calendar = Calendar.getInstance()
            calendar.set(year, month, dayOfMonth, 0, 0, 0)
            readingDate = Timestamp(calendar.time)
            readingBinding.meterReadingDate.setText(Date.formatTimestamp(readingDate, "dd MMM, yyyy"))
        }

        if(defaultMeterReading !== null){
            readingBinding.meterReadingValue.setText(AppUtils.formatDecimal(defaultMeterReading.value))
            readingDate = defaultMeterReading.createdAt
        }

        readingBinding.meterReadingDate.setText(Date.formatTimestamp(readingDate, "dd MMM, yyyy"))

        readingBinding.meterReadingButton.setOnClickListener {
            val docId = defaultMeterReading?.id ?: UUID.randomUUID().toString()
            val meterReading = MeterReading(
                docId,
                meterId,
                readingBinding.meterReadingValue.text.toString().toDouble(),
                readingDate
            )

            if(defaultMeterReading == null){
                if (readings.any { it.createdAt.toDate().let { d1 -> meterReading.createdAt.toDate().let { d2 -> d1.year == d2.year && d1.month == d2.month } } }) {
                    AppUtils.alert(this, "Duplicate", "Already added reading for the selected month. Please choose a different date")
                    return@setOnClickListener
                }
            }

            loader.dialogTitle.text = if(defaultMeterReading !== null) "Adding Reading.." else "Updating Reading.."
            loader.dialog.show()

            Constants.getDbRef().collection(Constants.METER_COLLECTION).document(meterId).collection(Constants.METER_READING_COLLECTION).document(docId).set(meterReading, SetOptions.merge()).addOnSuccessListener {
                if(defaultMeterReading == null){
                    readingAdapter.addReading(meterReading)
                    binding.meterReadingList.scrollToPosition(0)
                }else{
                    readingAdapter.updateReading(meterReading)
                }
            }.addOnCompleteListener {
                loader.dialog.hide()
                bottomSheetDialog.dismiss()
            }.addOnFailureListener {
                AppUtils.alert(this, "Error", "We encounter an error while saving the reading. Please try again")
            }
        }
    }

    private fun activateFilters() {
        var asc = false

        binding.meterReadingFilterRecent.setOnClickListener {
            binding.meterReadingFilterRecent.backgroundTintList = ContextCompat.getColorStateList(
                this, if (asc) R.color.white else R.color.primary
            )
            (binding.meterReadingFilterRecent.getChildAt(0) as TextView).setTextColor(
                getColor(if (asc) R.color.gray_800 else R.color.white)
            )

            readings = if (asc) {
                readings.sortedBy { it.createdAt.toDate().time }
            } else {
                readings.sortedByDescending { it.createdAt.toDate().time }
            }.toMutableList()

            readingAdapter = MeterReadingItemAdaptor(this, readings)
            binding.meterReadingList.adapter = readingAdapter

            asc = !asc
        }
    }
}