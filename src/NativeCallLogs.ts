import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface CallLog {
  phoneNumber: string;
  duration: number;
  name: string;
  timestamp: string;
  dateTime: string;
  type: string;
  rawType: number;
  phoneAccountId?: string | null;
  simSlot?: number | null;
}

export interface Spec extends TurboModule {
  load(limit: number): Promise<Array<Object>>;
  loadAll(): Promise<Array<Object>>;
  loadWithFilter(limit: number, filter?: Object | null): Promise<Array<Object>>;

  // Observer methods for real-time call log sync
  startObserver(): Promise<boolean>;
  stopObserver(): Promise<boolean>;
  isObserving(): Promise<boolean>;

  // Required for NativeEventEmitter
  addListener(eventName: string): void;
  removeListeners(count: number): void;
}

export default TurboModuleRegistry.getEnforcing<Spec>('CallLogs');
