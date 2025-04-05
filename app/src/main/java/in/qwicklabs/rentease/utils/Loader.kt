package `in`.qwicklabs.rentease.utils

import android.app.Dialog
import android.content.Context
import android.widget.TextView
import `in`.qwicklabs.rentease.R

class Loader(context: Context) {
    val dialog: Dialog = Dialog(context, R.style.TransparentDialog)
    var dialogTitle: TextView
    var dialogDes: TextView

    init {
        dialog.setContentView(R.layout.loader)
        dialog.setCancelable(false)

        dialogTitle = dialog.findViewById(R.id.dialogLoadingTitle)
        dialogDes = dialog.findViewById(R.id.dialogLoadingDes)
    }
}