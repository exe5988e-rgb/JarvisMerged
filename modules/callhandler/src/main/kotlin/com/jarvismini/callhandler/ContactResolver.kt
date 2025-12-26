package com.jarvismini.callhandler

import android.content.Context
import android.provider.ContactsContract

object ContactResolver {

    fun isContact(context: Context, number: String): Boolean {
        val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI
            .buildUpon()
            .appendPath(number)
            .build()

        context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup._ID),
            null,
            null,
            null
        )?.use { cursor ->
            return cursor.moveToFirst()
        }

        return false
    }
}
