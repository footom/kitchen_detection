package com.example.detection;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class data extends AppCompatActivity {
    public static final String tag = "TAG";
    LineChart linechart;
    TextView tvDeviceName;
    LineDataSet line1, line2;
    List<Entry> arrayList;
    List<Entry> arrayList1;
    List<String> arrayList3;
    private IAxisValueFormatter xAxisFormatter;
    private MakerView makerView;

    NavigationView navigationView;
    ImageView image;
    DrawerLayout drawerLayout;

    private static final long REFRESH_INTERVAL_MS = 5000;
    private final ExecutorService fetchExecutor = Executors.newSingleThreadExecutor();
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshTick = new Runnable() {
        @Override
        public void run() {
            fetchExecutor.execute(fetchTask);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data);

        linechart = findViewById(R.id.linechart);
        drawerLayout = findViewById(R.id.drawerlayout);
        image = findViewById(R.id.image);
        navigationView = findViewById(R.id.navigation);

        if (navigationView != null) {
            navigationView.setItemIconTintList(null);

            // 清理舊Header後動態建立唯一Header
            while (navigationView.getHeaderCount() > 0) {
                navigationView.removeHeaderView(navigationView.getHeaderView(0));
            }
            View headerView = navigationView.inflateHeaderView(R.layout.navigation_header);
            tvDeviceName = headerView.findViewById(R.id.tv_device_name);

            navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
                @Override
                public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                    int id = item.getItemId();
                    if (id == R.id.home) {
                        Intent intent = new Intent(data.this, MainActivity.class);
                        startActivity(intent);
                        finish();
                        return true;
                    } else if (id == R.id.showbtn) {
                        drawerLayout.closeDrawer(GravityCompat.START);
                        return true;
                    } else if (id == R.id.history) {
                        Intent intent = new Intent(data.this, history.class);
                        startActivity(intent);
                        finish();
                        return true;
                    } else if (id == R.id.showhistory) {
                        Intent intent = new Intent(data.this, showhistory.class);
                        startActivity(intent);
                        finish();
                        return true;
                    } else if (id == R.id.gasStandard) {
                        Intent intent = new Intent(data.this, gasStandard.class);
                        startActivity(intent);
                        finish();
                        return true;
                    } else if (id == R.id.signout) {
                        Intent intent = new Intent(data.this, login.class);
                        startActivity(intent);
                        finish();
                        return true;
                    }
                    return false;
                }
            });
        }

        makerView = new MakerView(this, null);
        makerView.setChartView(linechart);
        linechart.setMarker(makerView);

        image.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        refreshHandler.post(refreshTick);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        refreshHandler.removeCallbacks(refreshTick);
        fetchExecutor.shutdownNow();
    }

    private final Runnable fetchTask = new Runnable() {
        @Override
        public void run() {
            List<Entry> newLpgEntries = new ArrayList<>();
            List<Entry> newCoEntries = new ArrayList<>();
            List<String> newTimeLabels = new ArrayList<>();

            final String[] deviceNameHolder = new String[1];

            try {
                URL url = new URL(BuildConfig.API_BASE_URL + "/api/sensors/history?limit=50");
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
                        box.append(line);
                    }
                    bufferedReader.close();
                    inputStream.close();

                    JSONObject response = new JSONObject(box.toString());
                    JSONArray dataJson = response.getJSONArray("data");
                    for (int i = 0; i < dataJson.length(); i++) {
                        JSONObject json = dataJson.getJSONObject(i);
                        if (deviceNameHolder[0] == null && json.has("device_id") && !json.isNull("device_id")) {
                            String devId = json.getString("device_id").trim();
                            if (!devId.isEmpty()) {
                                deviceNameHolder[0] = devId;
                            }
                        }
                        String lpg = json.getString("lpg");
                        String co = json.getString("co");
                        String updatetime = json.getString("created_at");
                        newLpgEntries.add(new Entry(i, (float) Double.parseDouble(lpg)));
                        newCoEntries.add(new Entry(i, (float) Double.parseDouble(co)));
                        newTimeLabels.add(updatetime);
                    }
                }
            } catch (IOException | JSONException e) {
                android.util.Log.e("DEVICE_CHECK", "HTTP或JSON解析失敗: " + e.getMessage(), e);
            }

            runOnUiThread(() -> {
                arrayList = newLpgEntries;
                arrayList1 = newCoEntries;
                arrayList3 = newTimeLabels;

                if (tvDeviceName != null) {
                    if (deviceNameHolder[0] != null && !deviceNameHolder[0].isEmpty()) {
                        tvDeviceName.setText(deviceNameHolder[0]);
                    } else {
                        tvDeviceName.setText("ESP8266_Kitchen");
                    }
                }

                showlinechart();
            });

            refreshHandler.postDelayed(refreshTick, REFRESH_INTERVAL_MS);
        }
    };

    private void showlinechart() {
        line1 = new LineDataSet(arrayList, "lpg");
        line2 = new LineDataSet(arrayList1, "co");

        XAxis xAxis = linechart.getXAxis();
        xAxis.setDrawAxisLine(true);
        xAxis.setDrawGridLines(true);
        xAxis.setDrawLabels(true);
        xAxis.setTextSize(10f);
        xAxis.setAxisMinimum(0f);
        xAxis.enableAxisLineDashedLine(10f, 10f, 0f);

        xAxisFormatter = new IndexAxisValueFormatter(arrayList3);
        xAxis.setValueFormatter((ValueFormatter) xAxisFormatter);
        xAxis.setLabelCount(5, true);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);

        YAxis rightAxis = linechart.getAxisRight();
        rightAxis.setEnabled(false);
        YAxis axisLeft = linechart.getAxisLeft();
        axisLeft.setTextSize(12f);
        axisLeft.setDrawTopYLabelEntry(false);
        axisLeft.setAxisMinimum(0f);
        axisLeft.setLabelCount(8, false);
        axisLeft.setGridLineWidth(1f);
        axisLeft.setAxisLineWidth(1f);
        axisLeft.setGranularity(0.1f);
        axisLeft.setDrawZeroLine(false);

        axisLeft.removeAllLimitLines();
        LimitLine l1 = new LimitLine(200f, "警示線");
        l1.setLineColor(Color.RED);
        l1.setLineWidth(4f);
        l1.setTextColor(Color.RED);
        l1.setTextSize(12f);
        axisLeft.addLimitLine(l1);

        line1.setValueTextSize(12f);
        line1.setCircleColor(Color.parseColor("#DC143C"));
        line1.setDrawValues(false);
        line1.setLineWidth(3f);
        line1.setCircleRadius(4);
        line1.setHighlightLineWidth(2);
        line1.setColor(Color.parseColor("#DC143C"));
        line1.setCircleSize(4f);

        line2.setValueTextSize(12f);
        line2.setCircleColor(Color.parseColor("#00FFFF"));
        line2.setDrawValues(false);
        line2.setLineWidth(3f);
        line2.setCircleRadius(4);
        line2.setHighlightLineWidth(2);
        line2.setColor(Color.parseColor("#00FFFF"));
        line2.setCircleSize(4f);

        Legend legend = linechart.getLegend();
        legend.setXEntrySpace(20f);
        legend.setForm(Legend.LegendForm.CIRCLE);
        legend.setTextSize(20f);

        Description description = new Description();
        description.setText("單位:ppm");
        description.setTextSize(14);
        description.setEnabled(true);
        description.setPosition(180, 35);

        linechart.setNoDataText("沒有數據");
        linechart.setNoDataTextColor(Color.BLACK);

        linechart.setDrawGridBackground(true);
        linechart.setDrawBorders(true);
        linechart.setTouchEnabled(true);
        linechart.setHighlightPerDragEnabled(true);
        linechart.getViewPortHandler().setMinMaxScaleX(5.0f, 1.5f);
        linechart.setScaleXEnabled(true);
        linechart.setScaleYEnabled(true);

        linechart.setData(new LineData(line1, line2));
        linechart.setDescription(description);
        linechart.notifyDataSetChanged();
        linechart.invalidate();
    }
}