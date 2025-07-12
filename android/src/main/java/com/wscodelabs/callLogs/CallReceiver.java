package com.yourpackage.callLogs;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.facebook.react.HeadlessJsTaskService;

/**
 * This BroadcastReceiver listens for PHONE_STATE changes.
 * It will wake up even when the app is killed.
 * When a call ends (IDLE), it triggers a Headless JS task.
 */
public class CallReceiver extends BroadcastReceiver {

  @Override
  public void onReceive(Context context, Intent intent) {
    String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
    Log.d("CallReceiver", "Phone state changed: " + state);

    if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
      Log.d("CallReceiver", "Call ended. Invoking Headless JS task.");

      // Wake lock ensures JS task can run in background.
      HeadlessJsTaskService.acquireWakeLockNow(context);

      // Start our custom HeadlessJsTaskService.
      Intent service = new Intent(context, CallLogHeadlessTaskService.class);
      context.startService(service);
    }
  }
}
