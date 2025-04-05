package `in`.qwicklabs.rentease

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import `in`.qwicklabs.rentease.activities.Charges
import `in`.qwicklabs.rentease.activities.Collect
import `in`.qwicklabs.rentease.activities.Meters
import `in`.qwicklabs.rentease.activities.Rooms
import `in`.qwicklabs.rentease.auth.Login
import `in`.qwicklabs.rentease.constants.Constants
import `in`.qwicklabs.rentease.databinding.ActivityMainBinding
import `in`.qwicklabs.rentease.databinding.BottomAddExpenseBinding
import `in`.qwicklabs.rentease.fragments.Bills
import `in`.qwicklabs.rentease.fragments.Dashboard
import `in`.qwicklabs.rentease.fragments.Tenants
import `in`.qwicklabs.rentease.fragments.Transactions
import `in`.qwicklabs.rentease.model.Transaction
import `in`.qwicklabs.rentease.model.TransactionType
import `in`.qwicklabs.rentease.utils.AppUtils
import `in`.qwicklabs.rentease.utils.Loader
import java.util.UUID


class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        val splashScreen = installSplashScreen()
        var keepSplashScreen = true
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({ keepSplashScreen = false }, 1000)

        splashScreen.setKeepOnScreenCondition { keepSplashScreen }

        if (auth.currentUser == null) {
            val intent = Intent(this, Login::class.java)
            startActivity(intent)
            finish()
            return
        }

        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        binding.toolbar.setNavigationOnClickListener {
            binding.main.open()
        }

        setUser()

        launchFragment(Dashboard())

        binding.toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_collect -> {
                    startActivity(Intent(this, Collect::class.java))
                }

                R.id.action_expense -> {
                    showExpense()
                }
            }

            true
        }

        binding.bottomNavigationView.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.nav_home -> launchFragment(Dashboard())
                R.id.nav_bills -> launchFragment(Bills())
                R.id.nav_tenants -> launchFragment(Tenants())
                R.id.nav_logs -> launchFragment(Transactions())
            }

            true
        }

        binding.navigationView.setNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.tabRooms -> launchActivity(Rooms::class.java)
                R.id.tabCharges -> launchActivity(Charges::class.java)
                R.id.tabMeters -> launchActivity(Meters::class.java)
                R.id.tabShare -> {
                    try {
                        Intent(Intent.ACTION_SEND).apply {
                            setType("text/plain")
                            putExtra(Intent.EXTRA_SUBJECT, applicationInfo.name)
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "✨ I manage my tenants effortlessly with this amazing app! 🏡📲\n\nIt’s a game-changer—simple, smooth, and super convenient!\n\n✅ Give it a try and make your life easier:\n\n👉 https://play.google.com/store/apps/details?id=$packageName"
                            )
                        }.let { intent ->
                            startActivity(Intent.createChooser(intent, "choose one"))
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this, "Cannot share at the moment", Toast.LENGTH_SHORT)
                            .show()
                    }
                }

                R.id.tabRate -> {
                    try {
                        Intent(
                            Intent.ACTION_VIEW,
                            "https://play.google.com/store/apps/details?id=$packageName".toUri()
                        ).apply {
                            setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }.let { intent ->
                            startActivity(intent)
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this, "Something went wrong.", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            }

            binding.main.closeDrawer(GravityCompat.START)

            true
        }

        binding.navigationView.menu.findItem(R.id.tabLogout).actionView?.findViewById<LinearLayout>(
            R.id.btnLogout
        )?.setOnClickListener {
            val dialog = MaterialAlertDialogBuilder(this).setTitle("Logout")
                .setMessage("Are you sure you want to log out?")
            dialog.setPositiveButton("Yes") { d, _ ->
                d.dismiss()

                logout()
                Intent(this, Login::class.java).also {
                    startActivity(it)
                    finish()
                }
            }

            dialog.setNegativeButton("No") { d, _ ->
                d.dismiss()
            }

            dialog.show()
        }
    }


    private fun setUser() {
        val currentUser = auth.currentUser

        val navigationHeaderView = binding.navigationView.getHeaderView(0)
        val profileView = navigationHeaderView.findViewById<ImageView>(R.id.menuDrawerUserProfile)
        val profileDisplayName =
            navigationHeaderView.findViewById<TextView>(R.id.menuDrawerUserDisplayName)
        val profileEmail = navigationHeaderView.findViewById<TextView>(R.id.menuDrawerUserEmail)

        profileDisplayName.text = currentUser?.displayName ?: "Username"
        profileEmail.text = currentUser?.email ?: "hello@rentease.com"
        Glide.with(profileView).load(currentUser?.photoUrl).into(profileView)
    }

    private fun launchFragment(name: Fragment) {
        supportFragmentManager.beginTransaction().replace(R.id.contentArea, name).commit()
    }

    private fun launchActivity(activity: Class<out Activity>) {
        startActivity(Intent(this, activity))
    }


    private fun showExpense() {
        val bottomSheetDialog = BottomSheetDialog(this)

        val expense = BottomAddExpenseBinding.inflate(layoutInflater)
        val loader = Loader(this)
        loader.dialogTitle.text = "Adding Expense.."

        expense.expenseButton.setOnClickListener {
            val name = expense.expenseName.text.toString()
            val amount = expense.expenseAmount.text.toString()

            if (name.isEmpty() || amount.isEmpty()) {
                AppUtils.alert(
                    this,
                    "Wait!",
                    "All fields are mandatory. Please make sure you have entered name and amount"
                )
                return@setOnClickListener
            }

            loader.dialog.show()

            val transaction = Transaction(
                UUID.randomUUID().toString(),
                TransactionType.EXPENSE,
                name,
                null,
                null,
                null,
                Timestamp.now(),
                amount.toDouble()
            )

            Constants.getDbRef().collection(Constants.EXPENSE).document(Constants.EXPENSE).get().addOnSuccessListener {snapshot->
                val expenseValue = snapshot.getDouble("expense") ?: 0.0

                Constants.getDbRef().collection(Constants.EXPENSE).document(Constants.EXPENSE).set(
                    mapOf(
                        "expense" to (transaction.amount + expenseValue)
                    )
                )
            }

            Constants.getDbRef().collection(Constants.TRANSACTION_COLLECTION)
                .document(transaction.id).set(transaction).addOnSuccessListener {
                bottomSheetDialog.dismiss()
            }.addOnFailureListener { e ->
                AppUtils.alert(this, "Oops!", "Unable to add expense: ${e.message}")
            }.addOnCompleteListener {
                loader.dialog.dismiss()
            }
        }

        expense.expenseClose.setOnClickListener { bottomSheetDialog.dismiss() }
        bottomSheetDialog.setContentView(expense.root)
        bottomSheetDialog.show()
    }

    private fun logout() {
        auth.signOut()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val actionProfileItem = menu?.findItem(R.id.action_profile)

        if (auth.currentUser?.photoUrl !== null) {
            Glide.with(this)
                .asBitmap()
                .load(auth.currentUser?.photoUrl)
                .circleCrop()
                .into(object : CustomTarget<Bitmap>(150, 150) {
                    override fun onResourceReady(
                        resource: Bitmap,
                        transition: Transition<in Bitmap>?
                    ) {
                        actionProfileItem?.setIcon(resource.toDrawable(resources))
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {}
                })
        }

        return super.onPrepareOptionsMenu(menu)
    }

    @SuppressLint("MissingSuperCall")
    override fun onBackPressed() {
        if (binding.main.isDrawerOpen(GravityCompat.START)) {
            binding.main.closeDrawer(GravityCompat.START)
        } else {
            onBackPressedDispatcher.onBackPressed()
        }
    }
}
