package com.example.palpito1;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Locale;

public class studentMainActivity2 extends AppCompatActivity {

    private TextView studentNameText, studentDeviceIdText, tvActiveCourse, tvActiveVenue, tvExpectedGps, tvCurrentGps, tvSessionTime, tvAttendanceStatus;
    private EditText etEnrollCourse;
    private LinearLayout enrolledCoursesContainer;
    private Button btnMarkAttendance;
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase, mUserCoursesRef, mAttendanceRef, mSessionsRef;
    private FusedLocationProviderClient fusedLocationClient;
    private double expectedLat, expectedLon;
    private String activeSessionId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_student_main2);

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        mDatabase = FirebaseDatabase.getInstance().getReference().child("users").child(currentUser.getUid());
        mUserCoursesRef = FirebaseDatabase.getInstance().getReference().child("user_courses").child(currentUser.getUid());
        mAttendanceRef = FirebaseDatabase.getInstance().getReference().child("attendance").child(currentUser.getUid());
        mSessionsRef = FirebaseDatabase.getInstance().getReference().child("sessions");
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize UI elements
        studentNameText = findViewById(R.id.studentNameText);
        TextView studentEmailHeader = findViewById(R.id.studentEmailHeader);
        studentDeviceIdText = findViewById(R.id.studentDeviceIdText);
        etEnrollCourse = findViewById(R.id.etEnrollCourse);
        enrolledCoursesContainer = findViewById(R.id.enrolledCoursesContainer);
        tvActiveCourse = findViewById(R.id.tvActiveCourse);
        tvActiveVenue = findViewById(R.id.tvActiveVenue);
        tvExpectedGps = findViewById(R.id.tvExpectedGps);
        tvCurrentGps = findViewById(R.id.tvCurrentGps);
        tvSessionTime = findViewById(R.id.tvSessionTime);
        tvAttendanceStatus = findViewById(R.id.tvAttendanceStatus);
        btnMarkAttendance = findViewById(R.id.btnMarkAttendance);

        Button btnLogout = findViewById(R.id.btnLogout);
        Button btnEnrollNow = findViewById(R.id.btnEnrollNow);
        Button btnReports = findViewById(R.id.btnReports);

        studentEmailHeader.setText(currentUser.getEmail());

        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        studentDeviceIdText.setText("Device ID: " + deviceId);

        btnEnrollNow.setOnClickListener(v -> enrollInCourse());
        btnReports.setOnClickListener(v -> showAttendanceReports());
        btnMarkAttendance.setOnClickListener(v -> markAttendance());

        // Logout logic
        btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            startActivity(new Intent(studentMainActivity2.this, MainActivity.class));
            finish();
        });

        fetchStudentInfo();
        listenForActiveSessions();
        loadEnrolledCourses();
        fetchCurrentLocation();
    }

    private void fetchCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                tvCurrentGps.setText(String.format(Locale.getDefault(), "Current GPS: %.4f, %.4f", location.getLatitude(), location.getLongitude()));
            }
        });
    }

    private void loadEnrolledCourses() {
        mUserCoursesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                enrolledCoursesContainer.removeAllViews();
                if (!snapshot.exists()) {
                    TextView tv = new TextView(studentMainActivity2.this);
                    tv.setText("No enrolled courses yet.");
                    enrolledCoursesContainer.addView(tv);
                    return;
                }
                for (DataSnapshot ds : snapshot.getChildren()) {
                    String course = ds.getValue(String.class);
                    String courseKey = ds.getKey();
                    
                    // Create a "form" like card for the course
                    TextView courseView = new TextView(studentMainActivity2.this);
                    courseView.setText("Course: " + course);
                    courseView.setPadding(20, 20, 20, 20);
                    courseView.setTextSize(16);
                    courseView.setTextColor(getResources().getColor(android.R.color.black));
                    courseView.setBackgroundResource(android.R.drawable.edit_text); // Form-like look
                    
                    // Clicking shows enrolled students in this course
                    courseView.setOnClickListener(v -> {
                        if (courseKey != null) {
                            showEnrolledStudents(courseKey, course);
                        }
                    });

                    // Long click to delete
                    courseView.setOnLongClickListener(v -> {
                        new AlertDialog.Builder(studentMainActivity2.this)
                                .setTitle("Delete Course")
                                .setMessage("Are you sure you want to remove " + course + "?")
                                .setPositiveButton("Yes", (dialog, which) -> {
                                    if (courseKey != null) {
                                        mUserCoursesRef.child(courseKey).removeValue();
                                        FirebaseDatabase.getInstance().getReference().child("course_enrollments")
                                                .child(courseKey).child(mAuth.getUid()).removeValue()
                                                .addOnSuccessListener(aVoid -> Toast.makeText(studentMainActivity2.this, "Course removed", Toast.LENGTH_SHORT).show());
                                    }
                                })
                                .setNegativeButton("No", null)
                                .show();
                        return true;
                    });

                    enrolledCoursesContainer.addView(courseView);
                    
                    // Add a small spacer
                    View spacer = new View(studentMainActivity2.this);
                    spacer.setLayoutParams(new LinearLayout.LayoutParams(1, 10));
                    enrolledCoursesContainer.addView(spacer);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void showEnrolledStudents(String courseKey, String courseName) {
        FirebaseDatabase.getInstance().getReference().child("course_enrollments").child(courseKey)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            StringBuilder studentsList = new StringBuilder();
                            for (DataSnapshot ds : snapshot.getChildren()) {
                                String name = ds.getValue(String.class);
                                studentsList.append("• ").append(name).append("\n");
                            }
                            new AlertDialog.Builder(studentMainActivity2.this)
                                    .setTitle("Students in " + courseName)
                                    .setMessage(studentsList.toString())
                                    .setPositiveButton("Close", null)
                                    .show();
                        } else {
                            Toast.makeText(studentMainActivity2.this, "No students enrolled yet.", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void listenForActiveSessions() {
        mSessionsRef.orderByChild("active").equalTo(true).limitToLast(1).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    for (DataSnapshot ds : snapshot.getChildren()) {
                        activeSessionId = ds.getKey();
                        String course = ds.child("course").getValue(String.class);
                        String venue = ds.child("venue").getValue(String.class);
                        String time = ds.child("time").getValue(String.class);
                        expectedLat = ds.child("latitude").getValue(Double.class);
                        expectedLon = ds.child("longitude").getValue(Double.class);

                        tvActiveCourse.setText("Course: " + course);
                        tvActiveVenue.setText("Venue: " + venue);
                        tvSessionTime.setText("Time: " + time);
                        tvExpectedGps.setText(String.format(Locale.getDefault(), "Expected GPS: %.4f, %.4f", expectedLat, expectedLon));
                        
                        checkIfAttendanceMarked(activeSessionId);
                    }
                } else {
                    activeSessionId = null;
                    tvActiveCourse.setText("Course: --");
                    tvActiveVenue.setText("Venue: --");
                    tvSessionTime.setText("Time: --");
                    tvExpectedGps.setText("Expected GPS: --");
                    tvAttendanceStatus.setText("Attendance: NO ACTIVE SESSION");
                    tvAttendanceStatus.setTextColor(getResources().getColor(android.R.color.darker_gray));
                    btnMarkAttendance.setEnabled(false);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void checkIfAttendanceMarked(String sessionId) {
        mAttendanceRef.child(sessionId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    tvAttendanceStatus.setText("Attendance: MARKED");
                    tvAttendanceStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                    btnMarkAttendance.setEnabled(false);
                } else {
                    tvAttendanceStatus.setText("Attendance: NOT MARKED");
                    tvAttendanceStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                    btnMarkAttendance.setEnabled(true);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void markAttendance() {
        BiometricManager biometricManager = BiometricManager.from(this);
        switch (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL)) {
            case BiometricManager.BIOMETRIC_SUCCESS:
                showBiometricPrompt();
                break;
            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
            case BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED:
            case BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED:
            case BiometricManager.BIOMETRIC_STATUS_UNKNOWN:
                // Fallback to GPS only if biometric is not available or unsupported
                proceedWithGpsVerification();
                break;
        }
    }

    private void showBiometricPrompt() {
        java.util.concurrent.Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt biometricPrompt = new BiometricPrompt(studentMainActivity2.this,
                executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                Toast.makeText(getApplicationContext(), "Authentication error: " + errString, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                proceedWithGpsVerification();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Toast.makeText(getApplicationContext(), "Authentication failed", Toast.LENGTH_SHORT).show();
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Biometric Verification")
                .setSubtitle("Verify your identity to mark attendance")
                .setNegativeButtonText("Cancel")
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    private void proceedWithGpsVerification() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
            return;
        }

        String currentDeviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        mDatabase.child("deviceId").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String storedDeviceId = snapshot.getValue(String.class);
                if (storedDeviceId != null && !storedDeviceId.equals(currentDeviceId)) {
                    Toast.makeText(studentMainActivity2.this, "Error: You can only mark attendance from your registered device!", Toast.LENGTH_LONG).show();
                    return;
                }

                if (ActivityCompat.checkSelfPermission(studentMainActivity2.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                fusedLocationClient.getLastLocation().addOnSuccessListener(studentMainActivity2.this, location -> {
                    if (location != null) {
                        double currentLat = location.getLatitude();
                        double currentLon = location.getLongitude();
                        tvCurrentGps.setText(String.format(Locale.getDefault(), "Current GPS: %.4f, %.4f", currentLat, currentLon));

                        float[] results = new float[1];
                        Location.distanceBetween(currentLat, currentLon, expectedLat, expectedLon, results);
                        float distanceInMeters = results[0];

                        if (distanceInMeters <= 10) { // 10 meters radius
                            if (activeSessionId != null) {
                                mAttendanceRef.child(activeSessionId).setValue(com.google.firebase.database.ServerValue.TIMESTAMP)
                                        .addOnSuccessListener(aVoid -> {
                                            Toast.makeText(studentMainActivity2.this, "Attendance Marked Successfully!", Toast.LENGTH_SHORT).show();
                                            tvAttendanceStatus.setText("Attendance: MARKED");
                                            tvAttendanceStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                                            btnMarkAttendance.setEnabled(false);
                                        });
                            }
                        } else {
                            Toast.makeText(studentMainActivity2.this, "Warning: You must be within 10 meters of the venue to mark attendance! (You are " + (int)distanceInMeters + "m away)", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Toast.makeText(studentMainActivity2.this, "Unable to get current location. Ensure GPS is ON.", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(studentMainActivity2.this, "Security verification failed.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void enrollInCourse() {
        String course = etEnrollCourse.getText().toString().trim();
        if (course.isEmpty()) {
            Toast.makeText(this, "Please enter a course code", Toast.LENGTH_SHORT).show();
            return;
        }

        String courseKey = course.replace(".", "_").replace("#", "_").replace("$", "_").replace("[", "_").replace("]", "_");

        mUserCoursesRef.child(courseKey).setValue(course)
                .addOnSuccessListener(aVoid -> {
                    // Also add to the global course_students list for lookup
                    FirebaseDatabase.getInstance().getReference().child("course_enrollments")
                            .child(courseKey).child(mAuth.getUid()).setValue(studentNameText.getText().toString());

                    Toast.makeText(this, "Enrolled in " + course, Toast.LENGTH_SHORT).show();
                    etEnrollCourse.setText("");
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Enrollment failed", Toast.LENGTH_SHORT).show());
    }

    private void showAttendanceReports() {
        mAttendanceRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    long totalRecords = snapshot.getChildrenCount();
                    java.util.List<String> reportList = new java.util.ArrayList<>();
                    final int[] fetchedCount = {0};

                    for (DataSnapshot ds : snapshot.getChildren()) {
                        String sessionId = ds.getKey();
                        if (sessionId == null) continue;

                        mSessionsRef.child(sessionId).addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot sessionSnapshot) {
                                fetchedCount[0]++;
                                if (sessionSnapshot.exists()) {
                                    String course = sessionSnapshot.child("course").getValue(String.class);
                                    String date = sessionSnapshot.child("date").getValue(String.class);
                                    String time = sessionSnapshot.child("time").getValue(String.class);
                                    reportList.add("• " + course + " (" + date + " " + time + ")");
                                }

                                if (fetchedCount[0] == totalRecords) {
                                    showReportDialog(reportList);
                                }
                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError error) {
                                fetchedCount[0]++;
                                if (fetchedCount[0] == totalRecords) {
                                    showReportDialog(reportList);
                                }
                            }
                        });
                    }
                } else {
                    Toast.makeText(studentMainActivity2.this, "No attendance records found", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(studentMainActivity2.this, "Error fetching reports", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showReportDialog(java.util.List<String> reports) {
        StringBuilder sb = new StringBuilder();
        if (reports.isEmpty()) {
            sb.append("No detailed records found.");
        } else {
            for (String r : reports) {
                sb.append(r).append("\n");
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("My Attendance Reports")
                .setMessage(sb.toString())
                .setPositiveButton("OK", null)
                .show();
    }

    // Fetch name from Database
    private void fetchStudentInfo() {
        mDatabase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String name = snapshot.child("name").getValue(String.class);
                    studentNameText.setText(name);
                } else {
                    studentNameText.setText("Student");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(studentMainActivity2.this, "Failed to load data", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
