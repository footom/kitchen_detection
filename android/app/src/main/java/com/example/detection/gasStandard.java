package com.example.detection;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.navigation.NavigationView;

public class gasStandard extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ImageView image;
    private TextView text, text1, tvDeviceName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gas_standard);

        drawerLayout = findViewById(R.id.drawerlayout);
        image = findViewById(R.id.image);
        text = findViewById(R.id.text);
        text1 = findViewById(R.id.text1);
        navigationView = findViewById(R.id.navigation);

        if (navigationView != null) {
            navigationView.setItemIconTintList(null);

            if (navigationView.getHeaderCount() > 0) {
                View headerView = navigationView.getHeaderView(0);
                if (headerView != null) {
                    tvDeviceName = headerView.findViewById(R.id.tv_device_name);
                }
            }

            if (tvDeviceName != null) {
                tvDeviceName.setText("ESP8266_Kitchen");
            }

            navigationView.setNavigationItemSelectedListener(this);
        }

        image.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                drawerLayout.openDrawer(GravityCompat.START);
            }
        });

        text.setText("瓦斯警報器規定:濃度未達爆炸下限1/100，不得警報以防止誤報；濃度介於爆炸下限1/100 ~ 1/4範圍內則須警報。丙烷警報標準:210ppm~5250ppm；丁烷:190ppm~4750ppm");
        text1.setText("一氧化碳警報器標準:CO 70ppm--在60至240分鐘之間警報；CO 150 PPM--在10至50分鐘之間警報；CO 400ppm--在4至15分鐘之間警報。");
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.gasStandard) {
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        }

        Intent intent = null;
        if (id == R.id.home) {
            intent = new Intent(this, MainActivity.class);
        } else if (id == R.id.showbtn) {
            intent = new Intent(this, data.class);
        } else if (id == R.id.history) {
            intent = new Intent(this, history.class);
        } else if (id == R.id.showhistory) {
            intent = new Intent(this, showhistory.class);
        } else if (id == R.id.signout) {
            intent = new Intent(this, login.class);
        }

        if (intent != null) {
            startActivity(intent);
            finish();
        }

        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }
}