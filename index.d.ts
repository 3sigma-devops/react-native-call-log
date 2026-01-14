import { NativeEventEmitter } from 'react-native';

declare namespace CallLogs {
  export enum CallType {
    OUTGOING = 'OUTGOING',
    INCOMING = 'INCOMING',
    MISSED = 'MISSED',
    VOICEMAIL = 'VOICEMAIL',
    REJECTED = 'REJECTED',
    BLOCKED = 'BLOCKED',
    ANSWERED_EXTERNALLY = 'ANSWERED_EXTERNALLY',
    UNKNOWN = 'UNKNOWN',
  }

  export interface CallFilter {
    minTimestamp?: number;
    maxTimestamp?: number;
    types?: CallType | CallType[];
    phoneNumbers?: string | string[];
  }

  export interface CallLog {
    phoneNumber: string;
    duration: number;
    name: string;
    timestamp: string;
    dateTime: string;
    type: CallType;
    rawType: number;
    phoneAccountId?: string;
    simSlot?: number;
  }

  /**
   * Event emitted when a new call log entry is detected
   * Subscribe via: CallLogs.getEventEmitter().addListener('onCallLogChange', callback)
   */
  export interface CallLogChangeEvent extends CallLog {}

  const load: (limit: number, filter?: CallFilter) => Promise<CallLog[]>;

  const loadAll: () => Promise<CallLog[]>;

  const isAvailable: () => boolean;

  /**
   * Start observing call log changes
   * Events will be emitted via NativeEventEmitter when new calls are logged
   * @returns Promise that resolves to true if observer started successfully
   */
  const startObserver: () => Promise<boolean>;

  /**
   * Stop observing call log changes
   * @returns Promise that resolves to true if observer stopped successfully
   */
  const stopObserver: () => Promise<boolean>;

  /**
   * Check if currently observing call log changes
   * @returns Promise that resolves to true if observer is active
   */
  const isObserving: () => Promise<boolean>;

  /**
   * Get the event emitter for subscribing to call log changes
   * Use: CallLogs.getEventEmitter().addListener('onCallLogChange', callback)
   * @returns Event emitter instance (null on non-Android)
   */
  const getEventEmitter: () => NativeEventEmitter | null;
}

export = CallLogs;
