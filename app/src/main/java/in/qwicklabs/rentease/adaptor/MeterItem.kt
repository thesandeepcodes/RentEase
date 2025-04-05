package `in`.qwicklabs.rentease.adaptor

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import `in`.qwicklabs.rentease.R
import `in`.qwicklabs.rentease.activities.MeterReadingActivity
import `in`.qwicklabs.rentease.activities.Meters
import `in`.qwicklabs.rentease.constants.Constants
import `in`.qwicklabs.rentease.model.Meter
import `in`.qwicklabs.rentease.utils.AppUtils
import `in`.qwicklabs.rentease.utils.Loader

class MeterItemAdaptor(private val context: Context, private val meters: MutableList<Meter>) :
    RecyclerView.Adapter<MeterItemAdaptor.MeterItemViewHolder>() {

    fun addMeter(meter: Meter) {
        meters.add(0, meter)
        notifyItemInserted(0)
    }

    fun removeMeter(meterId: String) {
        val index = meters.indexOfFirst { it.id == meterId }

        if (index >= 0) {
            meters.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    fun updateMeter(meter: Meter) {
        val index = meters.indexOfFirst { it.id == meter.id }
        meters[index] = meter

        notifyItemChanged(index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MeterItemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_meter, parent, false)
        return MeterItemViewHolder(view)
    }

    override fun getItemCount(): Int {
        return meters.size
    }

    override fun onBindViewHolder(holder: MeterItemViewHolder, position: Int) {
        val meter = meters[position]

        holder.name.text = meter.name
        holder.unitCost.text = "₹" + AppUtils.formatDecimal(meter.unitCost)
        holder.appliedTo.text = meter.appliedTo.map { (key, value)-> "${(context as Meters).attachedItems.firstOrNull { it.second == key }?.first} :: $value"}.toList().joinToString(separator = ",  ") { it }
    }


    inner class MeterItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.list_meter_label)
        val unitCost: TextView = view.findViewById(R.id.list_meter_unit_cost)
        val appliedTo: TextView = view.findViewById(R.id.list_meter_applied_on)

        init {
            val edit = view.findViewById<ImageView>(R.id.list_meter_edit)
            val delete = view.findViewById<ImageView>(R.id.list_meter_delete)

            val loader = Loader(context)

            edit.setOnClickListener {
                val meter = meters[adapterPosition]
                (context as Meters).showMeterDialog(meter)
            }

            delete.setOnClickListener {
                val meter = meters[adapterPosition]

                MaterialAlertDialogBuilder(context).setTitle("Delete").setMessage("Are you sure? This will delete this meter permanently. This will delete all the readings within this meter.").setNegativeButton("No"){d,_->d.dismiss()}.setPositiveButton("Yes, Delete"){d,_->
                    d.dismiss()

                    loader.dialog.show()

                    Constants.getDbRef().collection(Constants.METER_COLLECTION).document(meter.id).delete().addOnCompleteListener {
                        loader.dialog.dismiss()
                    }.addOnSuccessListener {
                        removeMeter(meter.id)
                    }.addOnFailureListener {
                        MaterialAlertDialogBuilder(context).setTitle("Oops!").setMessage("An unexpected error occurred while deleting the meter. Please try again!").setPositiveButton("Dismiss"){d,_->
                            d.dismiss()
                        }.show()
                    }
                }.show()
            }


            view.setOnClickListener {
                val meter = meters[adapterPosition]
                Intent(context, MeterReadingActivity::class.java).apply {
                    putExtra("meterId", meter.id)
                    putExtra("meterName", meter.name)
                }.let{
                    context.startActivity(it)
                }
            }
        }
    }
}