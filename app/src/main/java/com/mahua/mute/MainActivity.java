package com.mahua.mute;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private AudioManager audioManager;
    private int lastNonSilentVolume = -1;
    private int lastNonSilentMode = -1;
    private boolean hasSaved = false;
    private boolean isCurrentlySilent = false;
    private static final int REQUEST_CODE_DND_ACCESS = 100;
    private static final String PREFS_NAME = "MutePrefs";
    private static final String KEY_LAST_VOLUME = "last_volume";
    private static final String KEY_LAST_MODE = "last_mode";
    private static final String KEY_HAS_SAVED = "has_saved";
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (audioManager == null) {
                Toast.makeText(this, "无法获取音频管理器", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            loadSavedState();

            final Button btnRestore = findViewById(R.id.btn_restore);
            final Button btnStatus = findViewById(R.id.btn_status);
            final TextView tvStatus = findViewById(R.id.tv_status);

            int currentMode = audioManager.getRingerMode();
            isCurrentlySilent = (currentMode == AudioManager.RINGER_MODE_SILENT);

            if (!isCurrentlySilent) {
                // 当前是非静音模式：保存当前状态（覆盖旧值）并设置为静音
                saveCurrentState();
                checkAndRequestDoNotDisturbPermission();
                setToSilentMode();
                Toast.makeText(this, "已保存当前铃声并静音", Toast.LENGTH_SHORT).show();
            } else {
                // 当前已是静音模式：不做任何保存和静音操作
                Toast.makeText(this, "设备已是静音模式，无需操作", Toast.LENGTH_SHORT).show();
            }

            updateStatusText(tvStatus);

            btnRestore.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    restoreLastNonSilentState();
                    updateStatusText(tvStatus);
                }
            });

            btnStatus.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showCurrentState();
                    updateStatusText(tvStatus);
                }
            });

        } catch (Exception e) {
            Toast.makeText(this, "初始化错误：" + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    private void loadSavedState() {
        hasSaved = prefs.getBoolean(KEY_HAS_SAVED, false);
        if (hasSaved) {
            lastNonSilentVolume = prefs.getInt(KEY_LAST_VOLUME, -1);
            lastNonSilentMode = prefs.getInt(KEY_LAST_MODE, -1);
        }
    }

    private void saveCurrentState() {
        try {
            int volume = audioManager.getStreamVolume(AudioManager.STREAM_RING);
            int mode = audioManager.getRingerMode();
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(KEY_LAST_VOLUME, volume);
            editor.putInt(KEY_LAST_MODE, mode);
            editor.putBoolean(KEY_HAS_SAVED, true);
            editor.apply();
            lastNonSilentVolume = volume;
            lastNonSilentMode = mode;
            hasSaved = true;
            Toast.makeText(this, "已保存状态 - 音量:" + volume + " 模式:" + ringerModeToString(mode), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void restoreLastNonSilentState() {
        if (!hasSaved) {
            Toast.makeText(this, "没有可恢复的铃声状态（从未在非静音模式下打开过）", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            audioManager.setRingerMode(lastNonSilentMode);
            audioManager.setStreamVolume(AudioManager.STREAM_RING, lastNonSilentVolume, 0);
            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, lastNonSilentVolume, 0);
            Toast.makeText(this, "已恢复至上次非静音状态 - 音量:" + lastNonSilentVolume +
                    " 模式:" + ringerModeToString(lastNonSilentMode), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "恢复失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void checkAndRequestDoNotDisturbPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null && !notificationManager.isNotificationPolicyAccessGranted()) {
                Toast.makeText(this, "请授予勿扰权限以允许应用设置静音模式", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                startActivityForResult(intent, REQUEST_CODE_DND_ACCESS);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_DND_ACCESS) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (notificationManager != null && notificationManager.isNotificationPolicyAccessGranted()) {
                    setToSilentMode();
                } else {
                    Toast.makeText(this, "未授予勿扰权限，应用无法设置静音模式。", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void setToSilentMode() {
        try {
            audioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
            Toast.makeText(this, "已设置为静音模式（无铃声、无振动）", Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            Toast.makeText(this, "设置静音模式失败：请授予勿扰权限", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "设置静音模式失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showCurrentState() {
        try {
            int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING);
            int currentMode = audioManager.getRingerMode();
            Toast.makeText(this, "当前铃声音量：" + currentVolume +
                    "\n当前模式：" + ringerModeToString(currentMode), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "获取状态失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateStatusText(final TextView tvStatus) {
        if (tvStatus == null) return;
        try {
            int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING);
            int currentMode = audioManager.getRingerMode();
            String modeDesc;
            if (currentMode == AudioManager.RINGER_MODE_SILENT) {
                modeDesc = "静音模式（无声无振动）";
            } else if (currentMode == AudioManager.RINGER_MODE_VIBRATE) {
                modeDesc = "振动模式";
            } else {
                modeDesc = "正常模式（音量" + currentVolume + "）";
            }

            if (hasSaved) {
                tvStatus.setText("当前状态：" + modeDesc + "\n上次非静音状态：音量" + lastNonSilentVolume +
                        " 模式" + ringerModeToString(lastNonSilentMode));
            } else {
                tvStatus.setText("当前状态：" + modeDesc + "\n无历史非静音记录");
            }
        } catch (Exception e) {
            tvStatus.setText("状态获取失败");
        }
    }

    private String ringerModeToString(int mode) {
        switch (mode) {
            case AudioManager.RINGER_MODE_NORMAL: return "正常";
            case AudioManager.RINGER_MODE_VIBRATE: return "振动";
            case AudioManager.RINGER_MODE_SILENT: return "静音";
            default: return "未知(" + mode + ")";
        }
    }
}
