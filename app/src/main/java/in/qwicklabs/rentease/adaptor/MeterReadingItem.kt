package `in`.qwicklabs.rentease.adaptor

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import `in`.qwicklabs.rentease.R
import `in`.qwicklabs.rentease.activities.MeterReadingActivity
import `in`.qwicklabs.rentease.constants.Constants
import `in`.qwicklabs.rentease.model.MeterReading
import `in`.qwicklabs.rentease.utils.AppUtils
import `in`.qwicklabs.rentease.utils.Date
import `in`.qwicklabs.rentease.utils.Loader

class MeterReadingItemAdaptor(val context: Context, val readings: MutableList<MeterReading>) :
    RecyclerView.Adapter<MeterReadingItemAdaptor.MeterReadingViewHolder>() {
    fun addReading(reading: MeterReading) {
        readings.add(0, reading)
        notifyItemInserted(0)
    }

    fun removeReading(readingId: String) {
        val index = readings.indexOfFirst { it.id == readingId }

        if (index >= 0) {
            readings.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    fun updateReading(reading: MeterReading) {
        val index = readings.indexOfFirst { it.id == reading.id }
        readings[index] = reading

        notifyItemChanged(index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MeterReadingViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.list_meter_reading, parent, false)
        return MeterReadingViewHolder(view)
    }

    override fun getItemCount(): Int {
        return readings.size
    }

    override fun onBindViewHolder(holder: MeterReadingViewHolder, position: Int) {
        val reading = readings[position]

        holder.reading.text = AppUtils.formatDecimal(reading.value)
        holder.date.text = Date.formatTimestamp(reading.createdAt)
    }

    inner class MeterReadingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val reading: TextView = view.findViewById(R.id.list_reading_value)
        val date: TextView = view.findViewById(R.id.list_reading_date)
        private val edit: ImageView = view.findViewById(R.id.list_reading_edit)
        private val delete: ImageView = view.findViewById(R.id.list_reading_delete)

        init {
            edit.setOnClickListener {
                val reading = readings[adapterPosition]
                (context as MeterReadingActivity).updateReading(reading)
            }

            delete.setOnClickListener {
                MaterialAlertDialogBuilder(context).setTitle("Delete?").setMessage("Are you sure you want to delete this reading?").setNegativeButton("No"){d,_->d.dismiss()}.setPositiveButton("Yes, Delete"){d,_->
                    d.dismiss()

                    val reading = readings[adapterPosition]
                    val loader = Loader(context)

                    loader.dialogTitle.text = "Deleting.."
                    loader.dialog.show()

                    Constants.getDbRef().collection(Constants.METER_READING_COLLECTION).document(reading.id).delete().addOnSuccessListener {
                        removeReading(reading.id)
                    }.addOnFailureListener {
                        AppUtils.alert(context, "Oops!", "Something went wrong while deleting the reading. Please try again")
                    }.addOnCompleteListener {
                        loader.dialog.hide()
                    }
                }.show()
            }
        }
    }
}