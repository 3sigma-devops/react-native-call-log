package com.wscodelabs.callLogs;

import android.Manifest;
import android.content.pm.PackageManager;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.database.ContentObserver;
import android.database.Cursor;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Log;
import androidx.core.content.ContextCompat;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import org.json.JSONArray;
import org.json.JSONException;

import javax.annotation.Nullable;

public class CallLogModule extends ReactContextBaseJavaModule {

    private static final String TAG = "CallLogModule";
    private static final String EVENT_CALL_LOG_CHANGE = "onCallLogChange";

    private final Context context;
    private final ReactApplicationContext reactContext;
    private ContentObserver callLogObserver;
    private long lastTimestamp = 0;
    private boolean isObserving = false;
    private HandlerThread observerThread;
    private Handler observerHandler;

    public CallLogModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.context = reactContext;
        this.reactContext = reactContext;
    }

    @Override
    public String getName() {
        return "CallLogs";
    }

    /**
     * Check if READ_CALL_LOG permission is granted
     * @return true if permission is granted
     */
    private boolean hasCallLogPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG)
                == PackageManager.PERMISSION_GRANTED;
        }
        // Pre-Marshmallow: permission is granted at install time
        return true;
    }

    @ReactMethod
    public void loadAll(Promise promise) {
        load(-1, promise);
    }

    @ReactMethod
    public void load(int limit, Promise promise) {
        loadWithFilter(limit, null, promise);
    }

    @ReactMethod
    public void loadWithFilter(int limit, @Nullable ReadableMap filter, Promise promise) {
        Cursor cursor = null;
        try {
            // Check permission first
            if (!hasCallLogPermission()) {
                Log.e(TAG, "READ_CALL_LOG permission not granted");
                promise.reject("PERMISSION_DENIED", "READ_CALL_LOG permission is required to access call logs");
                return;
            }

            // Validate context
            if (context == null || context.getContentResolver() == null) {
                Log.e(TAG, "Context or ContentResolver is null");
                promise.reject("INITIALIZATION_ERROR", "Module context is not properly initialized");
                return;
            }

            // Build SQL WHERE clause for efficient filtering at database level
            StringBuilder selection = new StringBuilder();
            List<String> selectionArgs = new ArrayList<>();

            if (filter != null) {
                // Filter by timestamp range
                if (filter.hasKey("minTimestamp")) {
                    String minTimestamp = filter.getString("minTimestamp");
                    if (minTimestamp != null && !minTimestamp.equals("0")) {
                        if (selection.length() > 0) selection.append(" AND ");
                        selection.append(Calls.DATE).append(" >= ?");
                        selectionArgs.add(minTimestamp);
                    }
                }

                if (filter.hasKey("maxTimestamp")) {
                    String maxTimestamp = filter.getString("maxTimestamp");
                    if (maxTimestamp != null && !maxTimestamp.equals("-1")) {
                        if (selection.length() > 0) selection.append(" AND ");
                        selection.append(Calls.DATE).append(" <= ?");
                        selectionArgs.add(maxTimestamp);
                    }
                }

                // Filter by call types
                if (filter.hasKey("types")) {
                    String types = filter.getString("types");
                    if (types != null) {
                        JSONArray typesArray = new JSONArray(types);
                        List<String> validTypeCodes = new ArrayList<>();

                        // Collect valid type codes first
                        for (int i = 0; i < typesArray.length(); i++) {
                            String typeStr = typesArray.optString(i);
                            int typeCode = resolveCallTypeCode(typeStr);
                            if (typeCode != -1) {
                                validTypeCodes.add(String.valueOf(typeCode));
                            }
                        }

                        // Only add to query if we have valid types
                        if (!validTypeCodes.isEmpty()) {
                            if (selection.length() > 0) selection.append(" AND ");
                            selection.append(Calls.TYPE).append(" IN (");
                            for (int i = 0; i < validTypeCodes.size(); i++) {
                                if (i > 0) selection.append(", ");
                                selection.append("?");
                                selectionArgs.add(validTypeCodes.get(i));
                            }
                            selection.append(")");
                        }
                    }
                }

                // Filter by phone numbers
                if (filter.hasKey("phoneNumbers")) {
                    String phoneNumbers = filter.getString("phoneNumbers");
                    if (phoneNumbers != null) {
                        JSONArray phoneNumbersArray = new JSONArray(phoneNumbers);
                        if (phoneNumbersArray.length() > 0) {
                            if (selection.length() > 0) selection.append(" AND ");
                            selection.append(Calls.NUMBER).append(" IN (");
                            for (int i = 0; i < phoneNumbersArray.length(); i++) {
                                if (i > 0) selection.append(", ");
                                selection.append("?");
                                selectionArgs.add(phoneNumbersArray.optString(i));
                            }
                            selection.append(")");
                        }
                    }
                }
            }

            // Build query
            String selectionStr = selection.length() > 0 ? selection.toString() : null;
            String[] selectionArgsArray = selectionArgs.size() > 0
                ? selectionArgs.toArray(new String[0])
                : null;
            String sortOrder = Calls.DATE + " DESC" + (limit > 0 ? " LIMIT " + limit : "");

            // Execute query with try-with-resources pattern for automatic cursor cleanup
            cursor = context.getContentResolver().query(
                CallLog.Calls.CONTENT_URI,
                null,
                selectionStr,
                selectionArgsArray,
                sortOrder
            );

            WritableArray result = Arguments.createArray();

            if (cursor == null) {
                Log.w(TAG, "Call log query returned null cursor");
                promise.resolve(result);
                return;
            }

            // Get column indices
            final int numberIndex = cursor.getColumnIndex(Calls.NUMBER);
            final int typeIndex = cursor.getColumnIndex(Calls.TYPE);
            final int dateIndex = cursor.getColumnIndex(Calls.DATE);
            final int durationIndex = cursor.getColumnIndex(Calls.DURATION);
            final int nameIndex = cursor.getColumnIndex(Calls.CACHED_NAME);
            final int phoneAccountIdIndex = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                ? cursor.getColumnIndex(Calls.PHONE_ACCOUNT_ID) : -1;

            // Process cursor results
            while (cursor.moveToNext()) {
                try {
                    WritableMap callLog = Arguments.createMap();

                    // Basic call information
                    String phoneNumber = numberIndex != -1 ? cursor.getString(numberIndex) : null;
                    int duration = durationIndex != -1 ? cursor.getInt(durationIndex) : 0;
                    String name = nameIndex != -1 ? cursor.getString(nameIndex) : null;
                    String timestampStr = dateIndex != -1 ? cursor.getString(dateIndex) : "0";
                    int rawType = typeIndex != -1 ? cursor.getInt(typeIndex) : Calls.INCOMING_TYPE;

                    // Format date/time
                    DateFormat df = SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.MEDIUM, SimpleDateFormat.MEDIUM);
                    String dateTime = df.format(new Date(Long.parseLong(timestampStr)));

                    // Resolve call type
                    String type = resolveCallType(rawType);

                    // Get phone account ID and SIM slot info
                    String phoneAccountId = null;
                    Integer simSlot = null;

                    if (phoneAccountIdIndex != -1) {
                        phoneAccountId = cursor.getString(phoneAccountIdIndex);
                        simSlot = getSimSlotIndex(phoneAccountId);
                    }

                    // Build result object
                    callLog.putString("phoneNumber", phoneNumber != null ? phoneNumber : "");
                    callLog.putInt("duration", duration);
                    callLog.putString("name", name != null ? name : "");
                    callLog.putString("timestamp", timestampStr);
                    callLog.putString("dateTime", dateTime);
                    callLog.putString("type", type);
                    callLog.putInt("rawType", rawType);

                    if (phoneAccountId != null) {
                        callLog.putString("phoneAccountId", phoneAccountId);
                    } else {
                        callLog.putNull("phoneAccountId");
                    }

                    if (simSlot != null) {
                        callLog.putInt("simSlot", simSlot);
                    } else {
                        callLog.putNull("simSlot");
                    }

                    result.pushMap(callLog);
                } catch (Exception e) {
                    Log.e(TAG, "Error processing call log entry: " + e.getMessage());
                    // Continue processing other entries
                }
            }

            promise.resolve(result);

        } catch (JSONException e) {
            Log.e(TAG, "JSON parsing error in loadWithFilter: " + e.getMessage());
            promise.reject("JSON_PARSE_ERROR", "Failed to parse filter parameters", e);
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied when accessing call log: " + e.getMessage());
            promise.reject("PERMISSION_DENIED", "READ_CALL_LOG permission is required", e);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in loadWithFilter: " + e.getMessage());
            promise.reject("CALL_LOG_ERROR", "Failed to load call logs: " + e.getMessage(), e);
        } finally {
            // Ensure cursor is always closed
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Exception e) {
                    Log.e(TAG, "Error closing cursor: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Get SIM slot index from phone account ID
     * Returns null if unable to determine or not available
     * Supports various device manufacturer formats
     */
    @Nullable
    private Integer getSimSlotIndex(@Nullable String phoneAccountId) {
        if (phoneAccountId == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return null;
        }

        try {
            TelecomManager telecomManager = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
            if (telecomManager == null) {
                return null;
            }

            // Method 1: Match against TelecomManager's call capable phone accounts (Android 6.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    List<PhoneAccountHandle> phoneAccounts = telecomManager.getCallCapablePhoneAccounts();
                    if (phoneAccounts != null && !phoneAccounts.isEmpty()) {
                        for (int i = 0; i < phoneAccounts.size(); i++) {
                            PhoneAccountHandle handle = phoneAccounts.get(i);
                            if (handle != null && handle.getId() != null && handle.getId().equals(phoneAccountId)) {
                                // Return 1-based slot number (SIM 1, SIM 2, etc.)
                                Log.d(TAG, "SIM slot detected via TelecomManager: " + (i + 1) + " for accountId: " + phoneAccountId);
                                return i + 1;
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Could not get phone accounts from TelecomManager: " + e.getMessage());
                }
            }

            // Method 2: Parse phoneAccountId string directly
            // Common formats across manufacturers:
            // - Simple numeric: "0", "1", "2"
            // - Samsung: "0", "1"
            // - Xiaomi: "slot0", "slot1"
            // - Huawei: "1", "2"
            // - OnePlus: "0", "1"
            // - Generic: "sim_0", "sim_1", "89XXXXXX" (ICCID)

            String lowerCaseId = phoneAccountId.toLowerCase();

            // Handle simple single digit cases (most common)
            if (phoneAccountId.matches("^[0-9]$")) {
                int slot = Integer.parseInt(phoneAccountId);
                Log.d(TAG, "SIM slot detected via simple numeric: " + (slot + 1) + " for accountId: " + phoneAccountId);
                // Convert 0-based to 1-based
                return slot + 1;
            }

            // Handle "slot0", "slot1", etc.
            if (lowerCaseId.startsWith("slot") && lowerCaseId.length() > 4) {
                try {
                    String slotNum = lowerCaseId.substring(4, 5);
                    int slot = Integer.parseInt(slotNum);
                    Log.d(TAG, "SIM slot detected via 'slot' prefix: " + (slot + 1) + " for accountId: " + phoneAccountId);
                    return slot + 1;
                } catch (NumberFormatException e) {
                    // Continue to next method
                }
            }

            // Handle "sim_0", "sim_1", etc.
            if (lowerCaseId.startsWith("sim") && lowerCaseId.contains("_")) {
                try {
                    String[] parts = lowerCaseId.split("_");
                    if (parts.length > 1) {
                        int slot = Integer.parseInt(parts[1]);
                        Log.d(TAG, "SIM slot detected via 'sim_' prefix: " + (slot + 1) + " for accountId: " + phoneAccountId);
                        return slot + 1;
                    }
                } catch (NumberFormatException e) {
                    // Continue to next method
                }
            }

            // Handle cases with multiple digits - extract last single digit
            if (phoneAccountId.matches(".*[0-9].*")) {
                try {
                    // Extract all digits
                    String digits = phoneAccountId.replaceAll("[^0-9]", "");
                    if (!digits.isEmpty()) {
                        // Use last digit as it's often the slot indicator
                        int lastDigit = Integer.parseInt(digits.substring(digits.length() - 1));
                        // Only return if it looks like a reasonable slot number (0-3)
                        if (lastDigit >= 0 && lastDigit <= 3) {
                            Log.d(TAG, "SIM slot detected via digit extraction: " + (lastDigit + 1) + " for accountId: " + phoneAccountId);
                            return lastDigit + 1;
                        }
                    }
                } catch (NumberFormatException e) {
                    Log.d(TAG, "Could not parse slot number from phoneAccountId: " + phoneAccountId);
                }
            }

            Log.d(TAG, "Could not determine SIM slot from phoneAccountId: " + phoneAccountId);
        } catch (SecurityException e) {
            Log.w(TAG, "SecurityException when accessing TelecomManager: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error getting SIM slot index: " + e.getMessage(), e);
        }

        return null;
    }

    /**
     * Convert call type string to integer code
     */
    private int resolveCallTypeCode(String callType) {
        if (callType == null) {
            return -1;
        }

        switch (callType.toUpperCase()) {
            case "OUTGOING":
                return Calls.OUTGOING_TYPE;
            case "INCOMING":
                return Calls.INCOMING_TYPE;
            case "MISSED":
                return Calls.MISSED_TYPE;
            case "VOICEMAIL":
                return Calls.VOICEMAIL_TYPE;
            case "REJECTED":
                return Calls.REJECTED_TYPE;
            case "BLOCKED":
                return Calls.BLOCKED_TYPE;
            case "ANSWERED_EXTERNALLY":
                return Calls.ANSWERED_EXTERNALLY_TYPE;
            default:
                return -1;
        }
    }

    /**
     * Convert call type integer code to string
     */
    private String resolveCallType(int callTypeCode) {
        switch (callTypeCode) {
            case Calls.OUTGOING_TYPE:
                return "OUTGOING";
            case Calls.INCOMING_TYPE:
                return "INCOMING";
            case Calls.MISSED_TYPE:
                return "MISSED";
            case Calls.VOICEMAIL_TYPE:
                return "VOICEMAIL";
            case Calls.REJECTED_TYPE:
                return "REJECTED";
            case Calls.BLOCKED_TYPE:
                return "BLOCKED";
            case Calls.ANSWERED_EXTERNALLY_TYPE:
                return "ANSWERED_EXTERNALLY";
            default:
                return "UNKNOWN";
        }
    }

    // ==================== Call Log Observer Methods ====================

    /**
     * Send event to JavaScript
     */
    private void sendEvent(String eventName, WritableMap params) {
        if (reactContext.hasActiveReactInstance()) {
            reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
        }
    }

    /**
     * Start observing call log changes
     */
    @ReactMethod
    public void startObserver(Promise promise) {
        try {
            if (isObserving) {
                promise.resolve(true);
                return;
            }

            if (!hasCallLogPermission()) {
                promise.reject("PERMISSION_DENIED", "READ_CALL_LOG permission is required");
                return;
            }

            // Set initial timestamp to current time to only capture new calls
            lastTimestamp = System.currentTimeMillis();

            // Create worker thread for background processing
            observerThread = new HandlerThread("CallLogObserver");
            observerThread.start();
            observerHandler = new Handler(observerThread.getLooper());

            // Create ContentObserver
            callLogObserver = new ContentObserver(observerHandler) {
                @Override
                public void onChange(boolean selfChange) {
                    super.onChange(selfChange);
                    queryNewCallLogs();
                }

                @Override
                public void onChange(boolean selfChange, Uri uri) {
                    super.onChange(selfChange, uri);
                    queryNewCallLogs();
                }
            };

            // Register observer
            context.getContentResolver().registerContentObserver(
                CallLog.Calls.CONTENT_URI,
                true,
                callLogObserver
            );

            isObserving = true;
            Log.d(TAG, "Call log observer started, tracking from timestamp: " + lastTimestamp);
            promise.resolve(true);

        } catch (Exception e) {
            Log.e(TAG, "Error starting observer: " + e.getMessage());
            promise.reject("OBSERVER_ERROR", "Failed to start call log observer: " + e.getMessage(), e);
        }
    }

    /**
     * Stop observing call log changes
     */
    @ReactMethod
    public void stopObserver(Promise promise) {
        try {
            if (callLogObserver != null) {
                context.getContentResolver().unregisterContentObserver(callLogObserver);
                callLogObserver = null;
            }
            if (observerThread != null) {
                observerThread.quit();
                observerThread = null;
                observerHandler = null;
            }
            isObserving = false;
            Log.d(TAG, "Call log observer stopped");
            promise.resolve(true);
        } catch (Exception e) {
            Log.e(TAG, "Error stopping observer: " + e.getMessage());
            promise.reject("OBSERVER_ERROR", "Failed to stop call log observer: " + e.getMessage(), e);
        }
    }

    /**
     * Check if currently observing
     */
    @ReactMethod
    public void isObserving(Promise promise) {
        promise.resolve(isObserving);
    }

    /**
     * Required for NativeEventEmitter
     */
    @ReactMethod
    public void addListener(String eventName) {
        // Keep: Required for RN event emitter
    }

    /**
     * Required for NativeEventEmitter
     */
    @ReactMethod
    public void removeListeners(Integer count) {
        // Keep: Required for RN event emitter
    }

    /**
     * Query for new call logs since last check and emit events
     */
    private void queryNewCallLogs() {
        if (!hasCallLogPermission()) {
            Log.w(TAG, "No permission to query call logs");
            return;
        }

        Cursor cursor = null;
        try {
            // Query for calls newer than lastTimestamp
            String selection = Calls.DATE + " > ?";
            String[] selectionArgs = new String[]{String.valueOf(lastTimestamp)};
            String sortOrder = Calls.DATE + " ASC";

            cursor = context.getContentResolver().query(
                CallLog.Calls.CONTENT_URI,
                null,
                selection,
                selectionArgs,
                sortOrder
            );

            if (cursor == null || cursor.getCount() == 0) {
                return;
            }

            // Get column indices
            final int numberIndex = cursor.getColumnIndex(Calls.NUMBER);
            final int typeIndex = cursor.getColumnIndex(Calls.TYPE);
            final int dateIndex = cursor.getColumnIndex(Calls.DATE);
            final int durationIndex = cursor.getColumnIndex(Calls.DURATION);
            final int nameIndex = cursor.getColumnIndex(Calls.CACHED_NAME);
            final int phoneAccountIdIndex = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                ? cursor.getColumnIndex(Calls.PHONE_ACCOUNT_ID) : -1;

            long newestTimestamp = lastTimestamp;

            while (cursor.moveToNext()) {
                try {
                    WritableMap callLog = Arguments.createMap();

                    String phoneNumber = numberIndex != -1 ? cursor.getString(numberIndex) : null;
                    int duration = durationIndex != -1 ? cursor.getInt(durationIndex) : 0;
                    String name = nameIndex != -1 ? cursor.getString(nameIndex) : null;
                    String timestampStr = dateIndex != -1 ? cursor.getString(dateIndex) : "0";
                    int rawType = typeIndex != -1 ? cursor.getInt(typeIndex) : Calls.INCOMING_TYPE;

                    long entryTimestamp = Long.parseLong(timestampStr);
                    if (entryTimestamp > newestTimestamp) {
                        newestTimestamp = entryTimestamp;
                    }

                    DateFormat df = SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.MEDIUM, SimpleDateFormat.MEDIUM);
                    String dateTime = df.format(new Date(entryTimestamp));

                    String type = resolveCallType(rawType);

                    String phoneAccountId = null;
                    Integer simSlot = null;

                    if (phoneAccountIdIndex != -1) {
                        phoneAccountId = cursor.getString(phoneAccountIdIndex);
                        simSlot = getSimSlotIndex(phoneAccountId);
                    }

                    callLog.putString("phoneNumber", phoneNumber != null ? phoneNumber : "");
                    callLog.putInt("duration", duration);
                    callLog.putString("name", name != null ? name : "");
                    callLog.putString("timestamp", timestampStr);
                    callLog.putString("dateTime", dateTime);
                    callLog.putString("type", type);
                    callLog.putInt("rawType", rawType);

                    if (phoneAccountId != null) {
                        callLog.putString("phoneAccountId", phoneAccountId);
                    } else {
                        callLog.putNull("phoneAccountId");
                    }

                    if (simSlot != null) {
                        callLog.putInt("simSlot", simSlot);
                    } else {
                        callLog.putNull("simSlot");
                    }

                    // Emit event for this call log entry
                    Log.d(TAG, "New call log detected: " + phoneNumber + " type: " + type);
                    sendEvent(EVENT_CALL_LOG_CHANGE, callLog);

                } catch (Exception e) {
                    Log.e(TAG, "Error processing new call log entry: " + e.getMessage());
                }
            }

            // Update lastTimestamp to newest entry
            lastTimestamp = newestTimestamp;

        } catch (Exception e) {
            Log.e(TAG, "Error querying new call logs: " + e.getMessage());
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Exception e) {
                    Log.e(TAG, "Error closing cursor: " + e.getMessage());
                }
            }
        }
    }
}
