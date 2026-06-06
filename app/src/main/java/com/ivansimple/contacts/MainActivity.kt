package com.ivansimple.contacts

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Bundle
import android.provider.ContactsContract
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import java.util.Locale

private data class Contact(
    val name: String?,
    val phoneNumber: String?,
    val email: String?
)

class MainActivity : AppCompatActivity() {

    private val tag = "ContactsLab2"

    private lateinit var header: TextView
    private lateinit var listBody: TextView
    private lateinit var detailsBody: TextView
    private lateinit var actionHint: TextView

    private var contacts: List<Contact> = emptyList()

    private var selectedIndex: Int = -1

    private val requestContactsPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            header.text = getString(R.string.contacts_have_permission)
            loadContactsAndShow()
        } else {
            header.text = getString(R.string.contacts_no_permission)
            clearContactsUi()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        header = findViewById(R.id.contactsHeader)
        listBody = findViewById(R.id.contactsList)
        detailsBody = findViewById(R.id.contactsDetails)
        actionHint = findViewById(R.id.contactsHint)

        ensurePermissionAndAct()
    }

    private fun ensurePermissionAndAct() {
        if (hasContactsPermission()) {
            header.text = getString(R.string.contacts_have_permission)
            loadContactsAndShow()
        } else {
            header.text = getString(R.string.contacts_no_permission)
            clearContactsUi()
            actionHint.isVisible = true
            requestContactsPermission.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun hasContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun clearContactsUi() {
        listBody.text = ""
        detailsBody.text = ""
        selectedIndex = -1
        actionHint.isVisible = false
    }

    private fun loadContactsAndShow() {
        contacts = try {
            fetchAllContacts(contentResolver)
        } catch (e: SecurityException) {
            Log.e(tag, "SecurityException while fetching contacts", e)
            emptyList()
        } catch (e: Exception) {
            Log.e(tag, "Unexpected error while fetching contacts", e)
            emptyList()
        }

        renderContactsList()
    }

    private fun renderContactsList() {
        if (contacts.isEmpty()) {
            listBody.text = getString(R.string.contacts_empty)
            detailsBody.text = ""
            selectedIndex = -1
            return
        }

        renderContactsWithDynamicClickables()
    }

    private fun renderContactsWithDynamicClickables() {
        listBody.text = contacts.mapIndexed { index, c ->
            val displayName = c.name?.trim().orEmpty()
            val name = if (displayName.isNotEmpty()) displayName else getString(R.string.contacts_unknown_name)
            String.format(Locale.US, "%02d. %s", index + 1, name)
        }.joinToString("\n")

        listBody.setOnClickListener { _: View ->
            val text = listBody.text?.toString().orEmpty()
            val firstLine = text.split("\n").firstOrNull().orEmpty()
            val number = firstLine.takeWhile { it.isDigit() }
            val idx = number.toIntOrNull()?.minus(1) ?: 0
            showContactDetails(idx.coerceIn(0, contacts.lastIndex))
        }

        actionHint.isVisible = true
        actionHint.text = getString(R.string.contacts_click_hint)
    }

    private fun showContactDetails(index: Int) {
        selectedIndex = index
        val c = contacts.getOrNull(index) ?: return

        val name = c.name?.takeIf { it.isNotBlank() } ?: getString(R.string.contacts_unknown_name)
        val phone = c.phoneNumber?.takeIf { it.isNotBlank() } ?: getString(R.string.contacts_no_phone)
        val email = c.email?.takeIf { it.isNotBlank() } ?: getString(R.string.contacts_no_email)

        detailsBody.text = getString(
            R.string.contacts_details_format,
            name,
            phone,
            email
        )

        Log.d(tag, "Selected contact index=$index name=$name")
    }
}

private fun Cursor?.getStringOrNull(columnIndex: Int): String? {
    if (this == null) return null
    if (columnIndex < 0) return null
    return if (isNull(columnIndex)) null else getString(columnIndex)
}

@SuppressLint("Range")
private fun fetchAllContacts(contentResolver: ContentResolver): List<Contact> {
    val result = mutableListOf<Contact>()

    val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI

    contentResolver.query(
        uri,
        null,
        null,
        null,
        null
    ).use { cursor: Cursor? ->
        if (cursor == null) return emptyList()

        val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val phoneIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val emailIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)

        while (cursor.moveToNext()) {
            val name = cursor.getStringOrNull(nameIndex)
            val phoneNumber = cursor.getStringOrNull(phoneIndex)
            val email = cursor.getStringOrNull(emailIndex)
            result.add(Contact(name, phoneNumber, email))
        }
    }

    return result.filter { it.name != null || it.phoneNumber != null || it.email != null }
}

