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
import com.google.firebase.storage.FirebaseStorage
import `in`.qwicklabs.rentease.constants.Constants
import `in`.qwicklabs.rentease.databinding.ActivityAddRoomBinding
import `in`.qwicklabs.rentease.utils.Loader
import kotlinx.coroutines.Delay
import kotlinx.coroutines.delay
import java.util.UUID

class AddRoom : AppCompatActivity() {
    private lateinit var binding: ActivityAddRoomBinding
    private lateinit var imageLauncher: ActivityResultLauncher<Intent>

    private lateinit var loader: Loader

    private var roomImageUri: String? = null
    private val storageRef = FirebaseStorage.getInstance().reference
    private lateinit var docId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityAddRoomBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loader = Loader(this)

        imageLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK && result.data !== null) {
                    val imageUri = result.data?.data

                    if (imageUri !== null) {
                        roomImageUri = imageUri.toString()
                        binding.addRoomPreviewImage.setImageURI(imageUri)
                        binding.addRoomPlaceholderImg.visibility = View.GONE
                    }
                }
            }

        binding.addRoomImgPicker.setOnClickListener {
            val intent = Intent(
                Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            )
            imageLauncher.launch(intent)
        }

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        binding.saveRoom.setOnClickListener {
            val roomName = binding.addRoomName.text.toString()
            val roomDes = binding.addRoomDescription.text.toString()
            val roomRent = binding.addRoomRent.text.toString()

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

            docId = UUID.randomUUID().toString()

            if (roomImageUri != null) {
                uploadImage()
            } else {
                saveRoom()
            }
        }
    }


    private fun saveRoom() {
        loader.dialogTitle.text = "Saving Room..."
        loader.dialog.show()

        val name = binding.addRoomName.text.toString()
        val des = binding.addRoomDescription.text.toString()
        val rent = binding.addRoomRent.text.toString().toInt()

        if (Constants.USER?.uid !== null) {
            val data = hashMapOf(
                "id" to docId,
                "name" to name,
                "des" to des,
                "rent" to rent,
                "img" to roomImageUri,
                "revenue" to 0.00
            )

            Constants.getDbRef().collection(Constants.ROOM_COLLECTION).document(docId).set(data)
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
                    Toast.makeText(this, "Unable to Add room at the moment.", Toast.LENGTH_SHORT)
                        .show()
                }.addOnCompleteListener { loader.dialog.hide() }
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
        loader.dialogTitle.text = "Uploading Image.."
        loader.dialog.show()

        val imgRef =
            storageRef.child(Constants.ROOM_PREVIEW_IMG_PATH + "/" + docId)

        if (roomImageUri !== null) {
            imgRef.putFile(roomImageUri.toString().toUri()).addOnSuccessListener {
                imgRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                    roomImageUri = downloadUrl.toString()
                    saveRoom()
                }
            }
        } else {
            saveRoom()
        }
    }
}