package `in`.qwicklabs.rentease.fragments

import android.app.DatePickerDialog
import android.icu.util.Calendar
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.firebase.Timestamp
import `in`.qwicklabs.rentease.activities.Rooms
import `in`.qwicklabs.rentease.adaptor.BillItemAdaptor
import `in`.qwicklabs.rentease.constants.Constants
import `in`.qwicklabs.rentease.databinding.FragmentBillsBinding
import `in`.qwicklabs.rentease.model.Bill
import `in`.qwicklabs.rentease.model.BillItem
import `in`.qwicklabs.rentease.model.Room
import `in`.qwicklabs.rentease.utils.Date
import `in`.qwicklabs.rentease.utils.GenerateBill
import `in`.qwicklabs.rentease.utils.Loader

class Bills : Fragment() {
    private lateinit var binding: FragmentBillsBinding

    private lateinit var paidBillItems: MutableList<BillItem>
    private lateinit var unpaidBillItems: MutableList<BillItem>

    private lateinit var billItemAdaptor: BillItemAdaptor

    private val billsRef = Constants.getDbRef().collection(Constants.BILL_COLLECTION)

    private var date = Calendar.getInstance()
    private lateinit var rooms: MutableList<Room>

    private lateinit var loader: Loader

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentBillsBinding.inflate(layoutInflater, container, false)

        binding.billTabs.apply {
            addTab(newTab().setText("Unpaid"))
            addTab(newTab().setText("Paid"))
        }

        loader = Loader(requireContext())

        Rooms().getRooms { r ->
            rooms = r

            loadBills()
        }

        binding.billMonth.text = Date.formatTimestamp(Timestamp(date.time))

        binding.billMonth.setOnClickListener {
            val calendar = Calendar.getInstance()

            calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
            val maxDate = calendar.timeInMillis

            val picker = DatePickerDialog(requireContext(), { _, year, month, dayOfMonth ->
                val selectedCalendar = Calendar.getInstance()
                selectedCalendar.set(year, month, dayOfMonth, 0, 0, 0)
                date = selectedCalendar

                binding.billMonth.text = Date.formatTimestamp(Timestamp(date.time))
                loadBills()
            }, date.get(Calendar.YEAR), date.get(Calendar.MONTH), date.get(Calendar.DAY_OF_MONTH))

            picker.datePicker.maxDate = maxDate

            picker.show()
        }

        binding.billMonthNext.setOnClickListener {
            val calendar = Calendar.getInstance()

            calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
            val maxDate = calendar.timeInMillis

            date.add(Calendar.MONTH, 1)

            if (date.timeInMillis < maxDate) {
                loadBills()
                binding.billMonth.text = Date.formatTimestamp(Timestamp(date.time))
            } else {
                date.add(Calendar.MONTH, -1)
            }
        }

        binding.billMonthBack.setOnClickListener {
            date.add(Calendar.MONTH, -1)

            loadBills()
            binding.billMonth.text = Date.formatTimestamp(Timestamp(date.time))
        }

