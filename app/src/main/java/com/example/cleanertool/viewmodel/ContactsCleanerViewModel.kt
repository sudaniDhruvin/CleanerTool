package com.example.cleanertool.viewmodel

import android.app.Application
import android.content.Context
import android.provider.ContactsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ContactEntry(
    val id: Long,
    val name: String,
    val phone: String
)

data class ContactDuplicateGroup(
    val normalizedKey: String,
    val entries: List<ContactEntry>
)

class ContactsCleanerViewModel(application: Application) : AndroidViewModel(application) {

    private val _duplicateGroups = MutableStateFlow<List<ContactDuplicateGroup>>(emptyList())
    val duplicateGroups: StateFlow<List<ContactDuplicateGroup>> = _duplicateGroups.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadDuplicates(context: Context = getApplication()) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val duplicates = withContext(Dispatchers.IO) {
                    val map = mutableMapOf<String, MutableList<ContactEntry>>()
                    val projection = arrayOf(
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    )
                    context.contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        projection,
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        val idCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                        val nameCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        val numberCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        while (cursor.moveToNext()) {
                            val id = if (idCol >= 0) cursor.getLong(idCol) else continue
                            val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "Unknown" else "Unknown"
                            val number = if (numberCol >= 0) cursor.getString(numberCol) ?: continue else continue
                            val normalized = normalizePhone(number)
                            if (normalized.isNotBlank()) {
                                map.getOrPut(normalized) { mutableListOf() }
                                    .add(ContactEntry(id = id, name = name, phone = number))
                            }
                        }
                    }
                    map.values
                        .filter { it.size > 1 }
                        .map { group ->
                            ContactDuplicateGroup(
                                normalizedKey = group.first().phone,
                                entries = group.sortedBy { it.name }
                            )
                        }
                        .sortedByDescending { it.entries.size }
                }
                _duplicateGroups.value = duplicates
            } catch (e: SecurityException) {
                _error.value = "Contacts permission required"
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load contacts"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun normalizePhone(value: String): String {
        return value.filter { it.isDigit() }
    }
}

