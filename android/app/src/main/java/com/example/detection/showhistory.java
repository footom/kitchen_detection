package com.example.detection;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.navigation.NavigationView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class showhistory extends AppCompatActivity {

    NavigationView navigationView;
    ImageView image;
    DrawerLayout drawerLayout;
    TextView time, time1, tvDeviceName;
    ImageView button, button1;
    Button btn;
    DatePickerDialog.OnDateSetListener datepick, datepick1;
    Calendar calendar = Calendar.getInstance();
    TableLayout tableLayout;
    private String URL = BuildConfig.API_BASE_URL + "/api/sensors/history";
    String format, date, date1;

    private List<String[]> records = new ArrayList<>();
    private final List<TableRow> addedRows = new ArrayList<>();
    private String currentDeviceName = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_showhistory);

        drawerLayout = findViewById(R.id.drawerlayout);
        image = findViewById(R.id.image);
        navigationView = findViewById(R.id.navigation);
        tableLayout = findViewById(R.id.tablelayout1);
        time = findViewById(R.id.text);
        time1 = findViewById(R.id.edit);
        button = findViewById(R.id.button);
        button1 = findViewById(R.id.button1);
        btn = findViewById(R.id.btn);

        if (navigationView != null) {
            navigationView.setItemIconTintList(null);

            if (navigationView.getHeaderCount() > 0) {
                View headerView = navigationView.getHeaderView(0);
                if (headerView != null) {
                    tvDeviceName = headerView.findViewById(R.id.tv_device_name);
                }
            }

            navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
                @Override
                public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                    int id = item.getItemId();
                    if (id == R.id.home) {
                        startActivity(new Intent(showhistory.this, MainActivity.class));
                        finish();
                        return true;
                    } else if (id == R.id.showbtn) {
                        startActivity(new Intent(showhistory.this, data.class));
                        finish();
                        return true;
                    } else if (id == R.id.history) {
                        startActivity(new Intent(showhistory.this, history.class));
                        finish();
                        return true;
                    } else if (id == R.id.showhistory) {
                        drawerLayout.closeDrawer(GravityCompat.START);
                        return true;
                    } else if (id == R.id.gasStandard) {
                        startActivity(new Intent(showhistory.this, gasStandard.class));
                        finish();
                        return true;
                    } else if (id == R.id.signout) {
                        startActivity(new Intent(showhistory.this, login.class));
                        finish();
                        return true;
                    }
                    return false;
                }
            });
        }

        image.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        datepick = (view, year, month, dayOfMonth) -> {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month);
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            format = "yyyy-MM-dd";
            SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.TAIWAN);
            time.setText(sdf.format(calendar.getTime()));
        };

        datepick1 = (view, year, month, dayOfMonth) -> {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month);
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            format = "yyyy-MM-dd";
            SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.TAIWAN);
            time1.setText(sdf.format(calendar.getTime()));
        };

        button.setOnClickListener(v -> {
            new DatePickerDialog(showhistory.this, datepick,
                    calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
            clearRows();
        });

        button1.setOnClickListener(v -> {
            new DatePickerDialog(showhistory.this, datepick1,
                    calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
            clearRows();
        });

        btn.setOnClickListener(v -> {
            date = time.getText().toString().trim();
            date1 = time1.getText().toString().trim();
            clearRows();
            fetchHistory();
        });

        // 頁面初次載入時預設抓取一次歷史數據
        fetchHistory();
    }

    private void clearRows() {
        for (TableRow row : addedRows) {
            tableLayout.removeView(row);
        }
        addedRows.clear();
    }

    private void fetchHistory() {
        StringBuilder urlBuilder = new StringBuilder(URL);
        boolean hasParam = false;
        if (date != null && !date.isEmpty()) {
            urlBuilder.append(hasParam ? '&' : '?').append("start=").append(date);
            hasParam = true;
        }
        if (date1 != null && !date1.isEmpty()) {
            urlBuilder.append(hasParam ? '&' : '?').append("end=").append(date1);
            hasParam = true;
        }

        StringRequest stringRequest = new StringRequest(Request.Method.GET, urlBuilder.toString(),
                response -> parseAndShow(response),
                error -> Toast.makeText(showhistory.this, "API 錯誤: " + error.getMessage(), Toast.LENGTH_SHORT).show());
        RequestQueue requestQueue = Volley.newRequestQueue(getApplicationContext());
        requestQueue.add(stringRequest);
    }

    private void parseAndShow(String response) {
        List<String[]> parsed = new ArrayList<>();
        try {
            JSONObject rootObj = new JSONObject(response);
            if ("success".equals(rootObj.optString("status"))) {
                JSONArray dataJson = rootObj.getJSONArray("data");
                int l = dataJson.length();
                for (int i = 0; i < l; i++) {
                    JSONObject json = dataJson.getJSONObject(i);

                    if (currentDeviceName == null && json.has("device_id") && !json.isNull("device_id")) {
                        String devId = json.getString("device_id").trim();
                        if (!devId.isEmpty()) {
                            currentDeviceName = devId;
                        }
                    }

                    String updatetime = json.optString("created_at", "");
                    String temperature = json.optString("temperature", "0");
                    String humidity = json.optString("humidity", "0");
                    String lpg = json.optString("lpg", "0");
                    String co = json.optString("co", "0");
                    String fire = json.optString("fire", "0");

                    parsed.add(new String[]{updatetime, temperature, humidity, lpg, co, fire});
                }
            } else {
                Toast.makeText(showhistory.this, "查無資料", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(showhistory.this, "資料解析錯誤", Toast.LENGTH_SHORT).show();
            return;
        }

        records = parsed;

        if (tvDeviceName != null) {
            if (currentDeviceName != null && !currentDeviceName.isEmpty()) {
                tvDeviceName.setText(currentDeviceName);
            } else {
                tvDeviceName.setText("ESP8266_Kitchen");
            }
        }

        show();
    }

    private void show() {
        clearRows();
        if (records == null) return;

        for (String[] rowData : records) {
            TableRow row = new TableRow(showhistory.this);

            for (String cell : rowData) {
                TextView cellView = new TextView(showhistory.this);
                cellView.setText(cell);
                cellView.setGravity(Gravity.CENTER);
                cellView.setTextSize(18);
                row.addView(cellView);
            }

            tableLayout.addView(row);
            addedRows.add(row);
        }
        tableLayout.invalidate();
        tableLayout.refreshDrawableState();
    }
}