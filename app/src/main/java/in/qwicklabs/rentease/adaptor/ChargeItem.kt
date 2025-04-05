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
import `in`.qwicklabs.rentease.activities.Charges
import `in`.qwicklabs.rentease.constants.Constants
import `in`.qwicklabs.rentease.model.Charge
import `in`.qwicklabs.rentease.utils.AppUtils
import `in`.qwicklabs.rentease.utils.Loader

class ChargeItemAdaptor(private val context: Context, private val charges: MutableList<Charge>) :
    RecyclerView.Adapter<ChargeItemAdaptor.ChargeItemViewHolder>() {

    fun addCharge(charge: Charge) {
        charges.add(0, charge)
        notifyItemInserted(0)
    }

    fun removeCharge(chargeId: String) {
        val index = charges.indexOfFirst { it.id == chargeId }

        if (index >= 0) {
            charges.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    fun updateCharge(charge: Charge) {
        val index = charges.indexOfFirst { it.id == charge.id }
        charges[index] = charge

        notifyItemChanged(index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChargeItemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_charge, parent, false)
        return ChargeItemViewHolder(view)
    }

    override fun getItemCount(): Int {
        return charges.size
    }

    override fun onBindViewHolder(holder: ChargeItemViewHolder, position: Int) {
        val charge = charges[position]

        holder.name.text = charge.name
        holder.amount.text = "₹" + AppUtils.formatDecimal(charge.amount)
        holder.appliedTo.text = charge.appliedTo.joinToString(separator = ", ") {
            (context as Charges).attachedItems.firstOrNull { a -> a.second == it }?.first
                ?: "Unknown"
        }
    }


    inner class ChargeItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.list_charge_label)
        val amount: TextView = view.findViewById(R.id.list_charge_amount)
        val appliedTo: TextView = view.findViewById(R.id.list_charge_applied_on)

        init {
            val edit = view.findViewById<ImageView>(R.id.list_charge_edit)
            val delete = view.findViewById<ImageView>(R.id.list_charge_delete)

            val loader = Loader(context)

            edit.setOnClickListener {
                val charge = charges[adapterPosition]
                (context as Charges).showChargeDialog(charge)
            }

            delete.setOnClickListener {
                val charge = charges[adapterPosition]

                MaterialAlertDialogBuilder(context).setTitle("Delete").setMessage("Are you sure? This will delete this charge permanently").setNegativeButton("No"){d,_->d.dismiss()}.setPositiveButton("Yes, Delete"){d,_->
                    d.dismiss()

                    loader.dialog.show()

                    Constants.getDbRef().collection(Constants.CHARGE_COLLECTION).document(charge.id).delete().addOnCompleteListener {
                        loader.dialog.dismiss()
                    }.addOnSuccessListener {
                        removeCharge(charge.id)
                    }.addOnFailureListener {
                        MaterialAlertDialogBuilder(context).setTitle("Oops!").setMessage("An unexpected error occurred while deleting the charge. Please try again!").setPositiveButton("Dismiss"){d,_->
                            d.dismiss()
                        }.show()
                    }
                }.show()
            }
        }
    }
}