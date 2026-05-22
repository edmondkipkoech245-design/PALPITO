package com.example.palpito1;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class AdminMainActivity extends AppCompatActivity {

    private TextView adminNameText, adminEmailText, adminDeviceIdText;
    private EditText etStudentName, etAdminName, etCourseNameAction;
    private DatabaseReference mDatabase, mAllUsersRef, mCoursesRef;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_main);

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        mDatabase = FirebaseDatabase.getInstance().getReference().child("users").child(currentUser.getUid());
        mAllUsersRef = FirebaseDatabase.getInstance().getReference().child("users");
        mCoursesRef = FirebaseDatabase.getInstance().getReference().child("courses");

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.admin_main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        adminNameText = findViewById(R.id.adminNameText);
        adminEmailText = findViewById(R.id.adminEmailText);
        adminDeviceIdText = findViewById(R.id.adminDeviceIdText);
        etStudentName = findViewById(R.id.etStudentName);
        etAdminName = findViewById(R.id.etAdminName);
        etCourseNameAction = findViewById(R.id.etCourseNameAction);
        
        Button btnAdminLogout = findViewById(R.id.btnAdminLogout);

        adminEmailText.setText(currentUser.getEmail());
        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        adminDeviceIdText.setText("Device ID: " + deviceId);

        // Management Buttons
        findViewById(R.id.btnAddStudentAction).setOnClickListener(v -> manageUser("Student", true, etStudentName));
        findViewById(R.id.btnRemoveStudentAction).setOnClickListener(v -> manageUser("Student", false, etStudentName));
        findViewById(R.id.btnAddAdminAction).setOnClickListener(v -> manageUser("Admin", true, etAdminName));
        findViewById(R.id.btnRemoveAdminAction).setOnClickListener(v -> manageUser("Admin", false, etAdminName));
        findViewById(R.id.btnAddCourseAction).setOnClickListener(v -> manageCourse(true));
        findViewById(R.id.btnRemoveCourseAction).setOnClickListener(v -> manageCourse(false));

        findViewById(R.id.cardGps).setOnClickListener(v -> {
            startActivity(new Intent(AdminMainActivity.this, GpsVenueActivity.class));
        });

        btnAdminLogout.setOnClickListener(v -> {
            mAuth.signOut();
            startActivity(new Intent(AdminMainActivity.this, MainActivity.class));
            finish();
        });

        mDatabase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String name = snapshot.child("name").getValue(String.class);
                    adminNameText.setText(name);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(AdminMainActivity.this, "Error loading data", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void manageUser(String role, boolean add, EditText et) {
        String input = et.getText().toString().trim();
        if (input.isEmpty()) {
            Toast.makeText(this, "Please enter email/name", Toast.LENGTH_SHORT).show();
            return;
        }

        if (add) {
            // For a real app, this would involve creating a user in Firebase Auth or marking them in DB
            mAllUsersRef.child(input.replace(".", "_")).child("role").setValue(role)
                    .addOnSuccessListener(aVoid -> Toast.makeText(this, role + " " + input + " role assigned", Toast.LENGTH_SHORT).show());
        } else {
            mAllUsersRef.child(input.replace(".", "_")).removeValue()
                    .addOnSuccessListener(aVoid -> Toast.makeText(this, role + " " + input + " removed from DB", Toast.LENGTH_SHORT).show());
        }
        et.setText("");
    }

    private void manageCourse(boolean add) {
        String course = etCourseNameAction.getText().toString().trim();
        if (course.isEmpty()) {
            Toast.makeText(this, "Please enter course code", Toast.LENGTH_SHORT).show();
            return;
        }
        if (add) {
            mCoursesRef.child(course).setValue(true)
                    .addOnSuccessListener(aVoid -> Toast.makeText(this, "Course " + course + " added", Toast.LENGTH_SHORT).show());
        } else {
            mCoursesRef.child(course).removeValue()
                    .addOnSuccessListener(aVoid -> Toast.makeText(this, "Course " + course + " removed", Toast.LENGTH_SHORT).show());
        }
        etCourseNameAction.setText("");
    }
}
