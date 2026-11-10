package com.zai.callerid.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

data class ContactEntry(val name: String, val number: String)

object ContactsRepo {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /** All phone contacts, sorted by name. */
    fun load(context: Context): List<ContactEntry> {
        if (!hasPermission(context)) return emptyList()
        val out = mutableListOf<ContactEntry>()
        val seen = mutableSetOf<String>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID
            ),
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )?.use { c ->
            val nameIdx = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val idIdx = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            while (c.moveToNext()) {
                val name = c.getString(nameIdx) ?: continue
                val number = c.getString(numIdx) ?: continue
                val id = c.getLong(idIdx)
                if (seen.add("${id}_$number")) out += ContactEntry(name, number)
            }
        }
        return out
    }

    /** Reverse lookup: name for a number, or null if unknown. */
    fun nameFor(context: Context, number: String): String? {
        if (number.isBlank() || !hasPermission(context)) return null
        return try {
            val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI
                .buildUpon()
                .appendPath(number)
                .build()
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
