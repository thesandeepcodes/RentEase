package `in`.qwicklabs.rentease.constants

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference

class Constants {
    private lateinit var userRef: DocumentReference
    private var storageRef: StorageReference = FirebaseStorage.getInstance().reference

    companion object {
        var USER = FirebaseAuth.getInstance().currentUser

        const val DB_NAME = "Landlords"

        const val ROOM_COLLECTION = "Rooms"
        const val ROOM_PREVIEW_IMG_PATH = "/rooms/preview"

        const val CHARGE_COLLECTION = "Charges"

        const val METER_COLLECTION = "Meters"
        const val METER_READING_COLLECTION = "Readings"

        const val TENANT_COLLECTION = "Tenants"
        const val TENANT_DOCUMENT_PATH= "/tenants/docs/"
        const val TENANT_PROFILE_PATH="/tenants/profiles/"

        const val BILL_COLLECTION = "Bills"

        const val TRANSACTION_COLLECTION = "Transactions"

        const val EXPENSE = "Expense"

        fun getDbRef(): DocumentReference{
            return Constants().userRef
        }

        fun getStorageRef(): StorageReference{
            return Constants().storageRef
        }
    }

    fun revalidateUser(){
        val user = FirebaseAuth.getInstance().currentUser
        USER = user

        if(user?.uid !== null){
            userRef = FirebaseFirestore.getInstance().collection(DB_NAME).document(user.uid)
        }
    }

    init {
        revalidateUser()
    }
}