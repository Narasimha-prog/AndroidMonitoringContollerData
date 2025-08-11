package com.rpd.rgms_unit_gui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 👇👇👇 THIS IS THE BUTTON-SPECIFIC CODE 👇👇👇
        // 1. Get a reference to the button using its ID from activity_main.xml
        Button goDashboardButton = findViewById(R.id.btnGoDashboard);

        // 2. Set an OnClickListener on the button to define its behavior when tapped
        if (goDashboardButton != null) {
            goDashboardButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // 3. Create an Intent to specify the target activity (DashboardActivity)
                    Intent intent = new Intent(MainActivity.this, DashboardActivity.class);
                    // 4. Start the DashboardActivity
                    startActivity(intent);
                }
            });
        }
        // 👆👆👆 END OF BUTTON-SPECIFIC CODE 👆👆👆
    }
}