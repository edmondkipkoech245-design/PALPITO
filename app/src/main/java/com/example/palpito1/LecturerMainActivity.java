package com.example.palpito1;

import android.Manifest;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
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

import java.util.Calendar;
import java.util.Locale;

public class LecturerMainActivity extends AppCompatActivity {

    private TextView lecturerNameText, tvLecCurrentLocation, lecturerDeviceIdText, tvSelectDate, tvSelectTime;
    private EditText etCourseName, etVenueName, etLecLat, etLecLon;
    private LinearLayout sessionsContainer, teachingCoursesContainer;
    private Button btnOpenSession, btnEndSession, btnAutoGps;
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase, mSessionsDatabase, mTeachingCoursesDatabase;
    private FusedLocationProviderClient fusedLocationClient;
    private String currentSessionId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lecturer_main);

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        mDatabase = FirebaseDatabase.getInstance().getReference().child("users").child(currentUser.getUid());
        mSessionsDatabase = FirebaseDatabase.getInstance().getReference().child("sessions");
        mTeachingCoursesDatabase = FirebaseDatabase.getInstance().getReference().child("lecturer_courses").child(currentUser.getUid());
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.lecturer_main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize UI elements
        lecturerNameText = findViewById(R.id.lecturerNameText);
        tvLecCurrentLocation = findViewById(R.id.tvLecCurrentLocation);
        lecturerDeviceIdText = findViewById(R.id.lecturerDeviceIdText);
        etCourseName = findViewById(R.id.etCourseName);
        etVenueName = findViewById(R.id.etVenueName);
        etLecLat = findViewById(R.id.etLecLat);
        etLecLon = findViewById(R.id.etLecLon);
        tvSelectDate = findViewById(R.id.tvSelectDate);
        tvSelectTime = findViewById(R.id.tvSelectTime);
        sessionsContainer = findViewById(R.id.sessionsContainer);
        teachingCoursesContainer = findViewById(R.id.teachingCoursesContainer);

        TextView lecturerEmailHeader = findViewById(R.id.lecturerEmailHeader);
        Button btnLecLogout = findViewById(R.id.btnLecLogout);
        Button btnLecRedFlags = findViewById(R.id.btnLecRedFlags);
        btnAutoGps = findViewById(R.id.btnAutoGps);
        btnOpenSession = findViewById(R.id.btnOpenSession);
        btnEndSession = findViewById(R.id.btnEndSession);
        Button btnLecReports = findViewById(R.id.btnLecReports);

        lecturerEmailHeader.setText(currentUser.getEmail());

        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        lecturerDeviceIdText.setText("Device ID: " + deviceId);

        // Date and Time Pickers
        tvSelectDate.setOnClickListener(v -> {
            if (currentSessionId == null) showDatePicker();
        });
        tvSelectTime.setOnClickListener(v -> {
            if (currentSessionId == null) showTimePicker();
        });

        // Get Current Location for status view
        getLastLocation(false);

        btnAutoGps.setOnClickListener(v -> getLastLocation(true));

        btnOpenSession.setOnClickListener(v -> openSession());
        btnEndSession.setOnClickListener(v -> endSession());

        btnLecReports.setOnClickListener(v -> showLecturerReports());

        loadSessions();
        loadTeachingCourses();
        checkActiveSession();

        btnLecLogout.setOnClickListener(v -> {
            mAuth.signOut();
            startActivity(new Intent(LecturerMainActivity.this, MainActivity.class));
            finish();
        });

        btnLecRedFlags.setOnClickListener(v -> {
            startActivity(new Intent(LecturerMainActivity.this, RedFlagActivity.class));
        });

        mDatabase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String name = snapshot.child("name").getValue(String.class);
                    lecturerNameText.setText(name);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(LecturerMainActivity.this, "Error loading data", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadTeachingCourses() {
        mTeachingCoursesDatabase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                teachingCoursesContainer.removeAllViews();
                if (!snapshot.exists()) {
                    TextView tv = new TextView(LecturerMainActivity.this);
                    tv.setText("No assigned courses yet.");
                    teachingCoursesContainer.addView(tv);
                    return;
                }
                for (DataSnapshot ds : snapshot.getChildren()) {
                    String courseName = ds.getValue(String.class);
                    TextView courseView = new TextView(LecturerMainActivity.this);
                    courseView.setText("• " + courseName);
                    courseView.setPadding(0, 10, 0, 10);
                    courseView.setTextSize(14);
                    teachingCoursesContainer.addView(courseView);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void loadSessions() {
        mSessionsDatabase.orderByChild("lecturerId").equalTo(mAuth.getUid())
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        sessionsContainer.removeAllViews();
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            String sessionId = ds.getKey();
                            String course = ds.child("course").getValue(String.class);
                            String venue = ds.child("venue").getValue(String.class);
                            String date = ds.child("date").getValue(String.class);
                            String time = ds.child("time").getValue(String.class);
                            Double lat = ds.child("latitude").getValue(Double.class);
                            Double lon = ds.child("longitude").getValue(Double.class);
                            Boolean isActive = ds.child("active").getValue(Boolean.class);

                            String status = (isActive != null && isActive) ? "[ACTIVE]" : "[ENDED]";
                            
                            TextView sessionView = new TextView(LecturerMainActivity.this);
                            sessionView.setText(String.format("%s %s at %s\nDate: %s, Time: %s\n(GPS: %.4f, %.4f)", status, course, venue, date, time, lat, lon));
                            sessionView.setPadding(10, 20, 10, 20);
                            sessionView.setBackgroundResource(android.R.drawable.edit_text);
                            
                            if (isActive != null && isActive) {
                                sessionView.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                                sessionView.setOnClickListener(v -> {
                                    currentSessionId = sessionId;
                                    etCourseName.setText(course);
                                    etVenueName.setText(venue);
                                    tvSelectDate.setText(date);
                                    tvSelectTime.setText(time);
                                    etLecLat.setText(String.valueOf(lat));
                                    etLecLon.setText(String.valueOf(lon));
                                    setSessionUI(true);
                                    Toast.makeText(LecturerMainActivity.this, "Session selected. You can now end it.", Toast.LENGTH_SHORT).show();
                                });
                            } else {
                                sessionView.setTextColor(getResources().getColor(android.R.color.darker_gray));
                            }

                            sessionsContainer.addView(sessionView);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void showLecturerReports() {
        // Simple report view
        Toast.makeText(this, "Total sessions created: " + sessionsContainer.getChildCount(), Toast.LENGTH_SHORT).show();
    }

    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this, (view, year1, month1, dayOfMonth) -> {
            String date = dayOfMonth + "/" + (month1 + 1) + "/" + year1;
            tvSelectDate.setText(date);
        }, year, month, day);
        datePickerDialog.show();
    }

    private void showTimePicker() {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);

        TimePickerDialog timePickerDialog = new TimePickerDialog(this, (view, hourOfDay, minute1) -> {
            String time = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute1);
            tvSelectTime.setText(time);
        }, hour, minute, true);
        timePickerDialog.show();
    }

    private void checkActiveSession() {
        mSessionsDatabase.orderByChild("lecturerId").equalTo(mAuth.getUid())
                .limitToLast(1).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    for (DataSnapshot ds : snapshot.getChildren()) {
                        Boolean isActive = ds.child("active").getValue(Boolean.class);
                        if (isActive != null && isActive) {
                            currentSessionId = ds.getKey();
                            etCourseName.setText(ds.child("course").getValue(String.class));
                            etVenueName.setText(ds.child("venue").getValue(String.class));
                            tvSelectDate.setText(ds.child("date").getValue(String.class));
                            tvSelectTime.setText(ds.child("time").getValue(String.class));
                            etLecLat.setText(String.valueOf(ds.child("latitude").getValue(Double.class)));
                            etLecLon.setText(String.valueOf(ds.child("longitude").getValue(Double.class)));
                            setSessionUI(true);
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void setSessionUI(boolean hasActiveSession) {
        boolean typable = !hasActiveSession;
        etCourseName.setEnabled(typable);
        etVenueName.setEnabled(typable);
        etLecLat.setEnabled(typable);
        etLecLon.setEnabled(typable);
        btnAutoGps.setEnabled(typable);
        
        btnOpenSession.setVisibility(hasActiveSession ? android.view.View.GONE : android.view.View.VISIBLE);
        btnEndSession.setVisibility(hasActiveSession ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    private void endSession() {
        if (currentSessionId != null) {
            mSessionsDatabase.child(currentSessionId).child("active").setValue(false)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Session Ended", Toast.LENGTH_SHORT).show();
                        currentSessionId = null;
                        setSessionUI(false);
                        etCourseName.setText("");
                        etVenueName.setText("");
                        tvSelectDate.setText("Select Date");
                        tvSelectTime.setText("Select Time");
                        etLecLat.setText("");
                        etLecLon.setText("");
                    });
        }
    }

    private void openSession() {
        String course = etCourseName.getText().toString().trim();
        String venue = etVenueName.getText().toString().trim();
        String date = tvSelectDate.getText().toString().trim();
        String time = tvSelectTime.getText().toString().trim();
        String latStr = etLecLat.getText().toString().trim();
        String lonStr = etLecLon.getText().toString().trim();

        if (course.isEmpty() || venue.isEmpty() || latStr.isEmpty() || lonStr.isEmpty() || date.equals("Select Date") || time.equals("Select Time")) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        double lat = Double.parseDouble(latStr);
        double lon = Double.parseDouble(lonStr);

        String sessionId = mSessionsDatabase.push().getKey();
        if (sessionId != null) {
            java.util.Map<String, Object> sessionData = new java.util.HashMap<>();
            sessionData.put("course", course);
            sessionData.put("venue", venue);
            sessionData.put("date", date);
            sessionData.put("time", time);
            sessionData.put("latitude", lat);
            sessionData.put("longitude", lon);
            sessionData.put("lecturerId", mAuth.getUid());
            sessionData.put("active", true);
            sessionData.put("timestamp", com.google.firebase.database.ServerValue.TIMESTAMP);

            mSessionsDatabase.child(sessionId).setValue(sessionData)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Session Opened Successfully", Toast.LENGTH_SHORT).show();
                        currentSessionId = sessionId;
                        setSessionUI(true);
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Failed to open session", Toast.LENGTH_SHORT).show());
        }
    }

    private void getLastLocation(boolean updateEditTexts) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                double latitude = location.getLatitude();
                double longitude = location.getLongitude();
                String locStr = String.format(Locale.getDefault(), "Lat: %.4f, Lon: %.4f", latitude, longitude);
                tvLecCurrentLocation.setText(locStr);

                if (updateEditTexts) {
                    etLecLat.setText(String.valueOf(latitude));
                    etLecLon.setText(String.valueOf(longitude));
                    Toast.makeText(this, "GPS Captured", Toast.LENGTH_SHORT).show();
                }
            } else {
                tvLecCurrentLocation.setText("Unable to get location. Ensure GPS is ON.");
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getLastLocation(false);
        } else {
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
            tvLecCurrentLocation.setText("Permission denied");
        }
    }
}
