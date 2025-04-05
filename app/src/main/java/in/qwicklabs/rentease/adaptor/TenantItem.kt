package `in`.qwicklabs.rentease.adaptor

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import `in`.qwicklabs.rentease.R
import `in`.qwicklabs.rentease.activities.ViewTenant
import `in`.qwicklabs.rentease.constants.Constants
import `in`.qwicklabs.rentease.model.Room
import `in`.qwicklabs.rentease.model.Tenant
import `in`.qwicklabs.rentease.utils.Date

class TenantItemAdaptor(val context: Context, val tenants: MutableList<Tenant>, val viewTenantLauncher: ActivityResultLauncher<Intent>) :
    RecyclerView.Adapter<TenantItemAdaptor.TenantItemViewHolder>() {

    fun addTenant(tenant: Tenant) {
        tenants.add(0, tenant)
        notifyItemInserted(0)
    }

    fun removeTenant(tenantId: String) {
        val index = tenants.indexOfFirst { it.id == tenantId }

        if (index >= 0) {
            tenants.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    fun updateTenant(tenant: Tenant) {
        val index = tenants.indexOfFirst { it.id == tenant.id }
        tenants[index] = tenant

        notifyItemChanged(index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TenantItemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_tenant, parent, false)
        return TenantItemViewHolder(view)
    }

    override fun getItemCount(): Int {
        return tenants.size
    }

    override fun onBindViewHolder(holder: TenantItemViewHolder, position: Int) {
        val tenant = tenants[position]

        holder.name.text = tenant.name

        holder.description.text = "Loading..."

        Constants.getDbRef().collection(Constants.ROOM_COLLECTION).document(tenant.roomId).get().addOnSuccessListener {
            val room = it.toObject(Room::class.java)
            if(room != null) holder.description.text = "${room.name} • Joined ${Date.timeAgo(tenant.startDate.toDate())}"
        }

        if (tenant.profile !== null) {
            Glide.with(holder.profile).load(tenant.profile.toUri()).into(holder.profile)
        }

        holder.verifiedBadge.visibility =
            if (tenant.documents.isNotEmpty()) View.VISIBLE else View.GONE
        holder.pendingBadge.visibility = if (tenant.documents.isEmpty()) View.VISIBLE else View.GONE
    }

    inner class TenantItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tenant_name)
        val description: TextView = view.findViewById(R.id.tenant_description)
        val profile: ImageView = view.findViewById(R.id.tenant_profile)
        val verifiedBadge: MaterialCardView = view.findViewById(R.id.tenant_verified)
        val pendingBadge: MaterialCardView = view.findViewById(R.id.tenant_pending)

        init {
            view.setOnClickListener {
                val tenant = tenants[adapterPosition]

                Intent(context, ViewTenant::class.java).apply {
                    putExtra("tenantId", tenant.id)
                }.let {
                    viewTenantLauncher.launch(it)
                }
            }
        }
    }
}