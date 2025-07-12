export type CallType = 'INCOMING' | 'OUTGOING' | 'MISSED' | 'REJECTED' | 'BLOCKED' | 'VOICEMAIL';

export interface CallLogItem {
  phoneNumber: string;
  name?: string;
  timestamp: number;
  duration: number;
  callType: CallType;
}

export interface CallLogFilter {
  minTimestamp?: number;
  maxTimestamp?: number;
  types?: CallType[] | CallType;
  phoneNumbers?: string[] | string;
}

declare class CallLogs {
  static load(limit: number, filter?: CallLogFilter): Promise<CallLogItem[]>;
  static loadAll(): Promise<CallLogItem[]>;
}

export default CallLogs;
