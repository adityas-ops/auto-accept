import {NativeModules, NativeEventEmitter} from 'react-native';

export type Config = {
  enabled: boolean;
  packages: string[];
  acceptTexts: string[];
  sliderTexts: string[];
  minFare: number;
  minPerKm: number;
  maxPickupKm: number;
  minTripKm: number;
  maxTripKm: number;
  bookingTypes: Record<'bike' | 'auto' | 'cab' | 'parcel' | 'boost', boolean>;
  blockedKeywords: string[];
  allowedKeywords: string[];
  testMode: boolean;
  delayMs: number;
  overlay: boolean;
  voice: boolean;
  vibrate: boolean;
  acceptUnknownFare: boolean;
  acceptBoost: boolean;
};

export type HistoryItem = {
  time: number;
  pkg: string;
  fare: number | null;
  pickupKm: number | null;
  tripKm: number | null;
  type: string | null;
  accepted: boolean;
  note: string;
};

export type Status = {accessibility: boolean; overlay: boolean; battery: boolean};

const M = NativeModules.RideAssist;
export const native = {
  getConfig: async (): Promise<Config> => JSON.parse(await M.getConfig()),
  setConfig: (c: Config): Promise<void> => M.setConfig(JSON.stringify(c)),
  getHistory: async (): Promise<HistoryItem[]> => JSON.parse(await M.getHistory()),
  clearHistory: (): Promise<void> => M.clearHistory(),
  status: (): Promise<Status> => M.status(),
  openAccessibilitySettings: () => M.openAccessibilitySettings(),
  openOverlaySettings: () => M.openOverlaySettings(),
  openBatterySettings: () => M.openBatterySettings(),
  getInstalledApps: (): Promise<{pkg: string; label: string}[]> => M.getInstalledApps(),
  startService: () => M.startService(),
  stopService: () => M.stopService(),
};

export const events = new NativeEventEmitter(M);
