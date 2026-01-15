import { NativeModules, NativeEventEmitter, Platform } from 'react-native';

// Try to use TurboModule (New Architecture) first, fallback to NativeModules (Old Architecture)
let NativeCallLogs;
try {
  // New Architecture support
  NativeCallLogs = require('./src/NativeCallLogs').default;
} catch (e) {
  // Old Architecture fallback
  NativeCallLogs = NativeModules.CallLogs;
}

if (Platform.OS === 'android' && !NativeCallLogs) {
  throw new Error(
    'CallLogs: Native module not found. Make sure react-native-call-log is properly installed and linked.'
  );
}

// Create event emitter for call log change events
const eventEmitter = Platform.OS === 'android' ? new NativeEventEmitter(NativeCallLogs) : null;

class CallLogs {
  /**
   * Load call logs with optional limit and filter
   * @param {number} limit - Maximum number of call logs to return (-1 for all)
   * @param {Object} filter - Optional filter object
   * @param {number} filter.minTimestamp - Minimum timestamp (inclusive)
   * @param {number} filter.maxTimestamp - Maximum timestamp (inclusive)
   * @param {string|string[]} filter.types - Call types to filter (INCOMING, OUTGOING, MISSED, etc.)
   * @param {string|string[]} filter.phoneNumbers - Phone numbers to filter
   * @returns {Promise<Array>} Array of call log objects
   */
  static async load(limit, filter) {
    try {
      if (!filter) {
        return await NativeCallLogs.load(limit);
      }

      const {minTimestamp, maxTimestamp, types, phoneNumbers} = filter;

      // Normalize phone numbers to array
      const phoneNumbersArray = Array.isArray(phoneNumbers)
        ? phoneNumbers
        : typeof phoneNumbers === 'string'
        ? [phoneNumbers]
        : [];

      // Normalize types to array
      const typesArray = Array.isArray(types)
        ? types.map(x => x.toString())
        : (typeof types === 'string' || typeof types === 'object')
        ? [types.toString()]
        : [];

      return await NativeCallLogs.loadWithFilter(
        limit,
        {
          minTimestamp: minTimestamp ? minTimestamp.toString() : undefined,
          maxTimestamp: maxTimestamp ? maxTimestamp.toString() : undefined,
          types: JSON.stringify(typesArray),
          phoneNumbers: JSON.stringify(phoneNumbersArray),
        }
      );
    } catch (error) {
      // Enhanced error handling with retry logic
      if (error.code === 'PERMISSION_DENIED') {
        throw new Error(
          'CallLogs: READ_CALL_LOG permission is required. Please request this permission before accessing call logs.'
        );
      }

      console.error('CallLogs.load error:', error);
      throw error;
    }
  }

  /**
   * Load all call logs without limit
   * @returns {Promise<Array>} Array of all call log objects
   */
  static async loadAll() {
    try {
      return await NativeCallLogs.loadAll();
    } catch (error) {
      if (error.code === 'PERMISSION_DENIED') {
        throw new Error(
          'CallLogs: READ_CALL_LOG permission is required. Please request this permission before accessing call logs.'
        );
      }

      console.error('CallLogs.loadAll error:', error);
      throw error;
    }
  }

  /**
   * Check if the module is available (for debugging)
   * @returns {boolean} True if native module is available
   */
  static isAvailable() {
    return Platform.OS === 'android' && !!NativeCallLogs;
  }

  /**
   * Start observing call log changes
   * Events will be emitted via NativeEventEmitter when new calls are logged
   * @returns {Promise<boolean>} True if observer started successfully
   */
  static async startObserver() {
    if (Platform.OS !== 'android') {
      throw new Error('CallLogs.startObserver is only available on Android');
    }
    try {
      return await NativeCallLogs.startObserver();
    } catch (error) {
      if (error.code === 'PERMISSION_DENIED') {
        throw new Error(
          'CallLogs: READ_CALL_LOG permission is required. Please request this permission before starting observer.'
        );
      }
      console.error('CallLogs.startObserver error:', error);
      throw error;
    }
  }

  /**
   * Stop observing call log changes
   * @returns {Promise<boolean>} True if observer stopped successfully
   */
  static async stopObserver() {
    if (Platform.OS !== 'android') {
      throw new Error('CallLogs.stopObserver is only available on Android');
    }
    try {
      return await NativeCallLogs.stopObserver();
    } catch (error) {
      console.error('CallLogs.stopObserver error:', error);
      throw error;
    }
  }

  /**
   * Check if currently observing call log changes
   * @returns {Promise<boolean>} True if observer is active
   */
  static async isObserving() {
    if (Platform.OS !== 'android') {
      return false;
    }
    try {
      return await NativeCallLogs.isObserving();
    } catch (error) {
      console.error('CallLogs.isObserving error:', error);
      throw error;
    }
  }

  /**
   * Get the event emitter for subscribing to call log changes
   * Use: CallLogs.getEventEmitter().addListener('onCallLogChange', callback)
   * @returns {NativeEventEmitter|null} Event emitter instance (null on non-Android)
   */
  static getEventEmitter() {
    return eventEmitter;
  }
}

module.exports = CallLogs;
