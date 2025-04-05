package `in`.qwicklabs.rentease.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import androidx.core.graphics.createBitmap
import java.text.DecimalFormat

class AppUtils {
    companion object{
        fun alert(context: Context, title: String, message: String, buttonText: String = "Dismiss"){
            MaterialAlertDialogBuilder(context).setTitle(title).setMessage(message).setPositiveButton(buttonText){d,_->
                d.dismiss()
            }.show()
        }

        fun captureView(context: Context, view: View, name: String = "screenshot.png"): File?{
            val bitmap = createBitmap(view.width, view.height)
            val canvas = Canvas(bitmap)
            view.draw(canvas)

            val file = File(context.cacheDir, name)

            file.outputStream().use { out->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            return file
        }

        fun shareFileToWa(context: Context, file: File?, text: String, phoneNumber: String){
            if(file == null) return

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)

            val intent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_STREAM, uri)
                type = "image/png"
                putExtra("jid", "$phoneNumber@s.whatsapp.net")
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                `package` = "com.whatsapp"
            }

            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
            }
        }

        fun formatDecimal(number: Double): String {
            val format = DecimalFormat("#.##")
            format.isDecimalSeparatorAlwaysShown = false
            return format.format(number)
        }

        fun formatDecimal(number: Float): String {
            val format = DecimalFormat("#.##")
            format.isDecimalSeparatorAlwaysShown = false
            return format.format(number)
        }
    }
}