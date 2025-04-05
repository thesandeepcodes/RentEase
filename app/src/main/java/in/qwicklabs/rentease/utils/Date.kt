package `in`.qwicklabs.rentease.utils

import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Comparator
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class Date {
    companion object {
        fun formatTimestamp(timestamp: Timestamp, format: String = "MMM, yyyy"): String{
            val date = timestamp.toDate()
            val sdf = SimpleDateFormat(format, Locale.ENGLISH)

            return sdf.format(date)
        }

        fun compareMonth(startDate: Date, endDate: Date, allowEqual: Boolean = false): Boolean {
            val calendarStart = Calendar.getInstance().apply { time = startDate }
            val calendarEnd = Calendar.getInstance().apply { time = endDate }

            return if (allowEqual) {
                calendarStart.get(Calendar.YEAR) > calendarEnd.get(Calendar.YEAR) ||
                        (calendarStart.get(Calendar.YEAR) == calendarEnd.get(Calendar.YEAR) &&
                                calendarStart.get(Calendar.MONTH) >= calendarEnd.get(Calendar.MONTH))
            } else {
                calendarStart.get(Calendar.YEAR) > calendarEnd.get(Calendar.YEAR) ||
                        (calendarStart.get(Calendar.YEAR) == calendarEnd.get(Calendar.YEAR) &&
                                calendarStart.get(Calendar.MONTH) > calendarEnd.get(Calendar.MONTH))
            }
        }

        fun timeAgo(date: Date): String {
            val now = Date()
            val diffInMillis = now.time - date.time

            return when (val seconds = TimeUnit.MILLISECONDS.toSeconds(diffInMillis)) {
                in 0..59 -> "$seconds seconds ago"
                in 60..3599 -> "${TimeUnit.SECONDS.toMinutes(seconds)} minutes ago"
                in 3600..86399 -> "${TimeUnit.SECONDS.toHours(seconds)} hours ago"
                in 86400..2591999 -> "${TimeUnit.SECONDS.toDays(seconds)} days ago"
                in 2592000..31103999 -> "${seconds / 2592000} months ago"
                else -> "${seconds / 31104000} years ago"
            }
        }
    }
}