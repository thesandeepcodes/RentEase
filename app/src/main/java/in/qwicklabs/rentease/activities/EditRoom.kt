package `in`.qwicklabs.rentease.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.bumptech.glide.Glide
import com.google.android.gms.tasks.Tasks
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import `in`.qwicklabs.rentease.constants.Constants
import `in`.qwicklabs.rentease.model.Room
import `in`.qwicklabs.rentease.databinding.ActivityEditRoomBinding
import `in`.qwicklabs.rentease.utils.Loader

class EditRoom : AppCompatActivity() {
    private lateinit var binding: ActivityEditRoomBinding
    private lateinit var imageLauncher: ActivityResultLauncher<Intent>

    private val storageRef = FirebaseStorage.getInstance().reference
    private lateinit var loader: Loader

    private lateinit var room: Room
    private lateinit var roomId: String
    private var roomImageUri: String? = null

    private lateinit var db: DocumentReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityEditRoomBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loader = Loader(this)
        roomId = intent.getStringExtra("roomId") ?: ""

        if (roomId.isEmpty()) {
            MaterialAlertDialogBuilder(this).setTitle("Not Found")
                .setMessage("This room doesn't exist").setPositiveButton("Close") { d, _ ->
                d.dismiss()
                finish()
            }.show()

            return
        }

        imageLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK && result.data !== null) {
                    val imageUri = result.data?.data

                    if (imageUri !== null) {
                        roomImageUri = imageUri.toString()
                        binding.editRoomPreviewImage.setImageURI(imageUri)
                        binding.editRoomPlaceholderImg.visibility = View.GONE
                    }
                }
            }

        binding.editRoomImgPicker.setOnClickListener {
            val intent = Intent(
                Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            )
            imageLauncher.launch(intent)
        }

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        binding.editRoomDelete.setOnClickListener {
            MaterialAlertDialogBuilder(this).setTitle("Delete")
                .setMessage("Are you sure you want to delete ${room.name}?")
                .setNegativeButton("No") { dialog, _ -> dialog.dismiss() }
                .setPositiveButton("Yes, Delete") { dialog, _ ->
                    dialog.dismiss()
                    loader.dialogTitle.text = "Deleting..."
                    loader.dialog.show()

                    val deleteRoom =
                        db.collection(Constants.ROOM_COLLECTION).document(roomId).delete()
                    val deleteImage =
                        storageRef.child(Constants.ROOM_PREVIEW_IMG_PATH + "/" + roomId).delete()


                    val deleteTasks = if (room.img !== null) mutableListOf(
                        deleteRoom,
                        deleteImage
                    ) else mutableListOf(deleteRoom)

                    Tasks.whenAll(deleteTasks).addOnFailureListener {
                        MaterialAlertDialogBuilder(this).setTitle("Failed")
                            .setMessage("We could not complete this action at the moment")
                            .setPositiveButton("Okay") { d, _ ->
                                d.dismiss()
                            }.show()
                    }.addOnSuccessListener {
                        Intent().apply {
                            putExtra("deleted", true)
                        }.let {
                            setResult(Activity.RESULT_OK, it)
                        }

                        finish()
                    }.addOnCompleteListener {
                        loader.dialog.hide()
                    }
                }.show()
        }

        binding.saveRoom.setOnClickListener {
            val roomName = binding.editRoomName.text.toString()
            val roomDes = binding.editRoomDescription.text.toString()
            val roomRent = binding.editRoomRent.text.toString()

            val errorMessage = validateRoomInputs(roomName, roomDes, roomRent)
            if (errorMessage != null) {
                showErrorMessage(errorMessage)
                return@setOnClickListener
            }

            val rent = roomRent.toIntOrNull()
            if (rent == null || rent < 0) {
                showErrorMessage("Rent amount cannot exceed ${Int.MAX_VALUE}.")
                return@setOnClickListener
            }

            if (roomImageUri != null && roomImageUri !== room.img) {
                uploadImage()
            } else {
                updateRoom()
            }
        }

        db = Constants.getDbRef()

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
                setDefaultData()
            }
    }

    private fun validateRoomInputs(roomName: String, roomDes: String, roomRent: String): String? {
        return when {
            roomName.isEmpty() -> "Please add a name for the room."
            roomDes.isEmpty() -> "Please add a description for the room."
            roomRent.isEmpty() -> "Please add the rent amount for the room."
            else -> null
        }
    }

    private fun showErrorMessage(message: String) {
        binding.errorMessage.text = message
        binding.errorMessage.visibility = View.VISIBLE
    }

    private fun uploadImage() {
        loader.dialogTitle.text = "Updating Image.."
        loader.dialog.show()

        val imgRef =
            storageRef.child(Constants.ROOM_PREVIEW_IMG_PATH + "/" + roomId)

        if (roomImageUri !== null) {
            imgRef.putFile(roomImageUri.toString().toUri()).addOnSuccessListener {
                imgRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                    roomImageUri = downloadUrl.toString()
                    updateRoom()
                }
            }
        } else {
            updateRoom()
        }
    }

    private fun updateRoom() {
        loader.dialogTitle.text = "Updating Room..."
        loader.dialog.show()

        val name = binding.editRoomName.text.toString()
        val des = binding.editRoomDescription.text.toString()
        val rent = binding.editRoomRent.text.toString().toInt()

        if (Constants.USER?.uid !== null) {
            val data = hashMapOf(
                "name" to name,
                "des" to des,
                "rent" to rent,
                "img" to roomImageUri,
            )

            Constants.getDbRef().collection(Constants.ROOM_COLLECTION).document(roomId)
                .set(data, SetOptions.merge())
                .addOnSuccessListener {
                    Intent().apply {
                        data.forEach { (key, value) ->
                            putExtra(key, value)
                        }
                    }.let {
                        setResult(Activity.RESULT_OK, it)
                        finish()
                    }
                }.addOnFailureListener {
                    Toast.makeText(
                        this,
                        "Unable to update room details at the moment.",
                        Toast.LENGTH_SHORT
                    )
                        .show()
                }.addOnCompleteListener { loader.dialog.hide() }
        }
    }

    private fun setDefaultData() {
        if (room.img !== null) {
            binding.editRoomPlaceholderImg.visibility = View.GONE
            Glide.with(binding.editRoomPreviewImage).load(room.img)
                .into(binding.editRoomPreviewImage)
        }

        binding.editRoomName.setText(room.name)
        binding.editRoomDescription.setText(room.des)
        binding.editRoomRent.setText(room.rent.toString())

        roomImageUri = room.img
    }
}