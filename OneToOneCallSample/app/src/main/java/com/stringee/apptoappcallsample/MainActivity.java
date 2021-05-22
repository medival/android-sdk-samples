package com.stringee.apptoappcallsample;

import android.app.KeyguardManager;
import android.app.KeyguardManager.KeyguardLock;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager.LayoutParams;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.stringee.StringeeClient;
import com.stringee.apptoappcallsample.common.Common;
import com.stringee.apptoappcallsample.common.Utils;
import com.stringee.call.StringeeCall;
import com.stringee.call.StringeeCall2;
import com.stringee.exception.StringeeError;
import com.stringee.listener.StatusListener;
import com.stringee.listener.StringeeConnectionListener;

import org.json.JSONObject;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, LifecycleObserver {

    public static StringeeClient client;
    private String to;
    //put your token here
    private String token = "eyJjdHkiOiJzdHJpbmdlZS1hcGk7dj0xIiwidHlwIjoiSldUIiwiYWxnIjoiSFMyNTYifQ.eyJqdGkiOiJTS0UxUmRVdFVhWXhOYVFRNFdyMTVxRjF6VUp1UWRBYVZULTE2MjE2Nzc2OTAiLCJpc3MiOiJTS0UxUmRVdFVhWXhOYVFRNFdyMTVxRjF6VUp1UWRBYVZUIiwiZXhwIjoxNjI0MjY5NjkwLCJ1c2VySWQiOiJ1c2VyMiJ9.TCCBOgi7Uctk8xNGSqyG8cUuz1P0OJjahY6JWfUoAR0";

    private EditText etTo;
    private TextView tvUserId;
    private ProgressDialog progressDialog;

    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor editor;

    private KeyguardLock lock;

    private final String PREF_NAME = "com.stringee.onetoonecallsample";
    private final String IS_TOKEN_REGISTERED = "is_token_registered";
    private final String TOKEN = "token";

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    public void onAppBackgrounded() {
        Log.d("AppLifecycle", "App in background");
        Common.isAppInBackground = true;
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    public void onAppForegrounded() {
        Log.d("AppLifecycle", "App in foreground");
        Common.isAppInBackground = false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //add Flag for show on lockScreen and disable keyguard
        getWindow().addFlags(LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | LayoutParams.FLAG_DISMISS_KEYGUARD
                | LayoutParams.FLAG_KEEP_SCREEN_ON
                | LayoutParams.FLAG_TURN_SCREEN_ON);

        setContentView(R.layout.activity_main);

        lock = ((KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE)).newKeyguardLock(Context.KEYGUARD_SERVICE);
        lock.disableKeyguard();

        if (VERSION.SDK_INT >= VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }

        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);

        tvUserId = (TextView) findViewById(R.id.tv_userid);

        Button btnVoiceCall = (Button) findViewById(R.id.btn_voice_call);
        btnVoiceCall.setOnClickListener(this);
        Button btnVideoCall = (Button) findViewById(R.id.btn_video_call);
        btnVideoCall.setOnClickListener(this);
        Button btnVoiceCall2 = (Button) findViewById(R.id.btn_voice_call2);
        btnVoiceCall2.setOnClickListener(this);
        Button btnVideoCall2 = (Button) findViewById(R.id.btn_video_call2);
        btnVideoCall2.setOnClickListener(this);
        etTo = (EditText) findViewById(R.id.et_to);

        Button btnUnregister = (Button) findViewById(R.id.btn_unregister);
        btnUnregister.setOnClickListener(this);

        progressDialog = ProgressDialog.show(this, "", "Connecting...");
        progressDialog.setCancelable(true);
        progressDialog.show();

        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        editor = sharedPreferences.edit();

        NotificationManager nm = (NotificationManager) getSystemService
                (NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel(44448888);
        }

        initAndConnectStringee();
    }

    public void initAndConnectStringee() {
        client = new StringeeClient(this);
        client.setConnectionListener(new StringeeConnectionListener() {
            @Override
            public void onConnectionConnected(final StringeeClient stringeeClient, boolean isReconnecting) {
                boolean isTokenRegistered = sharedPreferences.getBoolean(IS_TOKEN_REGISTERED, false);
                if (!isTokenRegistered) {
                    com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken().addOnCompleteListener(new OnCompleteListener<String>() {
                        @Override
                        public void onComplete(@NonNull Task<String> task) {
                            if (!task.isSuccessful()) {
                                Log.d("Stringee", "getInstanceId failed", task.getException());
                                return;
                            }
                            //register push notification
                            String token = task.getResult();
                            client.registerPushToken(token, new StatusListener() {
                                @Override
                                public void onSuccess() {
                                    Log.d("Stringee", "Register push token successfully.");
                                    editor.putBoolean(IS_TOKEN_REGISTERED, true);
                                    editor.putString(TOKEN, token);
                                    editor.commit();
                                }

                                @Override
                                public void onError(StringeeError error) {
                                    Log.d("Stringee", "Register push token unsuccessfully: " + error.getMessage());
                                }
                            });
                        }
                    });

                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.dismiss();
                        tvUserId.setText("Connected as: " + stringeeClient.getUserId());
                        Utils.reportMessage(MainActivity.this, "StringeeClient is connected.");
                    }
                });
            }

            @Override
            public void onConnectionDisconnected(StringeeClient stringeeClient, boolean isReconnecting) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.dismiss();
                        Utils.reportMessage(MainActivity.this, "StringeeClient disconnected.");
                    }
                });
            }

            @Override
            public void onIncomingCall(final StringeeCall stringeeCall) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (Common.isInCall) {
                            stringeeCall.hangup();
                        } else {
                            Common.callsMap.put(stringeeCall.getCallId(), stringeeCall);
                            Intent intent = new Intent(MainActivity.this, IncomingCallActivity.class);
                            intent.putExtra("call_id", stringeeCall.getCallId());
                            startActivity(intent);
                        }
                    }
                });
            }

            @Override
            public void onIncomingCall2(StringeeCall2 stringeeCall2) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (Common.isInCall) {
                            stringeeCall2.hangup();
                        } else {
                            Common.calls2Map.put(stringeeCall2.getCallId(), stringeeCall2);
                            Intent intent = new Intent(MainActivity.this, IncomingCall2Activity.class);
                            intent.putExtra("call_id", stringeeCall2.getCallId());
                            startActivity(intent);
                        }
                    }
                });
            }

            @Override
            public void onConnectionError(StringeeClient stringeeClient, final StringeeError stringeeError) {
                Log.d("Stringee", "StringeeClient fails to connect: " + stringeeError.getMessage());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.dismiss();
                        Utils.reportMessage(MainActivity.this, "StringeeClient fails to connect: " + stringeeError.getMessage());
                    }
                });
            }

            @Override
            public void onRequestNewToken(StringeeClient stringeeClient) {
                // Get new token here and connect to Stringe server
            }

            @Override
            public void onCustomMessage(String s, JSONObject jsonObject) {

            }

            @Override
            public void onTopicMessage(String s, JSONObject jsonObject) {

            }
        });
        client.connect(token);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_voice_call:
                to = etTo.getText().toString();
                if (to.trim().length() > 0) {
                    if (client.isConnected()) {
                        Intent intent = new Intent(this, OutgoingCallActivity.class);
                        intent.putExtra("from", client.getUserId());
                        intent.putExtra("to", to);
                        intent.putExtra("is_video_call", false);
                        startActivity(intent);
                    } else {
                        Utils.reportMessage(this, "Stringee session not connected");
                    }
                }
                break;
            case R.id.btn_video_call:
                to = etTo.getText().toString();
                if (to.trim().length() > 0) {
                    if (client.isConnected()) {
                        Intent intent = new Intent(this, OutgoingCallActivity.class);
                        intent.putExtra("from", client.getUserId());
                        intent.putExtra("to", to);
                        intent.putExtra("is_video_call", true);
                        startActivity(intent);
                    } else {
                        Utils.reportMessage(this, "Stringee session not connected");
                    }
                }
                break;

            case R.id.btn_voice_call2:
                to = etTo.getText().toString();
                if (to.trim().length() > 0) {
                    if (client.isConnected()) {
                        Intent intent = new Intent(this, OutgoingCall2Activity.class);
                        intent.putExtra("from", client.getUserId());
                        intent.putExtra("to", to);
                        intent.putExtra("is_video_call", false);
                        startActivity(intent);
                    } else {
                        Utils.reportMessage(this, "Stringee session not connected");
                    }
                }
                break;
            case R.id.btn_video_call2:
                to = etTo.getText().toString();
                if (to.trim().length() > 0) {
                    if (client.isConnected()) {
                        Intent intent = new Intent(this, OutgoingCall2Activity.class);
                        intent.putExtra("from", client.getUserId());
                        intent.putExtra("to", to);
                        intent.putExtra("is_video_call", true);
                        startActivity(intent);
                    } else {
                        Utils.reportMessage(this, "Stringee session not connected");
                    }
                }
                break;

            case R.id.btn_unregister:
                client.unregisterPushToken(sharedPreferences.getString(TOKEN, ""), new StatusListener() {
                    @Override
                    public void onSuccess() {
                        Log.d("Stringee", "Unregister push token successfully.");
                        editor.remove(IS_TOKEN_REGISTERED);
                        editor.remove(TOKEN);
                        editor.commit();
                    }

                    @Override
                    public void onError(StringeeError error) {
                        Log.d("Stringee", "Unregister push token unsuccessfully: " + error.getMessage());
                    }
                });
                break;
        }
    }
}
