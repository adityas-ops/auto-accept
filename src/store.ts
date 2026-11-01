import {create} from 'zustand';
import {Config, HistoryItem, Status, native} from './native';
import {Lang} from './i18n';

type State = {
  config: Config | null;
  history: HistoryItem[];
  status: Status;
  lang: Lang;
  load: () => Promise<void>;
  patch: (p: Partial<Config>) => Promise<void>;
  refreshStatus: () => Promise<void>;
  refreshHistory: () => Promise<void>;
  setLang: (l: Lang) => void;
};

export const useStore = create<State>((set, get) => ({
  config: null,
  history: [],
  status: {accessibility: false, overlay: false, battery: false},
  lang: 'en',
  load: async () => {
    set({config: await native.getConfig(), history: await native.getHistory()});
    await get().refreshStatus();
  },
  patch: async p => {
    const next = {...get().config!, ...p};
    set({config: next});
    await native.setConfig(next);
    if (next.enabled) native.startService();
  },
  refreshStatus: async () => set({status: await native.status()}),
  refreshHistory: async () => set({history: await native.getHistory()}),
  setLang: lang => set({lang}),
}));
