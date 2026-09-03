package com.example.detection;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.FirebaseApp;

import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String NODEJS_API_URL = BuildConfig.API_BASE_URL + "/api/sensors/latest";

    private NotificationManager manager;
    private NotificationCompat.Builder builder;
    public static final String ChannelID = "myChannel";
    public static final String ChannelName = "eles";

    private TextView temp_hum, fire, LPG, CO, time, current, tvDeviceName;
    private NavigationView navigationView;
    private ImageView image;
    private DrawerLayout drawerLayout;

    private final OkHttpClient client = new OkHttpClient();

    private static final long AUTO_REFRESH_INTERVAL_MS = 10000;
    private final Handler autoRefreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoRefreshTick = new Runnable() {
        @Override
        public void run() {
            updateCurrentTime();
            fetchDataFromNodeJS();
            autoRefreshHandler.postDelayed(this, AUTO_REFRESH_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        createNotificationChannel();
        initFirebaseMessaging();

        temp_hum = findViewById(R.id.temp_hum);
        fire = findViewById(R.id.fire);
        LPG = findViewById(R.id.lpg);
        CO = findViewById(R.id.co);
        time = findViewById(R.id.time);
        drawerLayout = findViewById(R.id.drawerlayout);
        image = findViewById(R.id.image);
        current = findViewById(R.id.current);
        navigationView = findViewById(R.id.navigation);

        if (navigationView != null) {
            navigationView.setItemIconTintList(null);

            if (navigationView.getHeaderCount() > 0) {
                View headerView = navigationView.getHeaderView(0);
                if (headerView != null) {
                    tvDeviceName = headerView.findViewById(R.id.tv_device_name);
                }
            }

            // 選單監聽器只需在onCreate設定一次
            navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
                @Override
                public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                    int id = item.getItemId();
                    if (id == R.id.home) {
                        drawerLayout.closeDrawer(GravityCompat.START);
                        return true;
                    } else if (id == R.id.showbtn) {
                        startActivity(new Intent(MainActivity.this, data.class));
                        finish();
                        return true;
                    } else if (id == R.id.history) {
                        startActivity(new Intent(MainActivity.this, history.class));
                        finish();
                        return true;
                    } else if (id == R.id.showhistory) {
                        startActivity(new Intent(MainActivity.this, showhistory.class));
                        finish();
                        return true;
                    } else if (id == R.id.gasStandard) {
                        startActivity(new Intent(MainActivity.this, gasStandard.class));
                        finish();
                        return true;
                    } else if (id == R.id.signout) {
                        startActivity(new Intent(MainActivity.this, login.class));
                        finish();
                        return true;
                    }
                    return false;
                }
            });
        }

        image.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        updateCurrentTime();
    }

    @Override
    protected void onResume() {
        super.onResume();
        autoRefreshHandler.post(autoRefreshTick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        autoRefreshHandler.removeCallbacks(autoRefreshTick);
    }

    private void initFirebaseMessaging() {
        if (FirebaseApp.initializeApp(this) == null) {
            Log.w(TAG, "Firebase is not configured; push notifications are disabled.");
            return;
        }
        FirebaseMessaging.getInstance().subscribeToTopic("sensor_alerts")
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "已成功訂閱FCM推播 (sensor_alerts)");
                        } else {
                            Log.e(TAG, "FCM訂閱失敗", task.getException());
                        }
                    }
                });
    }

    private void fetchDataFromNodeJS() {
        Request request = new Request.Builder()
                .url(NODEJS_API_URL)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "無法連接Node.js API: " + e.getMessage());
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this, "無法連線至伺服器", Toast.LENGTH_SHORT).show()
                );
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String jsonStr = response.body().string();
                    try {
                        JSONObject rootObj = new JSONObject(jsonStr);
                        if ("success".equals(rootObj.optString("status"))) {
                            JSONObject data = rootObj.getJSONObject("data");

                            final String deviceId = data.optString("device_id", "ESP8266_Kitchen"); // 👈 抓取 device_id
                            final String temperature = data.optString("temperature", "0");
                            final String humidity = data.optString("humidity", "0");
                            final int fireVal = data.optInt("fire", 0);
                            final double lpgVal = data.optDouble("lpg", 0.0);
                            final double coVal = data.optDouble("co", 0.0);
                            final String updateTimeStr = data.optString("created_at", "--");

                            runOnUiThread(() -> {
                                if (tvDeviceName != null) {
                                    tvDeviceName.setText(deviceId);
                                }

                                temp_hum.setText(temperature + "°C /" + humidity + "%");
                                fire.setText(fireVal == 1 ? "開" : "關");
                                LPG.setText("         " + lpgVal + " ppm");
                                CO.setText("         " + coVal + " ppm");
                                time.setText("上次更新:" + updateTimeStr);

                                checkLocalAlert(lpgVal, coVal);
                            });
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "JSON解析錯誤", e);
                    }
                }
            }
        });
    }

    private void updateCurrentTime() {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.TAIWAN);
        // 強制將格式化器指定為台灣時區
        formatter.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Taipei"));
        String str = formatter.format(new Date());
        current.setText("現在時間:" + str);
    }

    private void checkLocalAlert(double lpgVal, double coVal) {
        if (lpgVal > 180) {
            triggerNotification("LPG濃度過高警報！");
        }
        if (coVal > 150) {
            triggerNotification("CO濃度過高警報！");
        }
    }

    private void triggerNotification(String msg) {
        builder = getNotificationChannelBuilder(msg);
        manager.notify((int) System.currentTimeMillis(), builder.build());
        Ring(this);
    }

    public void createNotificationChannel() {
        manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    ChannelID, ChannelName, NotificationManager.IMPORTANCE_HIGH);
            manager.createNotificationChannel(channel);
        }
    }

    public NotificationCompat.Builder getNotificationChannelBuilder(String msg) {
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, ChannelID)
                .setContentTitle("氣體超標警報")
                .setContentText(msg)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);
    }

    private static void Ring(Context context) {
        try {
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone rt = RingtoneManager.getRingtone(context, uri);
            rt.play();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}