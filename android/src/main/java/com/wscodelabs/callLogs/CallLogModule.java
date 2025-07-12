package com.yourpackage.callLogs;

import android.Manifest;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.CallLog;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;

import androidx.core.app.ActivityCompat;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * CallLogModule
 * ------------------------------
 * Provides CallLogs.load() to your RN app.
 * Now runs safely on a background thread.
 * Also maps SIM slot & SIM phone number for each call log.
 */
public class CallLogModule extends ReactContextBaseJavaModule {

  private final ReactApplicationContext reactContext;

  public CallLogModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
  }

  @Override
  public String getName() {
    return "CallLogs";
  }

  @ReactMethod
  public void load(final int limit, final ReadableMap filter, final Promise promise) {
    Executors.newSingleThreadExecutor().execute(() -> {
      if (ActivityCompat.checkSelfPermission(
              reactContext,
              Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
        promise.reject("PERMISSION_DENIED", "READ_CALL_LOG permission not granted");
        return;
      }

      WritableArray callLogs = Arguments.createArray();
      Cursor cursor = null;

      try {
        String sortOrder = CallLog.Calls.DATE + " DESC LIMIT " + limit;

        cursor = reactContext.getContentResolver().query(
                CallLog.Calls.CONTENT_URI,
                null,
                null,
                null,
                sortOrder
        );

        // Fetch active SIM info
        SubscriptionManager sm = SubscriptionManager.from(reactContext);
        List<SubscriptionInfo> subs = sm.getActiveSubscriptionInfoList();
        if (subs == null) subs = new ArrayList<>();

        if (cursor != null && cursor.moveToFirst()) {
          do {
            WritableMap call = Arguments.createMap();

            String number = cursor.getString(cursor.getColumnIndex(CallLog.Calls.NUMBER));
            String name = cursor.getString(cursor.getColumnIndex(CallLog.Calls.CACHED_NAME));
            String date = cursor.getString(cursor.getColumnIndex(CallLog.Calls.DATE));
            String duration = cursor.getString(cursor.getColumnIndex(CallLog.Calls.DURATION));
            String type = cursor.getString(cursor.getColumnIndex(CallLog.Calls.TYPE));
            String simId = cursor.getString(cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID));

            // Default values if mapping fails
            int simSlot = -1;
            String simNumber = "";

            // Map simId to real SIM slot and phone number
            for (SubscriptionInfo info : subs) {
              // Depending on OEM, match subscriptionId or iccId
              if (String.valueOf(info.getSubscriptionId()).equals(simId)
                      || (info.getIccId() != null && info.getIccId().equals(simId))) {
                simSlot = info.getSimSlotIndex();
                simNumber = info.getNumber() != null ? info.getNumber() : "";
                break;
              }
            }

            // Build result
            call.putString("number", number);
            call.putString("name", name != null ? name : "");
            call.putString("date", date);
            call.putString("duration", duration);
            call.putString("type", type);
            call.putString("simSlot", String.valueOf(simSlot));
            call.putString("simPhoneNumber", simNumber);

            callLogs.pushMap(call);

          } while (cursor.moveToNext());
        }

        promise.resolve(callLogs);

      } catch (Exception e) {
        promise.reject("CALL_LOG_ERROR", e);
      } finally {
        if (cursor != null) cursor.close();
      }
    });
  }
}
