package `in`.qwicklabs.rentease.adaptor

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import `in`.qwicklabs.rentease.R
import `in`.qwicklabs.rentease.activities.RoomActivity
import `in`.qwicklabs.rentease.constants.Constants
import `in`.qwicklabs.rentease.model.Room
import `in`.qwicklabs.rentease.utils.AppUtils

class RoomItemAdaptor(
    val context: Context,
    private var items: MutableList<Room>,
    val roomActivityLauncher: ActivityResultLauncher<Intent>? = null
) :
    RecyclerView.Adapter<RoomItemAdaptor.RoomItemViewHolder>() {

    fun addRoom(room: Room) {
        items.add(0, room)
        notifyItemInserted(0)
    }

    fun removeRoom(roomId: String) {
        val index = items.indexOfFirst { it.id == roomId }

        if (index >= 0) {
            items.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    fun updateRoom(room: Room) {
        val index = items.indexOfFirst { it.id == room.id }
        items[index] = room

        notifyItemChanged(index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomItemViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.list_rooms_item, parent, false)

        return RoomItemViewHolder(view)
    }

    override fun getItemCount(): Int {
        return items.size
    }

    override fun onBindViewHolder(holder: RoomItemViewHolder, position: Int) {
        val currentItem = items[position]

        holder.name.text = currentItem.name
        holder.description.text = currentItem.des

        if (currentItem.img !== null) {
            Glide.with(holder.previewImage).load(currentItem.img)
                .into(holder.previewImage)
        }

        holder.statusAvailable.visibility = View.GONE
        holder.statusOnLease.visibility = View.GONE

        Constants.getDbRef().collection(Constants.TENANT_COLLECTION).whereEqualTo("roomId", currentItem.id).get().addOnSuccessListener {
            if(it.documents.isEmpty()){
                holder.statusAvailable.visibility = View.VISIBLE
            }else{
                holder.statusOnLease.visibility = View.VISIBLE
            }
        }

        holder.rent.text = "₹${currentItem.rent}"
        holder.totalRevenue.text = "₹${AppUtils.formatDecimal(currentItem.revenue)} Total Revenue"
    }

    inner class RoomItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.room_list_name)
        val description: TextView = view.findViewById(R.id.room_list_description)
        val previewImage: ImageView = view.findViewById(R.id.room_list_image)
        val statusAvailable: CardView = view.findViewById(R.id.room_list_available)
        val statusOnLease: CardView = view.findViewById(R.id.room_list_onlease)
        val rent: TextView = view.findViewById(R.id.room_list_rent)
        val totalRevenue: TextView = view.findViewById(R.id.room_list_total_revenue)

        init {
            view.setOnClickListener {
                val room = items[adapterPosition]

                Intent(context, RoomActivity::class.java).apply {
                    putExtra("roomId", room.id)
                }.let {
                    if (roomActivityLauncher == null) {
                        context.startActivity(it)
                    } else {
                        roomActivityLauncher.launch(it)
                    }
                }
            }
        }
    }
}