package com.example.detection;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.google.android.material.navigation.NavigationView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class history extends AppCompatActivity {

    NavigationView navigationView;
    ImageView image;
    DrawerLayout drawerLayout;
    TableLayout tableLayout;
    SwipeRefreshLayout swipe;
    TextView tvDeviceName;

    private List<String[]> records = new ArrayList<>();
    private final List<TableRow> addedRows = new ArrayList<>();
    private String currentDeviceName = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);
        drawerLayout = findViewById(R.id.drawerlayout);
        image = findViewById(R.id.image);
        navigationView = findViewById(R.id.navigation);
        swipe = findViewById(R.id.swipe);
        tableLayout = findViewById(R.id.tablelayout);

        if (navigationView != null) {
            navigationView.setItemIconTintList(null);
            if (navigationView.getHeaderCount() > 0) {
                View headerView = navigationView.getHeaderView(0);
                if (headerView != null) {
                    tvDeviceName = headerView.findViewById(R.id.tv_device_name);
                }
            }

            // 選單監聽器只在onCreate設定一次
            navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
                @Override
                public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                    int id = item.getItemId();
                    if (id == R.id.home) {
                        startActivity(new Intent(history.this, MainActivity.class));
                        finish();
                        return true;
                    } else if (id == R.id.showbtn) {
                        startActivity(new Intent(history.this, data.class));
                        finish();
                        return true;
                    } else if (id == R.id.history) {
                        drawerLayout.closeDrawer(GravityCompat.START);
                        return true;
                    } else if (id == R.id.showhistory) {
                        startActivity(new Intent(history.this, showhistory.class));
                        finish();
                        return true;
                    } else if (id == R.id.gasStandard) {
                        startActivity(new Intent(history.this, gasStandard.class));
                        finish();
                        return true;
                    } else if (id == R.id.signout) {
                        startActivity(new Intent(history.this, login.class));
                        finish();
                        return true;
                    }
                    return false;
                }
            });
        }

        // 點擊圖片僅開啟Drawer，不重複綁定監聽器
        image.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        Thread thread = new Thread(mutiThread);
        thread.start();

        swipe.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                swipe.setRefreshing(true);
                clearRows();

                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Thread thread = new Thread(mutiThread);
                        thread.start();
                        swipe.setRefreshing(false);
                    }
                }, 1000);
            }
        });
    }

    private void clearRows() {
        for (TableRow row : addedRows) {
            tableLayout.removeView(row);
        }
        addedRows.clear();
    }

    private Runnable mutiThread = new Runnable() {
        @Override
        public void run() {
            List<String[]> fetched = new ArrayList<>();
            final String[] deviceNameHolder = new String[1];

            try {
                URL url = new URL(BuildConfig.API_BASE_URL + "/api/sensors/history");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setDoInput(true);
                connection.setUseCaches(false);
                connection.connect();

                int responseCode = connection.getResponseCode();

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    InputStream inputStream = connection.getInputStream();
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, "utf-8"), 8);
                    StringBuilder box = new StringBuilder();
                    String line;

                    while ((line = bufferedReader.readLine()) != null) {
                        box.append(line).append("\n");
                    }
                    inputStream.close();

                    JSONObject rootObj = new JSONObject(box.toString());
                    if ("success".equals(rootObj.optString("status"))) {
                        JSONArray dataJson = rootObj.getJSONArray("data");
                        int l = dataJson.length();
                        for (int i = l - 1; i >= 0; i--) {
                            JSONObject json = dataJson.getJSONObject(i);

                            if (deviceNameHolder[0] == null && json.has("device_id") && !json.isNull("device_id")) {
                                String devId = json.getString("device_id").trim();
                                if (!devId.isEmpty()) {
                                    deviceNameHolder[0] = devId;
                                }
                            }

                            String updatetime = json.optString("created_at", "");
                            String lpg = json.optString("lpg", "0");
                            String co = json.optString("co", "0");
                            String fire = json.optString("fire", "0");

                            fetched.add(new String[]{updatetime, lpg, co, fire});
                        }
                    }
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }

            runOnUiThread(() -> {
                records = fetched;

                // 更新側邊欄裝置名稱
                if (deviceNameHolder[0] != null && !deviceNameHolder[0].isEmpty()) {
                    currentDeviceName = deviceNameHolder[0];
                }

                if (tvDeviceName != null) {
                    if (currentDeviceName != null && !currentDeviceName.isEmpty()) {
                        tvDeviceName.setText(currentDeviceName);
                    } else {
                        tvDeviceName.setText("ESP8266_Kitchen");
                    }
                }

                show();
            });
        }
    };

    private void show() {
        clearRows();
        if (records == null) return;

        for (String[] rowData : records) {
            TableRow row = new TableRow(history.this);

            for (String cell : rowData) {
                TextView cellView = new TextView(history.this);
                cellView.setText(cell);
                cellView.setGravity(Gravity.CENTER);
                cellView.setTextSize(16);
                row.addView(cellView);
            }

            tableLayout.addView(row);
            addedRows.add(row);
        }
        tableLayout.invalidate();
        tableLayout.refreshDrawableState();
    }
}