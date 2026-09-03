package com.example.detection;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class login extends AppCompatActivity {

    private EditText phone;
    private TextView time;

    private final String LOGIN_URL = BuildConfig.API_BASE_URL + "/api/auth/login";
    private final String REGISTER_URL = BuildConfig.API_BASE_URL + "/api/auth/register";

    private RequestQueue requestQueue;

    // 時鐘Handler與Runnable設定
    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat clockFormatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            time.setText("現在時間:" + clockFormatter.format(new Date()));
            clockHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        phone = findViewById(R.id.phone);
        time = findViewById(R.id.time);

        // 初始化Volley請求佇列
        requestQueue = Volley.newRequestQueue(getApplicationContext());

        clockHandler.post(clockTick);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        clockHandler.removeCallbacks(clockTick); // 移除計時監聽，防止Memory Leak
    }

    public void Login(View view) {
        final String inputPhone = phone.getText().toString().trim();

        if (inputPhone.isEmpty()) {
            Toast.makeText(this, "欄位不可為空", Toast.LENGTH_SHORT).show();
            return;
        }

        sendAuthRequest(LOGIN_URL, inputPhone, false);
    }

    public void Register(View view) {
        final String inputPhone = phone.getText().toString().trim();

        if (inputPhone.isEmpty()) {
            Toast.makeText(this, "欄位不可為空", Toast.LENGTH_SHORT).show();
            return;
        }

        sendAuthRequest(REGISTER_URL, inputPhone, true);
    }

    // 統一處理登入與註冊請求
    private void sendAuthRequest(String url, final String inputPhone, final boolean isRegister) {
        StringRequest stringRequest = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        if (isRegister) {
                            Toast.makeText(login.this, "註冊成功！請點擊登入", Toast.LENGTH_LONG).show();
                        } else {
                            if ("success".equalsIgnoreCase(response.trim())) {
                                Toast.makeText(login.this, "登入成功", Toast.LENGTH_SHORT).show();
                                Intent intent = new Intent(login.this, MainActivity.class);
                                startActivity(intent);
                                finish();
                            } else {
                                Toast.makeText(login.this, "無法進入", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        // 解析後端傳回的400, 401, 403等錯誤訊息
                        NetworkResponse response = error.networkResponse;
                        if (response != null && response.data != null) {
                            try {
                                String resString = new String(response.data, StandardCharsets.UTF_8);
                                JSONObject jsonObject = new JSONObject(resString);
                                String msg = jsonObject.optString("message", "驗證失敗");
                                Toast.makeText(login.this, msg, Toast.LENGTH_LONG).show();
                            } catch (Exception e) {
                                Toast.makeText(login.this, "驗證失敗", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(login.this, "網路連線錯誤", Toast.LENGTH_SHORT).show();
                        }
                    }
                }) {
            @Override
            protected Map<String, String> getParams() throws AuthFailureError {
                Map<String, String> data = new HashMap<>();
                data.put("phone_number", inputPhone); // 發送至後端phone_number欄位
                data.put("phone", inputPhone);
                return data;
            }
        };

        requestQueue.add(stringRequest);
    }
}