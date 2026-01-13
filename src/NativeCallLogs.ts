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
}

export default TurboModuleRegistry.getEnforcing<Spec>('CallLogs');
