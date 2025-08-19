package com.wscodelabs.callLogs;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.facebook.react.jstasks.HeadlessJsTaskConfig;
import com.facebook.react.HeadlessJsTaskService;
import com.facebook.react.bridge.Arguments;

/**
 * This HeadlessJsTaskService runs your React Native task in background.
 * It works when the app is killed or in background.
 */
public class CallLogHeadlessTaskService extends HeadlessJsTaskService {

  @Override
  protected @Nullable HeadlessJsTaskConfig getTaskConfig(Intent intent) {
    Bundle extras = intent.getExtras();

    return new HeadlessJsTaskConfig(
      "CallLogHeadlessTask", // <-- must match your JS registration name
      Arguments.fromBundle(extras != null ? extras : new Bundle()),
      5000, // Timeout in ms
      true  // Allow task to run in foreground as well
    );
  }
}
