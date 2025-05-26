package `in`.qwicklabs.rentease.fragments

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import `in`.qwicklabs.rentease.R
import `in`.qwicklabs.rentease.activities.ViewBill
import `in`.qwicklabs.rentease.constants.Constants
import `in`.qwicklabs.rentease.databinding.BottomBillShareTenantsBinding
import `in`.qwicklabs.rentease.databinding.FragmentDashboardBinding
import `in`.qwicklabs.rentease.databinding.ListBillShareTenantProfileBinding
import `in`.qwicklabs.rentease.databinding.ListDashboardDueRoomBinding
import `in`.qwicklabs.rentease.model.Bill
import `in`.qwicklabs.rentease.model.Charge
import `in`.qwicklabs.rentease.model.Room
import `in`.qwicklabs.rentease.model.Tenant
import `in`.qwicklabs.rentease.utils.AppUtils
import `in`.qwicklabs.rentease.utils.Date
import `in`.qwicklabs.rentease.utils.GenerateBill

class Dashboard : Fragment() {
    private lateinit var binding: FragmentDashboardBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentDashboardBinding.inflate(inflater, container, false)

        showDues()

        return binding.root
    }

    private fun showDues() {
        var removedViews = false
        var totalIncome = 0.0

        Constants.getDbRef().collection(Constants.EXPENSE).document(Constants.EXPENSE).get()
            .addOnSuccessListener { snapshot ->
                if (!isAdded) return@addOnSuccessListener

                val totalExpense = snapshot.getDouble("expense") ?: 0.0
                binding.totalExpense.text = "₹${AppUtils.formatDecimal(totalExpense)}"
            }

        Constants.getDbRef().collection(Constants.ROOM_COLLECTION).get()
            .addOnSuccessListener { querySnapshot ->
                if (!isAdded) return@addOnSuccessListener
                val rooms = querySnapshot.documents.mapNotNull { it.toObject(Room::class.java) }

                if (rooms.isEmpty()) {
                    binding.dashLoader.visibility = View.GONE
                    binding.dashWrapper.visibility = View.VISIBLE
                    return@addOnSuccessListener
                }

                val dueViews = mutableMapOf<String, View>()

                fun addViews(){
                    dueViews.toSortedMap().forEach { (t, view) ->
                        binding.dashboardDueLists.addView(view)
                    }
                }


                var completed = 0

                rooms.sortedBy { it.name }.forEachIndexed { roomIndex, room ->
                    totalIncome += room.revenue

                    Constants.getDbRef().collection(Constants.BILL_COLLECTION)
                        .whereEqualTo("roomId", room.id)
                        .get()
                        .addOnSuccessListener { billSnapshots ->
                            if (!isAdded) return@addOnSuccessListener

                            val bills = billSnapshots.documents.mapNotNull { it.toObject(Bill::class.java) }
                            val due = if (bills.isNotEmpty()) GenerateBill.dues(bills) else 0.0

                            if (due.toLong() > 0) {
                                activity?.runOnUiThread {
                                    if (!isAdded) return@runOnUiThread

                                    if (!removedViews) {
                                        binding.dashboardDueLists.removeAllViews()
                                        removedViews = true
                                    }

                                    val dueList = ListDashboardDueRoomBinding.inflate(
                                        LayoutInflater.from(binding.root.context),
                                        binding.dashboardDueLists,
                                        false
                                    )

                                    dueList.dashListDueAmount.text = "₹" + Math.round(due)
                                    dueList.dashListRoomName.text = room.name

                                    dueList.dashListCall.setOnClickListener {
                                        getTenants(room.id) { tenants ->
                                            if (isAdded) showTenants(tenants)
                                        }
                                    }

                                    dueList.root.setOnClickListener {
                                        Intent(context, ViewBill::class.java).apply {
                                            putExtra("billId", bills.map { it.id }.toTypedArray())
                                        }.let { startActivity(it) }
                                    }

                                    dueViews[room.name] = dueList.root
                                }
                            }
                        }
                        .addOnCompleteListener {
                            completed++

                            if (completed == rooms.size) {
                                addViews()

                                activity?.runOnUiThread {
                                    if (isAdded) {
                                        binding.dashLoader.visibility = View.GONE
                                        binding.dashWrapper.visibility = View.VISIBLE
                                        binding.totalIncome.text = "₹${AppUtils.formatDecimal(totalIncome)}"
                                    }
                                }
                            }
                        }
                }
            }
    }

    private fun showTenants(tenants: List<Tenant>) {
        if(!isAdded) return

        val bottomSheetDialog = BottomSheetDialog(requireContext())

        val content = BottomBillShareTenantsBinding.inflate(layoutInflater)

        content.billShareTitle.text = "Call to"

        content.billShareTenants.removeAllViews()

        if (tenants.isEmpty()) {
            AppUtils.alert(
                requireContext(),
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
                startActivity(Intent(Intent.ACTION_DIAL, "tel:${tenant.phone}".toUri()))
            }

            content.billShareTenants.addView(tenantView.root)
        }

        content.billShareClose.setOnClickListener { bottomSheetDialog.dismiss() }
        bottomSheetDialog.setContentView(content.root)
        bottomSheetDialog.show()
    }

    private fun getTenants(roomId: String, onComplete: (tenants: List<Tenant>) -> Unit) {
        Constants.getDbRef().collection(Constants.TENANT_COLLECTION).whereEqualTo("roomId", roomId)
            .whereEqualTo("active", true).get().addOnSuccessListener {
                onComplete(it.documents.mapNotNull { tenant -> tenant.toObject(Tenant::class.java) })
            }
    }
}