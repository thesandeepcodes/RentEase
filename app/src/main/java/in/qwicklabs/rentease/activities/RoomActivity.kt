package `in`.qwicklabs.rentease.activities

import android.app.Activity
import android.content.Intent
import android.icu.util.Calendar
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.DocumentReference
import `in`.qwicklabs.rentease.constants.Constants
import `in`.qwicklabs.rentease.model.Charge
import `in`.qwicklabs.rentease.model.Room
import `in`.qwicklabs.rentease.databinding.ActivityRoomBinding
import `in`.qwicklabs.rentease.databinding.ListRoomChargeBinding
import `in`.qwicklabs.rentease.databinding.ListRoomTenantBinding
import `in`.qwicklabs.rentease.model.Bill
import `in`.qwicklabs.rentease.model.Tenant
import `in`.qwicklabs.rentease.utils.AppUtils
import `in`.qwicklabs.rentease.utils.Date
import `in`.qwicklabs.rentease.utils.GenerateBill
import `in`.qwicklabs.rentease.utils.Loader

class RoomActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRoomBinding
    private lateinit var editRoomLauncher: ActivityResultLauncher<Intent>

    private lateinit var room: Room
    private var charges: MutableList<Charge> = mutableListOf()
    private var tenants: MutableList<Tenant> = mutableListOf()

    private lateinit var roomId: String
    private var due: Double? = null

    private lateinit var db: DocumentReference

    private lateinit var viewTenantLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityRoomBinding.inflate(layoutInflater)
        setContentView(binding.root)


        roomId = intent.getStringExtra("roomId") ?: ""

        if (roomId.isEmpty()) {
            MaterialAlertDialogBuilder(this).setTitle("Not Found")
                .setMessage("This room doesn't exist").setPositiveButton("Close") { d, _ ->
                    d.dismiss()
                    finish()
                }.show()

            return
        }

        db = Constants.getDbRef()

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
                    updateTenants()
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

                updateTenants()
            }
        }

        editRoomLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val data = result.data

                    val deleted = data?.getBooleanExtra("deleted", false) ?: false

                    if (deleted) {
                        Intent().apply {
                            putExtra("deleted", true)
                            putExtra("id", roomId)
                        }.let {
                            setResult(Activity.RESULT_OK, it)
                        }

                        finish()
                    }

                    val name = data?.getStringExtra("name")
                    val des = data?.getStringExtra("des")
                    val img = data?.getStringExtra("img")
                    val rent = data?.getIntExtra("rent", 0)

                    if (name.isNullOrEmpty() || des.isNullOrEmpty() || rent == null) {
                        MaterialAlertDialogBuilder(this).setTitle("Oops!")
                            .setMessage("Please ensure all fields are valid before updating the room")
                            .setPositiveButton("Retry") { dialog, _ ->
                                dialog.dismiss()
                            }.show()
                    } else {
                        updateRoomInfo(name, des, img, due, rent, room.revenue)
                    }
                }
            }

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.roomEdit.setOnClickListener {
            Intent(this, EditRoom::class.java).apply {
                putExtra("roomId", roomId)
            }.let {
                editRoomLauncher.launch(it)
            }
        }

        binding.roomGenerateBill.setOnClickListener {
            val loader = Loader(this)
            loader.dialogTitle.text = "Generating Bill.."
            loader.dialog.show()

            GenerateBill.generate(room, Calendar.getInstance()){b->
                binding.roomGenerateBill.visibility = View.GONE
                loader.dialog.dismiss()
                startActivity(Intent(this, ViewBill::class.java).apply {
                    putExtra("billId", arrayOf(b?.id))
                })
            }
        }

        // fetching dues
        db.collection(Constants.BILL_COLLECTION).whereEqualTo("roomId", roomId).get().addOnSuccessListener {
            val bills = it.documents.mapNotNull {b-> b.toObject(Bill::class.java) }

            if(!bills.any {b-> Date.compareMonth(b.createdAt.toDate(), Calendar.getInstance().time, true) }){
                binding.roomGenerateBill.visibility = View.VISIBLE
            }

            due = if(bills.isNotEmpty()){
                GenerateBill.dues(bills)
            }else{
                0.0
            }

            binding.roomInfoDue.text = "₹" + AppUtils.formatDecimal(due!!)
        }

        // Fetching room details
        db.collection(Constants.ROOM_COLLECTION).whereEqualTo("id", roomId).get()
            .addOnSuccessListener {
                if (!it.isEmpty) {
                    val data = it.documents[0]

                    val roomRef = data.toObject(Room::class.java)

                    if (roomRef !== null) {
                        room = roomRef
                    }
                }
            }.addOnCompleteListener {
                updateRoomInfo(room.name, room.des, room.img, due, room.rent, room.revenue)
            }


        // Fetching Charges
        db.collection(Constants.CHARGE_COLLECTION).whereArrayContains("appliedTo", roomId).get()
            .addOnSuccessListener {
                it.documents.forEach { doc ->
                    val charge = doc.toObject(Charge::class.java)

                    if (charge !== null) {
                        charges.add(charge)
                    }
                }
            }.addOnCompleteListener {
            updateCharges()
        }

        db.collection(Constants.TENANT_COLLECTION).whereEqualTo("roomId", roomId).get()
            .addOnSuccessListener {
                it.documents.forEach {doc ->
                    val tenant = doc.toObject(Tenant::class.java)

                    if(tenant !== null){
                        tenants.add(tenant)
                    }
                }
            }.addOnCompleteListener {
                updateTenants()
            }
    }

    private fun updateTenants(){
        val activeTenants = tenants.filter { it.active }
        val pastTenants = tenants.filter { !it.active }

        if (activeTenants.isEmpty()) {
            binding.roomTenantsNoTenant.visibility = View.VISIBLE
            binding.roomTenantsLoader.visibility = View.GONE
        } else {
            binding.roomTenantsWrapper.removeAllViews()
        }

        if (pastTenants.isEmpty()) {
            binding.roomPastTenantsNoTenant.visibility = View.VISIBLE
            binding.roomPastTenantsLoader.visibility = View.GONE
        } else {
            binding.roomPastTenantsWrapper.removeAllViews()
        }

        activeTenants.forEach {tenant->
            val listBinding =
                ListRoomTenantBinding.inflate(layoutInflater, binding.roomTenantsWrapper, false)
            listBinding.listRoomTenantName.text = tenant.name
            listBinding.listRoomTenantDes.text = tenant.description

            Glide.with(listBinding.listRoomTenantProfile).load(tenant.profile).into(listBinding.listRoomTenantProfile)

            listBinding.listRoomTenantPending.visibility = if(tenant.documents.isNotEmpty()) View.GONE else View.VISIBLE
            listBinding.listRoomTenantVerified.visibility = if(tenant.documents.isNotEmpty()) View.VISIBLE else View.GONE

            binding.roomTenantsWrapper.addView(listBinding.root)

            listBinding.root.setOnClickListener {
                Intent(this, ViewTenant::class.java).apply {
                    putExtra("tenantId", tenant.id)
                }.let {t-> viewTenantLauncher.launch(t) }
            }
        }

        pastTenants.forEach {tenant->
            val listBinding =
                ListRoomTenantBinding.inflate(layoutInflater, binding.roomTenantsWrapper, false)
            listBinding.listRoomTenantName.text = tenant.name
            listBinding.listRoomTenantDes.text = tenant.description

            Glide.with(listBinding.listRoomTenantProfile).load(tenant.profile).into(listBinding.listRoomTenantProfile)

            listBinding.listRoomTenantPending.visibility = if(tenant.documents.isNotEmpty()) View.GONE else View.VISIBLE
            listBinding.listRoomTenantVerified.visibility = if(tenant.documents.isNotEmpty()) View.VISIBLE else View.GONE

            binding.roomPastTenantsWrapper.addView(listBinding.root)

            listBinding.root.setOnClickListener {
                Intent(this, ViewTenant::class.java).apply {
                    putExtra("tenantId", tenant.id)
                }.let {t-> viewTenantLauncher.launch(t) }
            }
        }
    }

    private fun updateCharges() {
        if (charges.isEmpty()) {
            binding.roomChargesNoCharges.visibility = View.VISIBLE
            binding.roomChargesLoader.visibility = View.GONE
        } else {
            binding.roomChargesWrapper.removeAllViews()
        }

        charges.forEach {
            val listBinding =
                ListRoomChargeBinding.inflate(layoutInflater, binding.roomChargesWrapper, false)
            listBinding.listRoomChargeAmount.text = "₹" + it.amount.toString()
            listBinding.listRoomChargeName.text = it.name

            binding.roomChargesWrapper.addView(listBinding.root)
        }

    }

    private fun updateRoomInfo(
        name: String,
        des: String,
        img: String?,
        due: Double?,
        rent: Int,
        revenue: Double
    ) {
        if (img !== null) {
            Glide.with(binding.roomImage).load(img).into(binding.roomImage)
        }

        binding.toolbar.setTitle(name)
        binding.roomInfoName.text = name
        binding.roomInfoDes.text = des
        binding.roomInfoRent.text = "₹" + rent.toString()

        binding.roomInfoDue.text = if(due == null) "loading.." else "₹" + AppUtils.formatDecimal(due)
        binding.roomInfoRevenue.text = "₹" + AppUtils.formatDecimal(revenue)

        binding.roomInfoWrapper.visibility = View.VISIBLE
        binding.roomInfoLoader.visibility = View.GONE

        Intent().apply {
            putExtra("id", roomId)
            putExtra("name", name)
            putExtra("des", des)
            putExtra("rent", rent)
            putExtra("img", img)
        }.let {
            setResult(Activity.RESULT_OK, it)
        }
    }
}