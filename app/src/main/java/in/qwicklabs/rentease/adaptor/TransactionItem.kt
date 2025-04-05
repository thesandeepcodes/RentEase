package `in`.qwicklabs.rentease.adaptor

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import `in`.qwicklabs.rentease.R
import `in`.qwicklabs.rentease.constants.Constants
import `in`.qwicklabs.rentease.model.Tenant
import `in`.qwicklabs.rentease.model.Transaction
import `in`.qwicklabs.rentease.model.TransactionType
import `in`.qwicklabs.rentease.utils.AppUtils
import `in`.qwicklabs.rentease.utils.Date
import `in`.qwicklabs.rentease.utils.GenerateBill
import `in`.qwicklabs.rentease.utils.Loader

class TransactionItemAdaptor(private val context: Context, private val transactions: MutableList<Transaction>): RecyclerView.Adapter<TransactionItemAdaptor.TransactionViewHolder>(){

    fun removeTransaction(id: String){
        val index = transactions.indexOfFirst { it.id == id }

        if (index >= 0) {
            transactions.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_transaction, parent, false)
        return TransactionViewHolder(view)
    }

    override fun getItemCount(): Int = transactions.size


    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val transaction = transactions[position]

        if(transaction.type == TransactionType.EXPENSE){
            holder.tranName.text = transaction.name

            holder.tranListExpense.visibility = View.VISIBLE
            holder.tranListIncome.visibility = View.GONE

            holder.tranAmount.setTextColor(ContextCompat.getColor(context, R.color.colorDanger))
        }else{
            holder.tranName.text = "Loading.."

            transaction.tenantId?.let {
                Constants.getDbRef().collection(Constants.TENANT_COLLECTION).document(it).get().addOnSuccessListener {tSnapshot->
                    val tenant = tSnapshot.toObject(Tenant::class.java)
                    if(tenant !== null) holder.tranName.text = tenant.name
                }
            }

            holder.tranListExpense.visibility = View.GONE
            holder.tranListIncome.visibility = View.VISIBLE
            holder.tranAmount.setTextColor(ContextCompat.getColor(context, R.color.colorSuccess))
        }

        holder.tranAmount.text = "₹" + AppUtils.formatDecimal(transaction.amount)
        holder.tranDate.text = Date.formatTimestamp(transaction.createdAt, "MMM dd, yyyy")
    }

    inner class TransactionViewHolder(view: View): RecyclerView.ViewHolder(view){
        val tranListExpense: MaterialCardView = view.findViewById(R.id.transaction_list_expense)
        val tranListIncome: MaterialCardView = view.findViewById(R.id.transaction_list_income)
        val tranName: TextView = view.findViewById(R.id.transaction_list_name)
        val tranAmount: TextView = view.findViewById(R.id.transaction_list_amount)
        val tranDate: TextView = view.findViewById(R.id.transaction_list_date)


        init {

            view.setOnLongClickListener {
                val transaction = transactions[adapterPosition]

                MaterialAlertDialogBuilder(context)
                    .setTitle("Delete Transaction?")
                    .setMessage("Are you sure you want to permanently delete this transaction?")
                    .setNegativeButton("No"){d,_->d.dismiss()}
                    .setPositiveButton("Yes, Delete"){d,_->
                        d.dismiss()

                        val loader = Loader(context)
                        loader.dialogTitle.text = "Deleting.."
                        loader.dialog.show()

                        GenerateBill.deleteTransaction(transaction, {e->
                            loader.dialog.dismiss()
                            AppUtils.alert(context, "Oops!", "Unable to delete: ${e.message}")
                        }){
                            loader.dialog.dismiss()
                            removeTransaction(transaction.id)
                        }
                    }.show()

                true
            }
        }
    }
}