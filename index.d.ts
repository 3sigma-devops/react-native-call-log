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

  const load: (limit: number, filter?: CallFilter) => Promise<CallLog[]>;

  const loadAll: () => Promise<CallLog[]>;

  const isAvailable: () => boolean;
}

export = CallLogs;
