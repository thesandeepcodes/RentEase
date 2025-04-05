package `in`.qwicklabs.rentease.adaptor

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.marginTop
import androidx.recyclerview.widget.RecyclerView
import `in`.qwicklabs.rentease.R
import `in`.qwicklabs.rentease.activities.ViewBill
import `in`.qwicklabs.rentease.databinding.ListBillBinding
import `in`.qwicklabs.rentease.model.Bill
import `in`.qwicklabs.rentease.model.BillItem
import `in`.qwicklabs.rentease.model.Room
import `in`.qwicklabs.rentease.utils.Date
import `in`.qwicklabs.rentease.utils.GenerateBill

class BillItemAdaptor(
    val context: android.content.Context,
    private val bills: MutableList<BillItem>,
    private val rooms: List<Room>
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_NORMAL = 1
        const val TYPE_MERGED = 2
    }

    override fun getItemViewType(position: Int): Int {
        return when (bills[position]) {
            is BillItem.NormalItem -> TYPE_NORMAL
            is BillItem.MergedItem -> TYPE_MERGED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return when (viewType) {
            TYPE_NORMAL -> NormalViewHolder(ListBillBinding.inflate(inflater, parent, false))
            TYPE_MERGED -> MergedViewHolder(
                inflater.inflate(
                    R.layout.list_bill_wrapper,
                    parent,
                    false
                ) as ViewGroup
            )

            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun getItemCount(): Int = bills.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = bills[position]) {
            is BillItem.NormalItem -> (holder as NormalViewHolder).bind(item.data)
            is BillItem.MergedItem -> (holder as MergedViewHolder).bind(item.data)
        }
    }

    inner class NormalViewHolder(private val binding: ListBillBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(bill: Bill) {
            val generatedBill = GenerateBill(bill)

            binding.listBillRoomName.text =
                rooms.firstOrNull { it.id == bill.roomId }?.name ?: "Unknown"
            binding.listBillPending.visibility = if (bill.paid) View.GONE else View.VISIBLE
            binding.listBillPaid.visibility = if (bill.paid) View.VISIBLE else View.GONE
            binding.listBillCollected.text =
                "₹" + Math.round(if (bill.paid) generatedBill.billAmount else generatedBill.total)

            binding.listBillProgress.progress =
                ((bill.collected / generatedBill.billAmount) * 100).toInt()


            binding.listBillCard.setOnClickListener {
                Intent(context, ViewBill::class.java).apply {
                    putExtra("billId", arrayOf(bill.id))
                }.let { context.startActivity(it) }
            }
        }
    }

    inner class MergedViewHolder(private val viewGroup: ViewGroup) :
        RecyclerView.ViewHolder(viewGroup) {
        fun bind(bills: List<Bill>) {
            val listWrapper = viewGroup.findViewById<LinearLayout>(R.id.list_bill_items)
            val listWrapperRoomName = viewGroup.findViewById<TextView>(R.id.list_bill_room_name)
            val listWrapperViewAll = viewGroup.findViewById<LinearLayout>(R.id.list_bill_view_all)

            listWrapperViewAll.setOnClickListener {
                Intent(context, ViewBill::class.java).apply {
                    putExtra("billId", bills.map { it.id }.toTypedArray())
                }.let { context.startActivity(it) }
            }

            listWrapper.removeAllViews()

            listWrapperRoomName.text =
                rooms.firstOrNull { it.id == bills[0].roomId }?.name ?: "All Dues"

            bills.forEach { bill ->
                val viewBinding = ListBillBinding.inflate(
                    LayoutInflater.from(viewGroup.context),
                    viewGroup,
                    false
                )
                NormalViewHolder(viewBinding).bind(bill)
                viewBinding.listBillRoomName.text = Date.formatTimestamp(bill.createdAt, "MMMM")

                val layoutParams = viewBinding.root.layoutParams as ViewGroup.MarginLayoutParams

                layoutParams.topMargin = 5
                layoutParams.bottomMargin = 5
                layoutParams.leftMargin = 0
                layoutParams.rightMargin = 0
                viewBinding.root.layoutParams = layoutParams
                viewBinding.listBillCard.strokeWidth = 0

                listWrapper.addView(viewBinding.root)
            }
        }
    }
}
