package com.contacts.contactapplication;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.Manifest;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.contacts.contactapplication.Contact;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final int CONTACTS_PERMISSION_REQUEST = 1;
    private static final long UPDATE_INTERVAL = 5 * 60 * 1000; // 5 minutes in milliseconds

    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateContactsRunnable = new Runnable() {
        @Override
        public void run() {
            backupContacts();
            handler.postDelayed(this, UPDATE_INTERVAL); // Schedule the task to run again after 5 minutes
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button backupButton = findViewById(R.id.backupButton);

        backupButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestContactsPermission();
                // Start periodic updates
                handler.postDelayed(updateContactsRunnable, UPDATE_INTERVAL);
            }
        });
    }

    private void requestContactsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_CONTACTS}, CONTACTS_PERMISSION_REQUEST);
        } else {
            backupContacts();
        }
    }
    private void backupContacts() {
        // Get the list of contacts
        List<Contact> contacts = getContacts();

        // Create a Firebase database reference
        DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference("contacts");

        // Process and upload contacts in batches
        int batchSize = 10; // Set your desired batch size
        int maxContactsToBackup = 1000; // Set the maximum number of contacts to backup

        for (int i = 0; i < Math.min(contacts.size(), maxContactsToBackup); i += batchSize) {
            int endIndex = Math.min(i + batchSize, Math.min(contacts.size(), maxContactsToBackup));
            List<Contact> batch = contacts.subList(i, endIndex);

            for (Contact contact : batch) {
                // Check if the contact key (ID) already exists in the database
                Query query = databaseReference.orderByChild("id").equalTo(contact.getId());

                query.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        // If a contact with the same key does not exist, add it
                        if (!dataSnapshot.exists()) {
                            // Create a unique key for each contact
                            String contactKey = databaseReference.push().getKey();

                            // Create a JSON object with contact data
                            Map<String, Object> contactData = new HashMap<>();
                            contactData.put("id", contact.getId());
                            contactData.put("name", contact.getName());
                            contactData.put("phoneNumber", contact.getPhoneNumber());

                            // Store the contact data under the unique key
                            databaseReference.child(contactKey).setValue(contactData);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        // Handle errors here if needed
                    }
                });
            }
        }

        Toast.makeText(MainActivity.this, "Contacts backed up to Firebase (up to 1000 contacts).", Toast.LENGTH_SHORT).show();
    }

    private List<Contact> getContacts() {
        // Get the contact list from the Android system
        ContentResolver contentResolver = getContentResolver();
        Cursor cursor = contentResolver.query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null);

        // Create a list to store the contacts
        List<Contact> contacts = new ArrayList<>();

        // Iterate over the cursor and add each contact to the list
        while (cursor.moveToNext()) {
            String id = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID));
            String name = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));

            // Retrieve the phone number for the contact
            String phoneNumber = getPhoneNumber(id);

            // Create a Contact object with the phone number
            Contact contact = new Contact(id, name, phoneNumber);
            contacts.add(contact);
        }

        cursor.close();

        return contacts;
    }

    private String getPhoneNumber(String contactId) {
        String phoneNumber = null;

        // Query the phone numbers for the given contact ID
        Cursor phoneCursor = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                new String[]{contactId},
                null
        );

        if (phoneCursor.moveToFirst()) {
            phoneNumber = phoneCursor.getString(phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
        }

        phoneCursor.close();

        return phoneNumber;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == CONTACTS_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                backupContacts();
            } else {
                Toast.makeText(this, "Permission denied. Cannot backup contacts.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Remove the periodic updates when the activity is destroyed
        handler.removeCallbacks(updateContactsRunnable);
    }
}