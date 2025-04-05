package `in`.qwicklabs.rentease.fragments

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import `in`.qwicklabs.rentease.activities.Rooms
import `in`.qwicklabs.rentease.activities.TenantAction
import `in`.qwicklabs.rentease.adaptor.TenantItemAdaptor
import `in`.qwicklabs.rentease.constants.Constants
import `in`.qwicklabs.rentease.model.Room
import `in`.qwicklabs.rentease.model.Tenant
import `in`.qwicklabs.rentease.databinding.BottomAddTenantBinding
import `in`.qwicklabs.rentease.databinding.FragmentTenantsBinding
import `in`.qwicklabs.rentease.utils.AppUtils
import `in`.qwicklabs.rentease.utils.Loader
import java.util.UUID

class Tenants : Fragment() {
    private lateinit var binding: FragmentTenantsBinding

    private lateinit var bottomSheetDialog: BottomSheetDialog

    private lateinit var tenants: MutableList<Tenant>
    private var rooms = mutableListOf<Room>()

    private lateinit var activeTenantItemAdapter: TenantItemAdaptor
    private lateinit var inactiveTenantItemAdaptor: TenantItemAdaptor

    private lateinit var viewTenantLauncher: ActivityResultLauncher<Intent>

    private lateinit var loader: Loader

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentTenantsBinding.inflate(layoutInflater, container, false)
        loader = Loader(requireContext())

        Rooms().getRooms {r ->
            rooms = r

            getTenants {tenantsList->
                tenants = tenantsList
                tenants.sortByDescending { it.startDate.toDate() }

                loadTenants()
            }
        }


        binding.tenantAdd.setOnClickListener { addTenant() }
        binding.tenantInactiveAdd.setOnClickListener { addTenant() }
        binding.tenantAddNoResult.setOnClickListener { addTenant() }


        viewTenantLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){result ->
            if(result.resultCode == Activity.RESULT_OK){
                val data = result.data

                val status = (data?.getSerializableExtra("status") as TenantAction)
                val tenantId = data.getStringExtra("tenantId") ?: ""

                when (status) {
                    TenantAction.DELETE -> {
                        tenants.removeIf{it.id == tenantId}
                    }
                    TenantAction.INACTIVE -> {
                        tenants = tenants.map {t -> if(t.id == tenantId) t.copy(active = false) else t }.toMutableList()
                    }
                    TenantAction.ACTIVE -> {
                        tenants = tenants.map {t -> if(t.id == tenantId) t.copy(active = true) else t }.toMutableList()
                    }
                    TenantAction.NONE -> {}
                }

                if(status != TenantAction.NONE){
                    loadTenants()
                    return@registerForActivityResult
                }

                val name = data.getStringExtra("name") ?: ""
                val des = data.getStringExtra("des") ?: ""
                val profile = data.getStringExtra("profile")
                val docs = data.getSerializableExtra("documents") as? HashMap<String, String>


                var documents = mutableMapOf<String, String>()
                if(docs !== null) documents = docs.toMutableMap()

                tenants = tenants.map { t ->
                    if (t.id == tenantId) t.copy(name = name, description = des, documents = documents, profile = profile) else t
                }.toMutableList()

                loadTenants()
            }
        }

        return binding.root
    }

    private fun loadTenants(){
        if(!isAdded) return;

        val context = requireContext()

        val activeTenants = tenants.filter { t -> t.active }.toMutableList()
        val inactiveTenants = tenants.filter { t -> !t.active }.toMutableList()

        activeTenantItemAdapter = TenantItemAdaptor(context, activeTenants, viewTenantLauncher)
        binding.tenantActiveList.setHasFixedSize(true)
        binding.tenantActiveList.adapter = activeTenantItemAdapter
        binding.tenantActiveList.layoutManager = LinearLayoutManager(context)

        inactiveTenantItemAdaptor = TenantItemAdaptor(context, inactiveTenants, viewTenantLauncher)
        binding.tenantInactiveList.setHasFixedSize(true)
        binding.tenantInactiveList.adapter = inactiveTenantItemAdaptor
        binding.tenantInactiveList.layoutManager = LinearLayoutManager(context)


        binding.tenantListLoader.visibility = View.GONE

        if (tenants.isEmpty()) {
            binding.tenantNoResult.visibility = View.VISIBLE
            binding.tenantListScrollview.visibility = View.GONE
        } else {
            binding.tenantNoResult.visibility = View.GONE
            binding.tenantListWrapper.visibility = View.VISIBLE
            binding.tenantListScrollview.visibility = View.VISIBLE
        }

        binding.tenantActiveLabel.visibility =
            if (activeTenants.isEmpty()) View.GONE else View.VISIBLE
        binding.tenantInactiveLabel.visibility =
            if (inactiveTenants.isEmpty()) View.GONE else View.VISIBLE

        binding.tenantInactiveAdd.visibility = if (activeTenants.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun addTenant() {
        bottomSheetDialog = BottomSheetDialog(requireContext())

        val tenantBinding = BottomAddTenantBinding.inflate(layoutInflater)

        tenantBinding.tenantAddRoomSelect.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                rooms.map { it.name })
        )

        tenantBinding.tenantAddClose.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        tenantBinding.tenantAddButton.setOnClickListener {
            val name = tenantBinding.tenantAddName.text.toString()
            val phone = tenantBinding.tenantAddPhone.text.toString()
            val des = tenantBinding.tenantAddDescription.text.toString()
            val roomId =
                rooms.firstOrNull { it.name == tenantBinding.tenantAddRoomSelect.text.toString() }?.id

            if (roomId.isNullOrEmpty() || des.isEmpty() || phone.isEmpty() || name.isEmpty()) {
                AppUtils.alert(
                    requireContext(),
                    "Wait!",
                    "All fields are mandatory. Please check all the input fields"
                )
                return@setOnClickListener
            }

            if (phone.length > 10) {
                AppUtils.alert(
                    requireContext(),
                    "Wait!",
                    "Mobile number is invalid. Please check the provided number"
                )
                return@setOnClickListener
            }

            val tenant = Tenant(
                UUID.randomUUID().toString(),
                name,
                roomId,
                phone,
                des
            )

            loader.dialogTitle.text = "Adding Tenant.."
            loader.dialog.show()

            Constants.getDbRef().collection(Constants.TENANT_COLLECTION).document(tenant.id)
                .set(tenant).addOnSuccessListener {
                    bottomSheetDialog.dismiss()
                    activeTenantItemAdapter.addTenant(tenant)
                    binding.tenantActiveList.scrollToPosition(0)
                    binding.tenantActiveList.post{
                        binding.tenantActiveList.requestLayout()
                    }

                    tenants.add(tenant)

                    if(tenants.isEmpty()) {
                        loadTenants()
                    }
                }.addOnFailureListener {
                    AppUtils.alert(
                        requireContext(),
                        "Error",
                        "Unable to add tenant. Please try again later."
                    )
                }.addOnCompleteListener {
                    loader.dialog.dismiss()
                }
        }

        bottomSheetDialog.setContentView(tenantBinding.root)
        bottomSheetDialog.show()
    }

    private fun getTenants(onComplete: (MutableList<Tenant>) -> Unit) {
        val t = mutableListOf<Tenant>()

        Constants.getDbRef().collection(Constants.TENANT_COLLECTION).get().addOnSuccessListener {
            val docs = it.documents

            docs.forEach { doc ->
                val tenant = doc.toObject(Tenant::class.java)
                if (tenant !== null) t.add(tenant)
            }
        }.addOnCompleteListener {
            onComplete(t)
        }
    }
}
