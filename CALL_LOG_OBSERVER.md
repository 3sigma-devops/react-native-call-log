# Call Log Observer

Real-time call log sync for React Native. Get notified when new calls are logged.

## Installation

No additional installation required - this feature is included in `react-native-call-log`.

## Permissions

Ensure you have `READ_CALL_LOG` permission:

```javascript
import { PermissionsAndroid } from 'react-native';

await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.READ_CALL_LOG);
```

## Usage

### Basic Example

```javascript
import { useEffect } from 'react';
import CallLogs from 'react-native-call-log';

function App() {
  useEffect(() => {
    const emitter = CallLogs.getEventEmitter();

    // Start listening for new calls
    CallLogs.startObserver();

    // Handle new call log entries
    const subscription = emitter.addListener('onCallLogChange', (call) => {
      console.log('New call:', call.phoneNumber, call.type, call.duration);
    });

    // Cleanup on unmount
    return () => {
      subscription.remove();
      CallLogs.stopObserver();
    };
  }, []);

  return <YourApp />;
}
```

### With State Management

```javascript
import { useEffect, useState } from 'react';
import CallLogs from 'react-native-call-log';

function CallHistory() {
  const [recentCalls, setRecentCalls] = useState([]);

  useEffect(() => {
    const emitter = CallLogs.getEventEmitter();
    CallLogs.startObserver();

    const subscription = emitter.addListener('onCallLogChange', (call) => {
      // Add new call to the top of the list
      setRecentCalls(prev => [call, ...prev]);
    });

    return () => {
      subscription.remove();
      CallLogs.stopObserver();
    };
  }, []);

  return (
    <FlatList
      data={recentCalls}
      renderItem={({ item }) => (
        <Text>{item.phoneNumber} - {item.type}</Text>
      )}
    />
  );
}
```

## API Reference

### Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `startObserver()` | `Promise<boolean>` | Start listening for new call log entries |
| `stopObserver()` | `Promise<boolean>` | Stop listening for changes |
| `isObserving()` | `Promise<boolean>` | Check if observer is currently active |
| `getEventEmitter()` | `NativeEventEmitter` | Get event emitter for subscriptions |

### Events

| Event Name | Description |
|------------|-------------|
| `onCallLogChange` | Fired when a new call is added to the call log (after call ends) |

### Event Data Structure

```typescript
interface CallLogEvent {
  phoneNumber: string;      // Phone number of the call
  duration: number;         // Call duration in seconds
  name: string;             // Contact name (empty if not in contacts)
  timestamp: string;        // Unix timestamp in milliseconds
  dateTime: string;         // Formatted date/time string
  type: string;             // Call type (see below)
  rawType: number;          // Android raw type code
  phoneAccountId?: string;  // Phone account identifier
  simSlot?: number;         // SIM slot number (1 or 2)
}
```

### Call Types

| Type | Description |
|------|-------------|
| `INCOMING` | Received call |
| `OUTGOING` | Made call |
| `MISSED` | Missed call |
| `VOICEMAIL` | Voicemail |
| `REJECTED` | Rejected call |
| `BLOCKED` | Blocked call |
| `ANSWERED_EXTERNALLY` | Answered on another device |
| `UNKNOWN` | Unknown type |

## Background Support

The observer works in the background as long as your app process is alive. If you have a foreground service running, the observer will continue to detect new calls even when the app is minimized.

## Notes

- Events are only emitted for NEW call log entries (calls that happen after `startObserver()` is called)
- Each call triggers exactly one event (after the call ends and is logged)
- The observer uses Android's `ContentObserver` to watch the call log database
- No additional permissions beyond `READ_CALL_LOG` are required
