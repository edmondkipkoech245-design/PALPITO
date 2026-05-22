package com.example.palpito1;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class GpsVenueActivity extends AppCompatActivity {

    private TextView textLatitude, textLongitude;
    private TextInputEditText venueNameEdit, venueCodeEdit, radiusEdit;
    private FusedLocationProviderClient fusedLocationClient;
    private DatabaseReference gpsRef;
    private double latitude = 0.0;
    private double longitude = 0.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gps_venue);

        // Initialize Firebase
        gpsRef = FirebaseDatabase.getInstance().getReference("GPSVenues");

        // Initialize Location Client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Link UI
        textLatitude = findViewById(R.id.textLatitude);
        textLongitude = findViewById(R.id.textLongitude);
        venueNameEdit = findViewById(R.id.venueNameEdit);
        venueCodeEdit = findViewById(R.id.venueCodeEdit);
        radiusEdit = findViewById(R.id.radiusEdit);
        Button btnAddGpsVenue = findViewById(R.id.btnAddGpsVenue);
        Button btnBackToDashboard = findViewById(R.id.btnBackToDashboard);

        btnBackToDashboard.setOnClickListener(v -> finish());

        // Get Current Location
        getLastLocation();

        btnAddGpsVenue.setOnClickListener(v -> saveVenue());
    }

    private void getLastLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                latitude = location.getLatitude();
                longitude = location.getLongitude();
                textLatitude.setText(String.format(Locale.getDefault(), "Lat: %.4f", latitude));
                textLongitude.setText(String.format(Locale.getDefault(), "Lon: %.4f", longitude));
            } else {
                Toast.makeText(this, "Unable to get current location. Ensure GPS is ON.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveVenue() {
        if (venueNameEdit.getText() == null || venueCodeEdit.getText() == null || radiusEdit.getText() == null) return;

        String name = venueNameEdit.getText().toString().trim();
        String code = venueCodeEdit.getText().toString().trim();
        String radiusStr = radiusEdit.getText().toString().trim();

        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(code) || TextUtils.isEmpty(radiusStr)) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (latitude == 0.0 && longitude == 0.0) {
            Toast.makeText(this, "Location not captured yet", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> venue = new HashMap<>();
        venue.put("venueName", name);
        venue.put("venueCode", code);
        venue.put("radius", radiusStr);
        venue.put("latitude", latitude);
        venue.put("longitude", longitude);

        gpsRef.child(code).setValue(venue).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(GpsVenueActivity.this, "Venue added successfully", Toast.LENGTH_SHORT).show();
                venueNameEdit.setText("");
                venueCodeEdit.setText("");
                radiusEdit.setText("");
            } else {
                String error = task.getException() != null ? task.getException().getMessage() : "Error";
                Toast.makeText(GpsVenueActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getLastLocation();
        } else {
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
        }
    }
}
