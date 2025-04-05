package `in`.qwicklabs.rentease.activities

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.icu.util.Calendar
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import `in`.qwicklabs.rentease.constants.Constants
import `in`.qwicklabs.rentease.databinding.ActivityViewTenantBinding
import `in`.qwicklabs.rentease.databinding.BottomEditFieldBinding
import `in`.qwicklabs.rentease.databinding.BottomEditTenantProfileBinding
import `in`.qwicklabs.rentease.databinding.BottomEditTenantRoomBinding
import `in`.qwicklabs.rentease.databinding.ListTenantDocumentBinding
import `in`.qwicklabs.rentease.model.Room
import `in`.qwicklabs.rentease.model.Tenant
import `in`.qwicklabs.rentease.utils.AppUtils
import `in`.qwicklabs.rentease.utils.Date
import `in`.qwicklabs.rentease.utils.Loader
import java.util.UUID

class ViewTenant : AppCompatActivity() {
    private lateinit var binding: ActivityViewTenantBinding

    private lateinit var tenantId: String
    private lateinit var tenant: Tenant
    private lateinit var loader: Loader

    private lateinit var fileLauncher: ActivityResultLauncher<String>
    private lateinit var profileImageLauncher: ActivityResultLauncher<String>
    private var onProfilePicked: ((uri: Uri) -> Unit)? = null

    private lateinit var documents: MutableMap<String, String>

    private lateinit var bottomSheetDialog: BottomSheetDialog

    private lateinit var rooms: MutableList<Room>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityViewTenantBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tenantId = intent.getStringExtra("tenantId") ?: ""
        loader = Loader(this)

