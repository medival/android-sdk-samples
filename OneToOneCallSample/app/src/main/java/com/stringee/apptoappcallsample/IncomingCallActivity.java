package com.stringee.apptoappcallsample;

import android.Manifest;
import android.app.KeyguardManager;
import android.app.KeyguardManager.KeyguardLock;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager.LayoutParams;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.stringee.apptoappcallsample.R.id;
import com.stringee.call.StringeeCall;
import com.stringee.call.StringeeCall.MediaState;
import com.stringee.call.StringeeCall.SignalingState;
import com.stringee.common.StringeeAudioManager;
import com.stringee.listener.StatusListener;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class IncomingCallActivity extends AppCompatActivity implements View.OnClickListener {
    private FrameLayout mLocalViewContainer;
    private FrameLayout mRemoteViewContainer;
    private TextView tvFrom;
    private TextView tvState;
    private ImageButton btnAnswer;
    private ImageButton btnEnd;
    private ImageButton btnMute;
    private ImageButton btnSpeaker;
    private ImageButton btnVideo;
    private ImageButton btnSwitch;
    private View vControl;

    private StringeeCall mStringeeCall;
    private StringeeAudioManager audioManager;
    private boolean isMute = false;
    private boolean isSpeaker = false;
    private boolean isVideo = false;
    // 0: back camera, 1: front camera
    // When call starts, automatically use the front camera
    private int cameraId = 1;

    private StringeeCall.MediaState mMediaState;
    private StringeeCall.SignalingState mSignalingState;

    private KeyguardLock lock;

    public static final int REQUEST_PERMISSION_CALL = 1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //add Flag for show on lockScreen, disable keyguard, keep screen on
        getWindow().addFlags(LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | LayoutParams.FLAG_DISMISS_KEYGUARD
                | LayoutParams.FLAG_KEEP_SCREEN_ON
                | LayoutParams.FLAG_TURN_SCREEN_ON);

        setContentView(R.layout.activity_incoming_call);

        lock = ((KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE)).newKeyguardLock(Context.KEYGUARD_SERVICE);
        lock.disableKeyguard();

        if (VERSION.SDK_INT >= VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }

        Common.isInCall = true;

        String callId = getIntent().getStringExtra("call_id");
        mStringeeCall = Common.callsMap.get(callId);

        mLocalViewContainer = findViewById(id.v_local);
        mRemoteViewContainer = findViewById(id.v_remote);

        vControl = findViewById(id.v_control);

        tvFrom = findViewById(id.tv_from);
        tvFrom.setText(mStringeeCall.getFrom());
        tvState = findViewById(id.tv_state);

        btnAnswer = findViewById(id.btn_answer);
        btnAnswer.setOnClickListener(this);
        btnEnd = findViewById(id.btn_end);
        btnEnd.setOnClickListener(this);
        btnMute = findViewById(id.btn_mute);
        btnMute.setOnClickListener(this);
        btnSpeaker = findViewById(id.btn_speaker);
        btnSpeaker.setOnClickListener(this);
        btnVideo = findViewById(id.btn_video);
        btnVideo.setOnClickListener(this);
        btnSwitch = findViewById(id.btn_switch);
        btnSwitch.setOnClickListener(this);

        isSpeaker = mStringeeCall.isVideoCall();
        btnSpeaker.setBackgroundResource(isSpeaker ? R.drawable.btn_speaker_on : R.drawable.btn_speaker_off);

        isVideo = mStringeeCall.isVideoCall();
        btnVideo.setImageResource(isVideo ? R.drawable.btn_video : R.drawable.btn_video_off);

        btnVideo.setVisibility(isVideo ? View.VISIBLE : View.GONE);
        btnSwitch.setVisibility(isVideo ? View.VISIBLE : View.GONE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            List<String> lstPermissions = new ArrayList<>();

            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                lstPermissions.add(Manifest.permission.RECORD_AUDIO);
            }

            if (mStringeeCall.isVideoCall()) {
                if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED) {
                    lstPermissions.add(Manifest.permission.CAMERA);
                }
            }

            if (lstPermissions.size() > 0) {
                String[] permissions = new String[lstPermissions.size()];
                for (int i = 0; i < lstPermissions.size(); i++) {
                    permissions[i] = lstPermissions.get(i);
                }
                ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSION_CALL);
                return;
            }
        }

        startRinging();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean isGranted = false;
        if (grantResults.length > 0) {
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    isGranted = false;
                    break;
                } else {
                    isGranted = true;
                }
            }
        }
        if (requestCode == REQUEST_PERMISSION_CALL) {
            if (!isGranted) {
                tvState.setText("Ended");
                endCall(false, true);
            } else {
                startRinging();
            }
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        tvState.setText("Ended");
        endCall(true, false);
    }

    private void startRinging() {
        //create audio manager to control audio device
        audioManager = StringeeAudioManager.create(IncomingCallActivity.this);
        audioManager.start(new StringeeAudioManager.AudioManagerEvents() {
            @Override
            public void onAudioDeviceChanged(StringeeAudioManager.AudioDevice selectedAudioDevice, Set<StringeeAudioManager.AudioDevice> availableAudioDevices) {
            }
        });
        audioManager.setSpeakerphoneOn(isVideo);

        mStringeeCall.setCallListener(new StringeeCall.StringeeCallListener() {
            @Override
            public void onSignalingStateChange(StringeeCall stringeeCall, final StringeeCall.SignalingState signalingState, String s, int i, String s1) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Log.d("Stringee", "signalingState: " + signalingState);
                        mSignalingState = signalingState;
                        switch (signalingState) {
                            case ANSWERED:
                                tvState.setText("Starting");
                                if (mMediaState == StringeeCall.MediaState.CONNECTED) {
                                    startCall();
                                }
                                break;
                            case ENDED:
                                tvState.setText("Ended");
                                endCall(true, false);
                                break;
                        }
                    }
                });
            }

            @Override
            public void onError(StringeeCall stringeeCall, int i, String s) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Utils.reportMessage(IncomingCallActivity.this, s);
                    }
                });
            }

            @Override
            public void onHandledOnAnotherDevice(StringeeCall stringeeCall, final StringeeCall.SignalingState signalingState, String s) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (signalingState == StringeeCall.SignalingState.ANSWERED || signalingState == StringeeCall.SignalingState.BUSY) {
                            Utils.reportMessage(IncomingCallActivity.this, "This call is handled on another device.");
                            tvState.setText("Ended");
                            endCall(true, false);
                        }
                    }
                });
            }

            @Override
            public void onMediaStateChange(StringeeCall stringeeCall, final StringeeCall.MediaState mediaState) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Log.d("Stringee", "mediaState: " + mediaState);
                        mMediaState = mediaState;
                        if (mediaState == StringeeCall.MediaState.CONNECTED) {
                            if (mSignalingState == StringeeCall.SignalingState.ANSWERED) {
                                startCall();
                            }
                        } else {
                            tvState.setText("Reconnecting...");
                        }
                    }
                });
            }

            @Override
            public void onLocalStream(final StringeeCall stringeeCall) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (stringeeCall.isVideoCall()) {
                            mLocalViewContainer.removeAllViews();
                            SurfaceView localView = stringeeCall.getLocalView();
                            mLocalViewContainer.addView(localView);
                            stringeeCall.renderLocalView(true);
                        }
                    }
                });
            }

            @Override
            public void onRemoteStream(final StringeeCall stringeeCall) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (stringeeCall.isVideoCall()) {
                            mRemoteViewContainer.removeAllViews();
                            SurfaceView remoteView = stringeeCall.getRemoteView();
                            mRemoteViewContainer.addView(remoteView);
                            stringeeCall.renderRemoteView(false);
                        }
                    }
                });
            }

            @Override
            public void onCallInfo(StringeeCall stringeeCall, final JSONObject jsonObject) {

            }
        });
        mStringeeCall.ringing(new StatusListener() {
            @Override
            public void onSuccess() {
                Log.d("Stringee", "send ringing success");
            }
        });
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_mute:
                isMute = !isMute;
                btnMute.setBackgroundResource(isMute ? R.drawable.btn_mute : R.drawable.btn_mic);
                if (mStringeeCall != null) {
                    mStringeeCall.mute(isMute);
                }
                break;
            case R.id.btn_speaker:
                isSpeaker = !isSpeaker;
                btnSpeaker.setBackgroundResource(isSpeaker ? R.drawable.btn_speaker_on : R.drawable.btn_speaker_off);
                if (mSignalingState == SignalingState.ANSWERED || mMediaState == MediaState.CONNECTED) {
                    if (audioManager != null) {
                        audioManager.setSpeakerphoneOn(isSpeaker);
                    }
                }
                break;
            case R.id.btn_answer:
                if (mStringeeCall != null) {
                    vControl.setVisibility(View.VISIBLE);
                    btnAnswer.setVisibility(View.GONE);
                    mStringeeCall.answer();
                }
                break;
            case R.id.btn_end:
                tvState.setText("Ended");
                endCall(true, false);
                break;
            case R.id.btn_video:
                isVideo = !isVideo;
                btnVideo.setImageResource(isVideo ? R.drawable.btn_video : R.drawable.btn_video_off);
                if (mStringeeCall != null) {
                    mStringeeCall.enableVideo(isVideo);
                }
                break;
            case R.id.btn_switch:
                if (mStringeeCall != null) {
                    mStringeeCall.switchCamera(new StatusListener() {
                        @Override
                        public void onSuccess() {
                            cameraId = cameraId == 0 ? 1 : 0;
                        }
                    }, cameraId == 0 ? 1 : 0);
                }
                break;
        }
    }

    private void startCall() {
        tvState.setText("Started");
    }

    private void endCall(boolean isHangup, boolean isReject) {
        if (isHangup) {
            if (mStringeeCall != null) {
                mStringeeCall.hangup();
            }
        }

        if (isReject) {
            if (mStringeeCall != null) {
                mStringeeCall.reject();
            }
        }

        if (audioManager != null) {
            audioManager.stop();
            audioManager = null;
        }

        Utils.postDelay(new Runnable() {
            @Override
            public void run() {
                Common.isInCall = false;
                finish();
            }
        }, 1000);
    }
}
