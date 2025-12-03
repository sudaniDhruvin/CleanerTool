package com.example.cleanertool.utils

import android.content.Context
import android.provider.ContactsContract
import android.util.Log
import java.util.regex.Pattern

object ContactUtils {
    private const val TAG = "ContactUtils"

    /**
     * Normalize phone number by removing spaces, dashes, parentheses, and other formatting
     */
    private fun normalizePhoneNumber(phoneNumber: String): String {
        return phoneNumber.replace(Regex("[\\s\\-\\(\\)\\.]"), "")
    }

    /**
     * Get contact name from phone number using multiple lookup strategies
     * @return Pair of (name, number) - name will be null if contact not found
     */
    fun getContactNameFromNumber(context: Context, phoneNumber: String?): Pair<String?, String> {
        if (phoneNumber.isNullOrBlank()) {
            Log.w(TAG, "Phone number is null or blank")
            return Pair(null, "Unknown number")
        }

        Log.d(TAG, "Looking up contact for number: $phoneNumber")

        // Try multiple lookup strategies
        val normalizedNumber = normalizePhoneNumber(phoneNumber)
        val lookupNumbers = mutableListOf<String>()
        
        // Add original number
        lookupNumbers.add(phoneNumber)
        
        // Add normalized number
        if (normalizedNumber != phoneNumber) {
            lookupNumbers.add(normalizedNumber)
        }
        
        // Try with last 10 digits (for numbers with country codes)
        if (normalizedNumber.length > 10) {
            val last10 = normalizedNumber.takeLast(10)
            if (last10 != normalizedNumber) {
                lookupNumbers.add(last10)
            }
        }
        
        // Try with last 7 digits (local number)
        if (normalizedNumber.length > 7) {
            val last7 = normalizedNumber.takeLast(7)
            if (last7 != normalizedNumber && last7.length >= 7) {
                lookupNumbers.add(last7)
            }
        }

        // Try each lookup strategy
        for (lookupNumber in lookupNumbers) {
            try {
                Log.d(TAG, "Trying lookup with number: $lookupNumber")
                
                // Method 1: Using PhoneLookup URI
                val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI
                val lookupUri = android.net.Uri.withAppendedPath(uri, android.net.Uri.encode(lookupNumber))
                
                val projection = arrayOf(
                    ContactsContract.PhoneLookup.DISPLAY_NAME,
                    ContactsContract.PhoneLookup.NUMBER,
                    ContactsContract.PhoneLookup._ID
                )
                
                context.contentResolver.query(
                    lookupUri,
                    projection,
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                        
                        if (nameIndex >= 0) {
                            val name = cursor.getString(nameIndex)
                            if (!name.isNullOrBlank()) {
                                val numberIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.NUMBER)
                                val number = if (numberIndex >= 0) {
                                    cursor.getString(numberIndex) ?: phoneNumber
                                } else {
                                    phoneNumber
                                }
                                Log.d(TAG, "Found contact: $name for number: $lookupNumber (original: $phoneNumber)")
                                return Pair(name, number)
                            }
                        }
                    }
                }
                
                // Method 2: Direct query on ContactsContract.CommonDataKinds.Phone
                val phoneUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                val phoneProjection = arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID
                )
                
                // Try exact match
                var selection = "${ContactsContract.CommonDataKinds.Phone.NUMBER} = ?"
                var selectionArgs = arrayOf(lookupNumber)
                
                context.contentResolver.query(
                    phoneUri,
                    phoneProjection,
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        if (nameIndex >= 0) {
                            val name = cursor.getString(nameIndex)
                            if (!name.isNullOrBlank()) {
                                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                val number = if (numberIndex >= 0) {
                                    cursor.getString(numberIndex) ?: phoneNumber
                                } else {
                                    phoneNumber
                                }
                                Log.d(TAG, "Found contact via direct query: $name for number: $lookupNumber")
                                return Pair(name, number)
                            }
                        }
                    }
                }
                
                // Try LIKE query for partial matches (numbers stored with formatting)
                selection = "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
                selectionArgs = arrayOf("%$lookupNumber%")
                
                context.contentResolver.query(
                    phoneUri,
                    phoneProjection,
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        if (nameIndex >= 0) {
                            val name = cursor.getString(nameIndex)
                            if (!name.isNullOrBlank()) {
                                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                val number = if (numberIndex >= 0) {
                                    cursor.getString(numberIndex) ?: phoneNumber
                                } else {
                                    phoneNumber
                                }
                                Log.d(TAG, "Found contact via LIKE query: $name for number: $lookupNumber")
                                return Pair(name, number)
                            }
                        }
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error looking up contact for number: $lookupNumber", e)
            }
        }
        
        // Contact not found after all strategies
        Log.d(TAG, "Contact not found for number: $phoneNumber after trying ${lookupNumbers.size} strategies")
        return Pair(null, phoneNumber)
    }
}