        return binding.root
    }

    private fun loadBills() {
        binding.billLoaderWrapper.visibility = View.VISIBLE
        binding.billTabLists.visibility = View.GONE
        binding.billGenerateWrapper.visibility = View.GONE
        binding.billNotFound.visibility = View.GONE

        getBills(date) { paid, unpaid ->
            unpaidBillItems = unpaid.sortedBy { billItem ->
                val roomId = when (billItem) {
                    is BillItem.NormalItem -> billItem.data.roomId
                    is BillItem.MergedItem -> billItem.data.first().roomId
                }

                rooms.find { it.id == roomId }?.name ?: ""
            }.toMutableList()
            paidBillItems = paid.toMutableList()

            binding.billLoaderWrapper.visibility = View.GONE

            binding.billTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    when (tab?.position) {
                        0 -> showBills(unpaidBillItems)
                        1 -> showBills(paidBillItems, "paid")
                    }
                }

                override fun onTabUnselected(tab: TabLayout.Tab?) {}
                override fun onTabReselected(tab: TabLayout.Tab?) {}
            })

            if (binding.billTabs.selectedTabPosition == 0) {
                showBills(unpaidBillItems)
            } else {
                showBills(paidBillItems, "paid")
            }

            binding.billGenerate.setOnClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Added Readings?")
                    .setMessage("Did you add meter readings for all the rooms for this billing month?")
                    .setNegativeButton("No") { d, _ -> d.dismiss() }
                    .setPositiveButton("Yes") { d, _ ->
                        d.dismiss()

                        loader.dialogTitle.text = "Generating.."
                        loader.dialog.show()

                        GenerateBill.generateAll(date) {
                            loader.dialog.dismiss()

                            // call the load bills to fetch updated bills
                            loadBills()
                        }
                    }.show()
            }
        }
    }

    private fun showBills(items: MutableList<BillItem>, type: String = "unpaid") {
        if (!isAdded) return

        if (items.isEmpty()) {
            if (type == "unpaid" && paidBillItems.size != rooms.size && Date.compareMonth(
                    date.time,
                    Calendar.getInstance().time,
                    true
                )
            ) {
                binding.billGenerateWrapper.visibility = View.VISIBLE
                binding.billNotFound.visibility = View.GONE
                binding.billTabLists.visibility = View.GONE
            } else {
                binding.billGenerateWrapper.visibility = View.GONE
                binding.billNotFound.visibility = View.VISIBLE
            }
        } else {
            binding.billGenerateWrapper.visibility = View.GONE
            binding.billNotFound.visibility = View.GONE
            binding.billTabLists.visibility = View.VISIBLE
        }

        billItemAdaptor = BillItemAdaptor(requireContext(), items, rooms)

        binding.billTabLists.adapter = billItemAdaptor
        binding.billTabLists.setHasFixedSize(true)
        binding.billTabLists.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun getBills(
        createdTime: Calendar,
        onComplete: (paid: List<BillItem>, unpaid: List<BillItem>) -> Unit
    ) {
        val paidLists = mutableListOf<BillItem>()
        val unpaidLists = mutableListOf<BillItem>()

        val monthBounds = getMonthBounds(createdTime)

        billsRef.whereGreaterThanOrEqualTo("createdAt", monthBounds.first)
            .whereLessThan("createdAt", monthBounds.second).get()
            .addOnSuccessListener { monthSnapshot ->
                billsRef.whereEqualTo("paid", false).get().addOnSuccessListener { unpaidSnapshot ->
                    val monthBills =
                        monthSnapshot.documents.mapNotNull { it.toObject(Bill::class.java) }

                    val unpaidBills =
                        unpaidSnapshot.documents.mapNotNull { it.toObject(Bill::class.java) }

                    monthBills.forEach { doc ->
                        val unpaid =
                            unpaidBills.filter { it.roomId == doc.roomId && it.id != doc.id }
                                .filter { Date.compareMonth(date.time, it.createdAt.toDate()) }
                                .toMutableList()

                        if (doc.paid && unpaid.isEmpty()) {
                            paidLists.add(BillItem.NormalItem(doc))
                        } else {
                            if (unpaid.isEmpty()) {
                                unpaidLists.add(BillItem.NormalItem(doc))
                            } else {
                                unpaid.add(0, doc)
                                unpaidLists.add(BillItem.MergedItem(unpaid))
                            }
                        }
                    }

                }.addOnCompleteListener {
                    onComplete(paidLists, unpaidLists)
                }
            }
    }

    private fun getMonthBounds(calendar: Calendar): Pair<Timestamp, Timestamp> {
        val cal = calendar.clone() as Calendar

        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        val startOfMonth = Timestamp(cal.time)

        cal.add(Calendar.MONTH, 1)
        val startOfNextMonth = Timestamp(cal.time)

        return Pair(startOfMonth, startOfNextMonth)
    }
}