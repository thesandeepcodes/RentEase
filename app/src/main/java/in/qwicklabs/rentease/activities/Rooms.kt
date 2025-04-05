package `in`.qwicklabs.rentease.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import `in`.qwicklabs.rentease.adaptor.RoomItemAdaptor
import `in`.qwicklabs.rentease.constants.Constants
import `in`.qwicklabs.rentease.model.Room
import `in`.qwicklabs.rentease.databinding.ActivityRoomsBinding

class Rooms : AppCompatActivity() {
    private lateinit var binding: ActivityRoomsBinding
    private lateinit var addRoomLauncher: ActivityResultLauncher<Intent>
    private lateinit var itemsAdaptor: RoomItemAdaptor
    private lateinit var roomActivityLauncher: ActivityResultLauncher<Intent>
    private lateinit var rooms: MutableList<Room>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRoomsBinding.inflate(layoutInflater)

        setContentView(binding.root)
        enableEdgeToEdge()

        roomActivityLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val data = result.data

                    val deleted = data?.getBooleanExtra("deleted", false) ?: false
                    val roomId = data?.getStringExtra("id")

                    if (deleted && roomId !== null) {
                        itemsAdaptor.removeRoom(roomId)
                        return@registerForActivityResult
                    }

                    val name = data?.getStringExtra("name")
                    val des = data?.getStringExtra("des")
                    val rent = data?.getIntExtra("rent", 0)
                    val img = data?.getStringExtra("img")

                    if (roomId.isNullOrEmpty() || name.isNullOrEmpty() || des.isNullOrEmpty() || rent == null) {
                        return@registerForActivityResult
                    }

                    val r = rooms.firstOrNull { it.id == roomId }

                    if(r !== null){
                        itemsAdaptor.updateRoom(
                            Room(
                                roomId,
                                name,
                                des,
                                img,
                                r.rent,
                                r.revenue
                            )
                        )
                    }
                }
            }

        getRooms() { r ->
            rooms = r.sortedBy { it.name }.toMutableList()

            binding.roomsLoader.visibility = View.GONE

            itemsAdaptor = RoomItemAdaptor(this, rooms, roomActivityLauncher)
            binding.roomsList.layoutManager = LinearLayoutManager(this)
            binding.roomsList.setHasFixedSize(true)
            binding.roomsList.adapter = itemsAdaptor
        }

        binding.addRoom.setOnClickListener {
            Intent(this, AddRoom::class.java).let {
                addRoomLauncher.launch(it)
            }
        }

        addRoomLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val data = result.data

                    addRoom(
                        data?.getStringExtra("id"),
                        data?.getStringExtra("name"),
                        data?.getStringExtra("des"),
                        data?.getStringExtra("img"),
                        data?.getIntExtra("rent", 0)
                    )
                }
            }

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun addRoom(
        id: String?, name: String?, description: String?, imageUrl: String?, rent: Int?
    ) {
        if (id.isNullOrEmpty() || name.isNullOrEmpty() || description.isNullOrEmpty() || (rent == null)) {
            MaterialAlertDialogBuilder(this).setTitle("Invalid Fields")
                .setMessage("Please ensure that all fields are valid")
                .setPositiveButton("Okay") { dialog, _ ->
                    dialog.dismiss()
                }.show()

            return;
        }

        val item = Room(
            id, name, description, imageUrl, rent, 0.00
        )

        itemsAdaptor.addRoom(item)
    }

    fun getRooms(onComplete: (MutableList<Room>) -> Unit) {

        val rooms = mutableListOf<Room>()

        val roomRef = Constants.getDbRef().collection(Constants.ROOM_COLLECTION)

        roomRef.get().addOnSuccessListener {
            it.documents.forEach { doc ->
                val room = doc.toObject(Room::class.java)

                if (room !== null) {
                    rooms.add(room)
                }
            }
        }.addOnCompleteListener {
            onComplete(rooms)
        }
    }
}