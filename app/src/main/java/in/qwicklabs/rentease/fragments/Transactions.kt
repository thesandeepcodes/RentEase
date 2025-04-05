package `in`.qwicklabs.rentease.fragments

import android.app.DatePickerDialog
import android.icu.util.Calendar
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.Timestamp
import `in`.qwicklabs.rentease.adaptor.TransactionItemAdaptor
import `in`.qwicklabs.rentease.constants.Constants
import `in`.qwicklabs.rentease.databinding.FragmentTransactionsBinding
import `in`.qwicklabs.rentease.model.Transaction
import `in`.qwicklabs.rentease.model.TransactionType
import `in`.qwicklabs.rentease.utils.AppUtils


class Transactions : Fragment() {
    private lateinit var binding: FragmentTransactionsBinding
    private lateinit var transactionAdapter: TransactionItemAdaptor
    private lateinit var transactions: MutableList<Transaction>

    private var date = Calendar.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentTransactionsBinding.inflate(inflater, container, false)

        loadTransactions()

        binding.transactionMonth.text =  `in`.qwicklabs.rentease.utils.Date.formatTimestamp(Timestamp(date.time))

        binding.transactionMonth.setOnClickListener {
            val calendar = Calendar.getInstance()

            calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
            val maxDate = calendar.timeInMillis

            val picker = DatePickerDialog(requireContext(), { _, year, month, dayOfMonth ->
                val selectedCalendar = Calendar.getInstance()
                selectedCalendar.set(year, month, dayOfMonth, 0, 0, 0)
                date = selectedCalendar

                binding.transactionMonth.text =
                    `in`.qwicklabs.rentease.utils.Date.formatTimestamp(Timestamp(date.time))

                loadTransactions()
            }, date.get(Calendar.YEAR), date.get(Calendar.MONTH), date.get(Calendar.DAY_OF_MONTH))

            picker.datePicker.maxDate = maxDate

            picker.show()
        }

        binding.transactionMonthNext.setOnClickListener {
            val calendar = Calendar.getInstance()

            calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
            val maxDate = calendar.timeInMillis

            date.add(Calendar.MONTH, 1)

            if (date.timeInMillis < maxDate) {
                loadTransactions()
                binding.transactionMonth.text =
                    `in`.qwicklabs.rentease.utils.Date.formatTimestamp(Timestamp(date.time))
            } else {
                date.add(Calendar.MONTH, -1)
            }
        }

        binding.transactionMonthBack.setOnClickListener {
            date.add(Calendar.MONTH, -1)

            loadTransactions()
            binding.transactionMonth.text =
                `in`.qwicklabs.rentease.utils.Date.formatTimestamp(Timestamp(date.time))
        }

        return binding.root
    }

    private fun showLoader() {
        binding.transactionLoaderWrapper.visibility = View.VISIBLE
        binding.transactionNotFound.visibility = View.GONE
        binding.transactionContent.visibility = View.GONE
    }

    private fun hideLoader() {
        binding.transactionLoaderWrapper.visibility = View.GONE
        binding.transactionNotFound.visibility = View.GONE
        binding.transactionContent.visibility = View.VISIBLE
    }

    private fun loadTransactions() {
        showLoader()

        getTransactions(date, { e ->
            if (!isAdded) return@getTransactions

            AppUtils.alert(requireContext(), "Oops!", "Unable to fetch transactions: ${e.message}")
        }) { t ->
            transactions = t.sortedByDescending { it.createdAt.toDate() }.toMutableList()

            showTransactions()
        }
    }

    private fun showTransactions() {
        if (!isAdded) return

        hideLoader()

        if (transactions.isEmpty()) {
            binding.transactionNotFound.visibility = View.VISIBLE
            binding.transactionSummary.visibility = View.GONE
        }else{
            binding.transactionSummary.visibility = View.VISIBLE
        }

        transactionAdapter = TransactionItemAdaptor(requireContext(), transactions)
        binding.transactionLists.adapter = transactionAdapter
        binding.transactionLists.setHasFixedSize(true)
        binding.transactionLists.layoutManager = LinearLayoutManager(requireContext())

        binding.transactionTotalIncome.text = "₹" + AppUtils.formatDecimal(transactions.filter { it.type == TransactionType.REGULAR }.sumOf { it.amount })
        binding.transactionTotalExpense.text = "₹" + AppUtils.formatDecimal(transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount })
    }

    private fun getTransactions(
        cal: Calendar,
        onFailure: (Exception) -> Unit,
        onComplete: (transactions: List<Transaction>) -> Unit
    ) {
        val calendar = Calendar.getInstance()
        calendar.time = cal.time

        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val startOfMonth = Timestamp(calendar.time)
        calendar.add(Calendar.MONTH, 1)
        val startOfNextMonth = Timestamp(calendar.time)

        Constants.getDbRef().collection(Constants.TRANSACTION_COLLECTION)
            .whereGreaterThanOrEqualTo("createdAt", startOfMonth)
            .whereLessThan("createdAt", startOfNextMonth).get().addOnSuccessListener {
                val trans = it.documents.mapNotNull { t -> t.toObject(Transaction::class.java) }

                onComplete(trans)
            }.addOnFailureListener { e ->
                onFailure(e)
            }
    }
}