        if (tenantId.isEmpty()) {
            AppUtils.alert(
                this,
                "Oops!",
                "We could not find the details for unknown tenant Id. Please try again!"
            )
            return
        }

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }


        profileImageLauncher =
            registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                if (uri !== null) {
                    onProfilePicked?.invoke(uri)
                }
            }

        fileLauncher =
            registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
                if (uris.isNotEmpty()) {
                    if (uris.size > 10) {
                        AppUtils.alert(this, "Wait!", "You can upload maximum 10 files at a time")
                        return@registerForActivityResult
                    }

                    loader.dialogTitle.text = "Uploading..."
                    loader.dialog.show()

                    val storageRef = Constants.getStorageRef().child(Constants.TENANT_DOCUMENT_PATH)
                    val uploadedUrls = mutableMapOf<String, String>()
                    var uploadCount = 0

                    for (uri in uris) {
                        val fileName = "doc_${UUID.randomUUID()}_${System.currentTimeMillis()}"
                        val fileRef = storageRef.child(fileName)

                        fileRef.putFile(uri)
                            .addOnSuccessListener { taskSnapshot ->
                                fileRef.downloadUrl.addOnSuccessListener { downloadUri ->
                                    uploadedUrls[fileName] = downloadUri.toString()
                                    uploadCount++

                                    if (uploadCount == uris.size) {
                                        val docRef = Constants.getDbRef()
                                            .collection(Constants.TENANT_COLLECTION)
                                            .document(tenantId)

                                        docRef.update(uploadedUrls.mapKeys { "documents.${it.key}" })
                                            .addOnSuccessListener {
                                                documents.putAll(uploadedUrls)
                                                updateDocuments()
                                            }
                                            .addOnCompleteListener {
                                                loader.dialog.dismiss()
                                            }
                                            .addOnFailureListener { e ->
                                                loader.dialog.dismiss()
                                                AppUtils.alert(
                                                    this,
                                                    "Upload Failed",
                                                    "Error: ${e.message}"
                                                )
                                            }
                                    }
                                }
                            }
                            .addOnFailureListener { e ->
                                loader.dialog.dismiss()
                                AppUtils.alert(this, "Upload Failed", "Error: ${e.message}")
                            }
                    }
                }
            }

        getTenant {
            if (it !== null) {
                tenant = it
            } else {
                AppUtils.alert(
                    this,
                    "Oops!",
                    "We could not find details for unknown tenant Id. Please try again!"
                )
                return@getTenant
            }


            binding.viewTenantRoomNo.text = "Loading.."
            getRoomData(tenant.roomId) { roomData ->
                binding.viewTenantRoomNo.text = roomData?.name ?: "Unknown"
            }

            Rooms().getRooms { r -> rooms = r }

            updateTenantDetails()

            binding.viewTenantUploadDoc.setOnClickListener {
                fileLauncher.launch("*/*")
            }

            documents = tenant.documents.toMutableMap()
            updateDocuments()

            // share button listeners
            addShareButtonListener()

            // Edit Listeners
            binding.viewTenantEditPhone.setOnClickListener { editTenantField("phone") }
            binding.viewTenantEditStartDate.setOnClickListener { editTenantField("startDate") }
            binding.tenantEditRoomNo.setOnClickListener { editRooms() }
            binding.viewTenantEditProfile.setOnClickListener { editProfile() }

            // Delete & Active Listeners
            binding.tenantDelete.setOnClickListener {
                MaterialAlertDialogBuilder(this).setTitle("${if (tenant.active) "Inactive" else "Delete"} Tenant?")
                    .setMessage(
                        "Are you sure? This will ${if (tenant.active) "inactive" else "permanently delete"} this tenant. Proceed if you are confirmed for this action."
                    ).setNegativeButton("No") { d, _ -> d.dismiss() }
                    .setPositiveButton("Yes, ${if (tenant.active) "Inactive" else "Delete"}") { d, _ ->
                        d.dismiss()

                        val dbRef = Constants.getDbRef().collection(Constants.TENANT_COLLECTION)
                            .document(tenantId)

                        val task = if (tenant.active) {
                            dbRef.set(hashMapOf("active" to false), SetOptions.merge())
                        } else {
                            dbRef.delete()
                        }

                        fun performAction() {
                            loader.dialogTitle.text =
                                if (tenant.active) "Inactivating.." else "Deleting..."

                            // delete all docs if not active
                            if (!tenant.active) {
                                val storageRef = Constants.getStorageRef()
                                tenant.documents.forEach { (t, _) ->
                                    storageRef.child("${Constants.TENANT_DOCUMENT_PATH}/${t}")
                                        .delete()
                                }
                            }

                            task.addOnFailureListener { e ->
                                AppUtils.alert(
                                    this,
                                    "Error",
                                    "Unable to process this action: ${e.message}"
                                )
                            }.addOnSuccessListener {
                                updateIntent(if (tenant.active) TenantAction.INACTIVE else TenantAction.DELETE)
                                finish()
                            }.addOnCompleteListener {
                                loader.dialog.dismiss()
                            }
                        }

                        if (!tenant.active && tenant.profile !== null) {
                            loader.dialogTitle.text = "Deleting Profile.."
                            loader.dialog.show()

                            Constants.getStorageRef()
                                .child("${Constants.TENANT_PROFILE_PATH}/${tenantId}").delete()
                                .addOnSuccessListener {
                                    performAction()
                                }.addOnFailureListener { e ->
                                AppUtils.alert(
                                    this,
                                    "Error",
                                    "Unable to process this action: ${e.message}"
                                )

                                loader.dialog.dismiss()
                            }
                        } else {
                            performAction()
                        }
                    }.show()
            }

            if (!tenant.active) {
                binding.tenantActivate.visibility = View.VISIBLE
            }

            binding.tenantActivate.setOnClickListener {
                MaterialAlertDialogBuilder(this).setTitle("Activate?")
                    .setMessage("Are you sure you want to activate this tenant?")
                    .setNegativeButton("No") { d, _ -> d.dismiss() }
                    .setPositiveButton("Yes, Activate") { d, _ ->
                        d.dismiss()

                        val dbRef = Constants.getDbRef().collection(Constants.TENANT_COLLECTION)
                            .document(tenantId)
                        val task = dbRef.set(hashMapOf("active" to true), SetOptions.merge())

                        loader.dialogTitle.text = "Activating.."
                        loader.dialog.show()

                        task.addOnFailureListener { e ->
                            AppUtils.alert(
                                this,
                                "Error",
                                "Unable to process this action: ${e.message}"
                            )
                        }.addOnSuccessListener {
                            updateIntent(TenantAction.ACTIVE)
                            finish()
                        }.addOnCompleteListener {
                            loader.dialog.dismiss()
                        }
                    }.show()
            }
        }
    }

    private fun updateTenantDetails() {
        binding.viewTenantLoader.visibility = View.GONE
        binding.viewTenantOuter.visibility = View.VISIBLE

        binding.viewTenantName.text = tenant.name
        binding.viewTenantDes.text = tenant.description
        Glide.with(binding.viewTenantProfile).load(tenant.profile).into(binding.viewTenantProfile)

        binding.viewTenantPhone.text = tenant.phone
        binding.viewTenantStartDate.text =
            Date.formatTimestamp(tenant.startDate, "MMM dd, yyyy")
    }

    private fun editProfile() {
        bottomSheetDialog = BottomSheetDialog(this)
        val profileBinding = BottomEditTenantProfileBinding.inflate(layoutInflater)

        var profileUri: Uri? = null

        profileBinding.tenantProfileEditClose.setOnClickListener {
            bottomSheetDialog.dismiss()
        }


        // default fields
        profileBinding.tenantEditProfileName.setText(tenant.name)
        profileBinding.tenantEditProfileDes.setText(tenant.description)

        Glide.with(profileBinding.tenantEditProfileImage).load(tenant.profile)
            .into(profileBinding.tenantEditProfileImage)

        profileBinding.tenantEditProfileImageWrapper.setOnClickListener {
            profileImageLauncher.launch("image/*")
        }

        onProfilePicked = { uri ->
            profileUri = uri
            Glide.with(profileBinding.tenantEditProfileImage).load(uri)
                .into(profileBinding.tenantEditProfileImage)
        }

        profileBinding.tenantEditProfileButton.setOnClickListener {
            val name = profileBinding.tenantEditProfileName.text.toString()
            val des = profileBinding.tenantEditProfileDes.text.toString()

            if (name.isEmpty() || des.isEmpty()) {
                AppUtils.alert(this, "Wait!", "Please ensure that all fields are provided.")
                return@setOnClickListener
            }

            loader.dialogTitle.text = "Updating.."
            loader.dialog.show()

            if (profileUri !== null) {
                val profileRef =
                    Constants.getStorageRef().child("${Constants.TENANT_PROFILE_PATH}/${tenantId}")

                profileRef.putFile(
                    profileUri as Uri
                ).addOnSuccessListener { _ ->
                    profileRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                        updateTenantProfile(name, des, downloadUrl.toString())
                    }.addOnFailureListener { e ->
                        loader.dialog.dismiss()
                        AppUtils.alert(
                            this,
                            "Error",
                            "Failed to get profile image URL: ${e.message}"
                        )
                    }
                }.addOnFailureListener { e ->
                    loader.dialog.dismiss()
                    AppUtils.alert(this, "Error", "Failed to upload profile image: ${e.message}")
                }
            } else {
                updateTenantProfile(name, des, tenant.profile)
            }

        }

        bottomSheetDialog.setContentView(profileBinding.root)
        bottomSheetDialog.show()
    }

    private fun updateTenantProfile(name: String, des: String, profile: String?) {
        Constants.getDbRef().collection(Constants.TENANT_COLLECTION).document(tenantId).set(
            mutableMapOf(
                "name" to name,
                "description" to des,
                "profile" to profile
            ),
            SetOptions.merge()
        ).addOnSuccessListener {
            bottomSheetDialog.dismiss()
            tenant = tenant.copy(name = name, description = des, profile = profile)
            updateTenantDetails()
            updateIntent()
        }.addOnFailureListener { e ->
            AppUtils.alert(this, "Error", "Error updating profile ${e.message}")
        }.addOnCompleteListener {
            loader.dialog.dismiss()
        }
    }

    private fun editRooms() {
        bottomSheetDialog = BottomSheetDialog(this)
        val roomBinding = BottomEditTenantRoomBinding.inflate(layoutInflater)

        roomBinding.tenantRoomEditClose.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        val adapter =
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, rooms.map { it.name })
        roomBinding.tenantEditRoomSelect.setAdapter(adapter)

        roomBinding.tenantEditRoomButton.setOnClickListener {
            loader.dialogTitle.text = "Updating Room.."
            loader.dialog.show()

            val roomName = roomBinding.tenantEditRoomSelect.text.toString()
            val newRoomId = rooms.firstOrNull { it.name == roomName }?.id ?: "Unknown"

            Constants.getDbRef().collection(Constants.TENANT_COLLECTION).document(tenantId).set(
                mutableMapOf("roomId" to newRoomId),
                SetOptions.merge()
            ).addOnSuccessListener {
                tenant = tenant.copy(roomId = newRoomId)
                binding.viewTenantRoomNo.text = roomName
                updateIntent()
                bottomSheetDialog.dismiss()
            }.addOnFailureListener { e ->
                AppUtils.alert(this, "Oops!", "Error updating room: ${e.message}")
            }.addOnCompleteListener {
                loader.dialog.dismiss()
            }
        }

        bottomSheetDialog.setContentView(roomBinding.root)
        bottomSheetDialog.show()
    }

    private fun editTenantField(property: String) {
        bottomSheetDialog = BottomSheetDialog(this)
        val editBinding = BottomEditFieldBinding.inflate(layoutInflater)

        val type: Int
        val hint: String
        val defaultValue: String

        when (property) {
            "phone" -> {
                hint = "Mobile Number"
                type = InputType.TYPE_CLASS_PHONE
                defaultValue = tenant.phone
            }

            "startDate" -> {
                hint = "Start Date"
                type = InputType.TYPE_CLASS_DATETIME
                defaultValue = Date.formatTimestamp(tenant.startDate, "MMM dd, yyyy")
            }

            else -> {
                hint = "Field"
                type = InputType.TYPE_CLASS_TEXT
                defaultValue = ""
            }
        }

        editBinding.fieldEditFieldLayout.hint = hint
        editBinding.fieldEditField.inputType = type
        editBinding.fieldEditField.setText(defaultValue)

        val updatedData = hashMapOf<String, Any>()
        var value: Any? = null

        if (type == InputType.TYPE_CLASS_DATETIME) {
            editBinding.fieldEditField.isFocusable = false
            editBinding.fieldEditField.setOnClickListener {
                val picker = DatePickerDialog(this)
                picker.setTitle(hint)
                picker.show()

                picker.setOnDateSetListener { _, year, month, dayOfMonth ->
                    val calender = Calendar.getInstance()
                    calender.set(year, month, dayOfMonth, 0, 0, 0)

                    value = Timestamp(calender.time)
                    editBinding.fieldEditField.setText(
                        Date.formatTimestamp(
                            value as Timestamp,
                            "MMM dd, yyyy"
                        )
                    )
                }
            }
        }

        editBinding.fieldEditButton.setOnClickListener {
            loader.dialogTitle.text = "Updating.."
            loader.dialog.show()

            val fieldValue = editBinding.fieldEditField.text.toString()

            if (value == null) value = when (type) {
                InputType.TYPE_CLASS_NUMBER -> fieldValue.toInt()
                else -> fieldValue
            }

            updatedData[property] = value!!

            Constants.getDbRef().collection(Constants.TENANT_COLLECTION).document(tenantId).set(
                updatedData,
                SetOptions.merge()
            ).addOnSuccessListener {
                // Successfully updated
                bottomSheetDialog.dismiss()

                val t: Tenant = when (property) {
                    "phone" -> tenant.copy(phone = updatedData[property] as String)
                    "startDate" -> tenant.copy(startDate = updatedData[property] as Timestamp)
                    else -> tenant
                }

                tenant = t
                updateTenantDetails()
                updateIntent()
            }.addOnFailureListener { e ->
                AppUtils.alert(this, "Oops!", "Error updating the field: ${e.message}")
            }.addOnCompleteListener {
                loader.dialog.dismiss()
            }
        }

        editBinding.fieldEditClose.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.setContentView(editBinding.root)
        bottomSheetDialog.show()
    }

    private fun updateDocuments() {
        if (tenant.documents != documents) {
            updateIntent()
        }

        if (documents.isEmpty()) {
            binding.viewTenantDocuments.visibility = View.GONE
            binding.viewTenantNoDocs.visibility = View.VISIBLE
        } else {
            binding.viewTenantDocuments.visibility = View.VISIBLE
            binding.viewTenantNoDocs.visibility = View.GONE
        }

        binding.viewTenantDocuments.removeAllViews()

        documents.forEach { (docImage, docImageUrl) ->
            val docBinding = ListTenantDocumentBinding.inflate(
                layoutInflater,
                binding.viewTenantDocuments,
                false
            )

            Glide.with(docBinding.tenantDocumentImage).load(docImageUrl)
                .into(docBinding.tenantDocumentImage)

            docBinding.tenantDocumentDelete.setOnClickListener {
                MaterialAlertDialogBuilder(this).setTitle("Delete?")
                    .setMessage("Are you sure? This will permanently delete this document")
                    .setNegativeButton("No") { d, _ -> d.dismiss() }
                    .setPositiveButton("Yes, Delete") { d, _ ->
                        d.dismiss()

                        loader.dialogTitle.text = "Deleting.."
                        loader.dialog.show()

                        Constants.getStorageRef()
                            .child("${Constants.TENANT_DOCUMENT_PATH}/${docImage}").delete()
                            .addOnSuccessListener {
                                Constants.getDbRef().collection(Constants.TENANT_COLLECTION)
                                    .document(tenantId)
                                    .update(mapOf("documents.${docImage}" to FieldValue.delete()))
                                    .addOnSuccessListener {
                                        documents.remove(docImage)
                                        updateDocuments()
                                    }.addOnFailureListener { e ->
                                        AppUtils.alert(
                                            this,
                                            "Error",
                                            "Error deleting the document: ${e.message}"
                                        )
                                    }.addOnCompleteListener {
                                        loader.dialog.dismiss()
                                    }
                            }.addOnFailureListener { e ->
                                loader.dialog.dismiss()
                                AppUtils.alert(
                                    this,
                                    "Error",
                                    "Error deleting the document: ${e.message}"
                                )
                            }
                    }.show()
            }

            binding.viewTenantDocuments.addView(docBinding.root)
        }
    }

    private fun updateIntent(status: TenantAction = TenantAction.NONE) {
        return Intent().apply {
            putExtra("status", status)
            putExtra("tenantId", tenantId)
            putExtra("name", tenant.name)
            putExtra("des", tenant.description)
            putExtra("documents", HashMap(documents))
            putExtra("profile", tenant.profile)
        }.let {
            setResult(Activity.RESULT_OK, it)
        }
    }

    private fun getTenant(onComplete: (tenant: Tenant?) -> Unit) {
        var tenant: Tenant? = null

        Constants.getDbRef().collection(Constants.TENANT_COLLECTION).document(tenantId).get()
            .addOnSuccessListener {
                tenant = it.toObject(Tenant::class.java)
            }.addOnCompleteListener {
                onComplete(tenant)
            }
    }

    private fun getRoomData(roomId: String, onComplete: (room: Room?) -> Unit) {
        var room: Room? = null

        Constants.getDbRef().collection(Constants.ROOM_COLLECTION).document(roomId).get()
            .addOnSuccessListener {
                room = it.toObject(Room::class.java)
            }.addOnCompleteListener {
                onComplete(room)
            }
    }

    private fun addShareButtonListener() {
        binding.viewTenantCall.setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL, "tel:${tenant.phone}".toUri()))
        }

        binding.viewTenantWhatsapp.setOnClickListener {
            Intent(Intent.ACTION_VIEW).apply {
                data = "https://wa.me/+91${tenant.phone}".toUri()
            }.let { startActivity(it) }
        }

        binding.viewTenantMessage.setOnClickListener {
            startActivity(Intent(Intent.ACTION_SENDTO, "smsto:${tenant.phone}".toUri()))
        }
    }
}

enum class TenantAction {
    NONE,
    DELETE,
    ACTIVE,
    INACTIVE
}