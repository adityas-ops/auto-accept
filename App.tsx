import React, {useEffect, useState} from 'react';
import {
  AppState, FlatList, Keyboard, KeyboardAvoidingView, PanResponder, Pressable, ScrollView, StatusBar, StyleSheet, Switch, Text, TextInput, View,
} from 'react-native';
import {SafeAreaProvider, SafeAreaView} from 'react-native-safe-area-context';
import {useStore} from './src/store';
import {dict} from './src/i18n';
import {events, native} from './src/native';

const C = {bg: '#0F1115', card: '#1A1D24', text: '#F2F3F5', mute: '#8A8F9A', green: '#2E7D32', red: '#C62828', accent: '#FFC400'};
const ICON: Record<string, string> = {home: '🏠', rules: '🎚️', activity: '⚡', test: '🧪', settings: '⚙️'};
type Tab = 'home' | 'rules' | 'activity' | 'test' | 'settings';

const DRIVER_APPS = [
  {pkg: 'com.rapido.rider', name: 'Rapido Captain', icon: '🟡'},
  {pkg: 'com.theporter.android.driverapp', name: 'Porter Partner', icon: '🔵'},
  {pkg: 'com.ubercab.driver', name: 'Uber Driver', icon: '⚫'},
  {pkg: 'com.olacabs.oladriver', name: 'Ola Driver', icon: '🟢'},
];

function QuickAppsCard({packages, onChange}: {packages: string[]; onChange: (pkgs: string[]) => void}) {
  return (
    <View style={s.card}>
      <Text style={s.h}>Monitored Driver Apps</Text>
      <Text style={s.mute}>Instant auto-accept for supported apps:</Text>
      {DRIVER_APPS.map(app => {
        const on = packages.includes(app.pkg);
        return (
          <View key={app.pkg} style={[s.row, {paddingVertical: 4}]}>
            <Text style={s.label}>{app.icon} {app.name}</Text>
            <Switch
              value={on}
              onValueChange={v => {
                onChange(v ? [...packages, app.pkg] : packages.filter(x => x !== app.pkg));
              }}
              trackColor={{true: C.green, false: '#444'}}
            />
          </View>
        );
      })}
    </View>
  );
}

function AppPicker({selected, onChange, label, placeholder}: {selected: string[]; onChange: (v: string[]) => void; label: string; placeholder: string}) {
  const [apps, setApps] = useState<{pkg: string; label: string}[]>([]);
  const [q, setQ] = useState('');
  useEffect(() => { native.getInstalledApps().then(setApps); }, []);
  const shown = apps.filter(a => a.label.toLowerCase().includes(q.toLowerCase()));
  return (
    <View style={s.card}>
      <Text style={s.label}>{label} ({selected.length})</Text>
      <TextInput style={s.input} value={q} onChangeText={setQ} placeholder={placeholder} placeholderTextColor={C.mute} />
      {shown.slice(0, 40).map(a => {
        const on = selected.includes(a.pkg);
        return (
          <Pressable key={a.pkg} style={[s.row, {paddingVertical: 6}]} onPress={() => onChange(on ? selected.filter(x => x !== a.pkg) : [...selected, a.pkg])}>
            <Text style={s.label}>{a.label}</Text>
            <View style={[s.plus, {backgroundColor: on ? C.green : C.accent}]}>
              <Text style={s.plusTxt}>{on ? '✓' : '+'}</Text>
            </View>
          </Pressable>
        );
      })}
    </View>
  );
}

function RulesScreen() {
  const {config, patch, lang} = useStore();
  const t = dict[lang];
  const c = config!;
  const [d, setD] = useState({
    minFare: String(c.minFare), minPerKm: String(c.minPerKm), maxPickupKm: String(c.maxPickupKm),
    minTripKm: String(c.minTripKm), maxTripKm: String(c.maxTripKm),
    allowed: c.allowedKeywords.join(', '), blocked: c.blockedKeywords.join(', '),
  });
  const [types, setTypes] = useState(c.bookingTypes);
  const [msg, setMsg] = useState('');
  const num = (v: string) => { const n = parseFloat(v); return Number.isFinite(n) && n > 0 ? n : 0; };
  const list = (v: string) => v.split(',').map(x => x.trim()).filter(Boolean);
  const field = (k: keyof typeof d, label: string, hint?: string, kbd: 'decimal-pad' | 'default' = 'decimal-pad') => (
    <View style={s.card}>
      <Text style={s.label}>{label}</Text>
      {hint ? <Text style={s.mute}>{hint}</Text> : null}
      <TextInput style={s.input} value={d[k]} keyboardType={kbd} placeholderTextColor={C.mute}
        onChangeText={v => { setMsg(''); setD({...d, [k]: v}); }} />
    </View>
  );
  const save = async () => {
    Keyboard.dismiss();
    await patch({
      minFare: num(d.minFare), minPerKm: num(d.minPerKm), maxPickupKm: num(d.maxPickupKm),
      minTripKm: num(d.minTripKm), maxTripKm: num(d.maxTripKm),
      allowedKeywords: list(d.allowed), blockedKeywords: list(d.blocked), bookingTypes: types,
    });
    const back = await native.getConfig();
    setMsg(`${t.saved}: ₹${back.minFare} min · pickup ≤ ${back.maxPickupKm || '∞'} km · trip ${back.minTripKm || 0}–${back.maxTripKm || '∞'} km`);
  };
  return (
    <View style={{flex: 1}}>
    <ScrollView contentContainerStyle={[s.pad, {paddingBottom: 150}]} keyboardShouldPersistTaps="handled" automaticallyAdjustKeyboardInsets>
      <Text style={s.mute}>{t.note}</Text>
      <Row label={t.acceptUnknownFare} value={config!.acceptUnknownFare} onChange={b => patch({acceptUnknownFare: b})} />
      <Row label={t.acceptBoost} value={config!.acceptBoost} onChange={b => patch({acceptBoost: b})} />
      {field('minFare', t.minFare, t.zeroOff)}
      {field('minPerKm', t.minPerKm, t.zeroOff)}
      {field('maxPickupKm', t.maxPickup, t.zeroOff)}
      {field('minTripKm', t.minTrip, t.zeroOff)}
      {field('maxTripKm', t.maxTrip, t.zeroOff)}
      <Text style={s.h}>{t.bookingTypes}</Text>
      {(['bike', 'auto', 'cab', 'parcel', 'boost'] as const).map(k => (
        <Row key={k} label={t[k]} value={types[k] ?? true} onChange={b => { setMsg(''); setTypes({...types, [k]: b}); }} />
      ))}
      {field('allowed', t.allowed, undefined, 'default')}
      {field('blocked', t.blocked, undefined, 'default')}
    </ScrollView>
    <View style={s.fab} pointerEvents="box-none">
      {msg ? <Text style={{color: C.green, backgroundColor: C.card, padding: 8, borderRadius: 8, marginBottom: 6}}>✓ {msg}</Text> : null}
      <Pressable style={[s.btn, {paddingVertical: 16, elevation: 6}]} onPress={save}><Text style={[s.btnTxt, {fontSize: 17}]}>{t.save}</Text></Pressable>
    </View>
    </View>
  );
}

function randomCard(rapidoMode: boolean = false) {
  const r = (a: number, b: number) => Math.round((a + Math.random() * (b - a)) * 10) / 10;
  const types = rapidoMode ? ['Bike', 'Auto', 'Bike Boost'] : ['Bike', 'Auto', 'Cab', 'Parcel'];
  const type = types[Math.floor(Math.random() * types.length)];
  const isRapidoBikeNoFare = rapidoMode && type === 'Bike';
  return {
    fare: isRapidoBikeNoFare ? null : Math.round(r(25, 400)),
    pickup: r(0.3, 6),
    trip: r(1, 25),
    type,
    area: ['Ashiyana - Phoenix United Mall', 'LDA Colony', 'Sector 72 Noida', 'Krishna Nagar', 'Airport'][Math.floor(Math.random() * 5)],
    dropArea: ['Krishna Nagar - Hari Om Nagar', 'Alambagh', 'Sector 18', 'Cyber City'][Math.floor(Math.random() * 4)],
  };
}

function SwipeAccept({onDone, label}: {onDone: () => void; label: string}) {
  const [w, setW] = useState(300);
  const [x, setX] = useState(0);
  const pan = React.useMemo(() => PanResponder.create({
    onStartShouldSetPanResponder: () => true,
    onMoveShouldSetPanResponder: () => true,
    onPanResponderMove: (_, g) => setX(Math.max(0, Math.min(g.dx, w - 56))),
    onPanResponderRelease: (_, g) => { if (g.dx > (w - 56) * 0.7) onDone(); setX(0); },
  }), [w, onDone]);
  return (
    <View style={{height: 56, borderRadius: 28, backgroundColor: '#0F1115', justifyContent: 'center', marginTop: 8}} importantForAccessibility="yes" onLayout={e => setW(e.nativeEvent.layout.width)}>
      <Text style={{color: C.mute, textAlign: 'center'}}>{label}</Text>
      <View {...pan.panHandlers} style={{position: 'absolute', left: x, width: 56, height: 56, borderRadius: 28, backgroundColor: C.accent, alignItems: 'center', justifyContent: 'center'}}>
        <Text style={{fontSize: 22}}>›</Text>
      </View>
    </View>
  );
}

function TestLab() {
  const {config, patch, lang} = useStore();
  const t = dict[lang];
  const [rapidoMode, setRapidoMode] = useState(true);
  const [card, setCard] = useState(randomCard(true));
  const [tapped, setTapped] = useState(false);
  const [slider, setSlider] = useState(false);
  const next = () => { setTapped(false); setCard(randomCard(rapidoMode)); };
  return (
    <ScrollView contentContainerStyle={s.pad} keyboardShouldPersistTaps="handled" automaticallyAdjustKeyboardInsets>
      <Text style={s.mute}>{t.testIntro}</Text>
      <Row label={t.testMode} value={config!.testMode} onChange={b => patch({testMode: b})} />
      <Row label="Rapido card style" value={rapidoMode} onChange={b => { setRapidoMode(b); setTapped(false); setCard(randomCard(b)); }} />
      <Row label="Slider style" value={slider} onChange={b => { setSlider(b); setTapped(false); }} />
      <View style={s.card} importantForAccessibility="yes">
        <Text style={s.h}>{card.type}</Text>
        {card.fare != null ? <Text style={s.bigState}>₹{card.fare}</Text> : null}
        <Text style={s.label}>{rapidoMode ? `• ${card.pickup} Km` : `${card.pickup} km away`}</Text>
        <Text style={s.mute}>{card.area}</Text>
        <Text style={s.label}>{rapidoMode ? `▼ ${card.trip} Km` : `${card.trip} km trip`}</Text>
        {card.dropArea ? <Text style={s.mute}>{card.dropArea}</Text> : null}
        {slider ? (
          <SwipeAccept label="Swipe to accept" onDone={() => setTapped(true)} />
        ) : (
          <Pressable accessible accessibilityRole="button" accessibilityLabel="Accept in 7s" style={[s.btn, {marginTop: 8}]} onPress={() => setTapped(true)}>
            <Text style={s.btnTxt}>Accept in 7s</Text>
          </Pressable>
        )}
        <Text style={{color: tapped ? C.green : C.mute, marginTop: 6}}>{tapped ? '✓ ' + t.engineTapped : t.waiting}</Text>
      </View>
      <Pressable style={s.btn} onPress={next}><Text style={s.btnTxt}>{t.newReq}</Text></Pressable>
    </ScrollView>
  );
}

function ActivityScreen() {
  const {history, refreshHistory, lang} = useStore();
  const t = dict[lang];
  const [filter, setFilter] = useState<'all' | 'accepted' | 'declined' | 'detected'>('all');
  const [liveDebug, setLiveDebug] = useState(false);
  const [expandedKey, setExpandedKey] = useState<string | null>(null);

  const filtered = history.filter(item => {
    const isAccepted = item.accepted;
    const isDetected = item.note.toLowerCase().includes('watching') || item.note.toLowerCase().includes('detected');
    const isDeclined = !isAccepted && !isDetected;
    if (filter === 'accepted') return isAccepted;
    if (filter === 'declined') return isDeclined;
    if (filter === 'detected') return isDetected;
    return true;
  });

  return (
    <View style={{flex: 1}}>
      <View style={[s.pad, {paddingBottom: 4}]}>
        <View style={s.row}>
          <Text style={s.h}>{t.activity} ({history.length})</Text>
          <Pressable style={s.btn} onPress={async () => { await native.clearHistory(); refreshHistory(); }}>
            <Text style={s.btnTxt}>{t.clear}</Text>
          </Pressable>
        </View>

        {/* Filter Pills */}
        <View style={{flexDirection: 'row', gap: 8, marginTop: 8}}>
          {(['all', 'accepted', 'declined', 'detected'] as const).map(f => {
            const active = filter === f;
            return (
              <Pressable
                key={f}
                style={[
                  s.pill,
                  active && {backgroundColor: C.accent},
                ]}
                onPress={() => setFilter(f)}>
                <Text style={[s.pillTxt, active && {color: '#000', fontWeight: '700'}]}>
                  {t[f]}
                </Text>
              </Pressable>
            );
          })}
        </View>

        {/* Live Debug Toggle */}
        <View style={[s.row, {marginTop: 6}]}>
          <Text style={s.mute}>{t.liveDebug}</Text>
          <Switch value={liveDebug} onValueChange={setLiveDebug} trackColor={{true: C.green, false: '#444'}} />
        </View>
      </View>

      <FlatList
        data={filtered}
        keyExtractor={(h, i) => `${h.time}-${i}`}
        contentContainerStyle={s.pad}
        ListEmptyComponent={
          <View style={[s.card, {alignItems: 'center', paddingVertical: 32}]}>
            <Text style={{fontSize: 32, marginBottom: 8}}>⚡</Text>
            <Text style={s.label}>{t.empty}</Text>
            <Text style={[s.mute, {textAlign: 'center', marginTop: 4}]}>
              Open Rapido, Porter, Uber, or Ola. Any detected screens, incoming orders, and rules applied will appear here live.
            </Text>
          </View>
        }
        renderItem={({item, index}) => {
          const isAccepted = item.accepted;
          const isDetected = item.note.toLowerCase().includes('watching') || item.note.toLowerCase().includes('detected');
          const isFailed = item.note.toLowerCase().includes('failed');
          const badgeColor = isAccepted ? C.green : isFailed ? C.red : isDetected ? '#3B82F6' : '#F59E0B';
          const badgeText = isAccepted ? `✓ ${t.accepted}` : isFailed ? '⚠️ Failed' : isDetected ? `👁️ ${t.detected}` : `✕ ${t.declined}`;
          const itemKey = `${item.time}-${index}`;
          const isExpanded = expandedKey === itemKey || liveDebug;

          return (
            <Pressable
              style={s.card}
              onPress={() => setExpandedKey(expandedKey === itemKey ? null : itemKey)}>
              <View style={s.row}>
                <View style={{flexDirection: 'row', alignItems: 'center', gap: 6}}>
                  <View style={[s.statusBadge, {backgroundColor: badgeColor}]}>
                    <Text style={s.statusBadgeTxt}>{badgeText}</Text>
                  </View>
                  <Text style={[s.label, {fontWeight: '700'}]}>{item.appName || item.pkg.split('.').pop()}</Text>
                </View>
                <Text style={s.mute}>{new Date(item.time).toLocaleTimeString()}</Text>
              </View>

              {(item.fare != null || item.pickupKm != null || item.tripKm != null || item.type) ? (
                <Text style={[s.label, {fontSize: 16, fontWeight: '600'}]}>
                  {item.fare != null ? `₹${item.fare} · ` : ''}
                  {item.type ? `${item.type} · ` : ''}
                  {item.pickupKm != null ? `${item.pickupKm} km pickup · ` : ''}
                  {item.tripKm != null ? `${item.tripKm} km trip` : ''}
                </Text>
              ) : null}

              <Text style={{color: isAccepted ? C.green : isDetected ? '#93C5FD' : '#FCD34D', fontSize: 14}}>
                {item.note}
              </Text>

              {isExpanded && item.rawText ? (
                <View style={{marginTop: 6, backgroundColor: '#0F1115', padding: 8, borderRadius: 6}}>
                  <Text style={[s.mute, {fontSize: 11, fontWeight: '700', marginBottom: 2}]}>{t.rawDetails}:</Text>
                  <Text style={{color: '#9CA3AF', fontSize: 12, fontFamily: 'monospace'}}>{item.rawText}</Text>
                </View>
              ) : null}
            </Pressable>
          );
        }}
      />
    </View>
  );
}

function NumField({label, hint, value, onChange}: {label: string; hint?: string; value: number; onChange: (n: number) => void}) {
  const [txt, setTxt] = useState(String(value));
  return (
    <View style={s.card}>
      <Text style={s.label}>{label}</Text>
      {hint ? <Text style={s.mute}>{hint}</Text> : null}
      <TextInput
        style={s.input} keyboardType="decimal-pad" value={txt} placeholderTextColor={C.mute}
        onChangeText={t => {
          setTxt(t);
          const n = parseFloat(t);
          onChange(Number.isFinite(n) ? n : 0);
        }}
      />
    </View>
  );
}

function ListField({label, value, onChange}: {label: string; value: string[]; onChange: (v: string[]) => void}) {
  const [txt, setTxt] = useState(value.join(', '));
  return (
    <View style={s.card}>
      <Text style={s.label}>{label}</Text>
      <TextInput
        style={s.input} value={txt} multiline
        onChangeText={t => {
          setTxt(t);
          onChange(t.split(',').map(x => x.trim()).filter(Boolean));
        }}
      />
    </View>
  );
}

function Row({label, value, onChange}: {label: string; value: boolean; onChange: (b: boolean) => void}) {
  return (
    <View style={[s.card, s.row]}>
      <Text style={s.label}>{label}</Text>
      <Switch value={value} onValueChange={onChange} trackColor={{true: C.green, false: '#444'}} />
    </View>
  );
}

export default function Root() {
  return (
    <SafeAreaProvider>
      <App />
    </SafeAreaProvider>
  );
}

function App() {
  const {config, history, status, lang, load, patch, refreshStatus, refreshHistory, setLang} = useStore();
  const [tab, setTab] = useState<Tab>('home');
  const t = dict[lang];
  const [kb, setKb] = useState(false);

  useEffect(() => {
    const a = Keyboard.addListener('keyboardDidShow', () => setKb(true));
    const b = Keyboard.addListener('keyboardDidHide', () => setKb(false));
    return () => { a.remove(); b.remove(); };
  }, []);

  useEffect(() => {
    load();
    const sub = AppState.addEventListener('change', st => st === 'active' && (refreshStatus(), refreshHistory()));
    const ride = events.addListener('ride', refreshHistory);
    const cfg = events.addListener('config', () => load());
    return () => { sub.remove(); ride.remove(); cfg.remove(); };
  }, [load, refreshStatus, refreshHistory]);

  if (!config) return <SafeAreaView style={s.root} />;
  const ready = status.accessibility && status.overlay;
  const startOfDay = new Date().setHours(0, 0, 0, 0);
  const todays = history.filter(h => h.accepted && h.time >= startOfDay);
  const earned = todays.reduce((a, h) => a + (h.fare ?? 0), 0);

  const home = (
    <ScrollView contentContainerStyle={s.pad} keyboardShouldPersistTaps="handled" automaticallyAdjustKeyboardInsets>
      <Pressable
        style={[s.big, {backgroundColor: config.enabled ? C.green : C.red}]}
        onPress={() => patch({enabled: !config.enabled})}>
        <Text style={s.bigTxt}>{t.autoAccept}</Text>
        <Text style={s.bigState}>{config.enabled ? t.on : t.off}</Text>
      </Pressable>
      <View style={[s.card, s.row]}>
        <Text style={s.label}>{t.today}</Text>
        <Text style={s.label}>{todays.length} {t.rides} · ₹{Math.round(earned)} {t.earned}</Text>
      </View>

      <QuickAppsCard packages={config.packages} onChange={pkgs => patch({packages: pkgs})} />

      <Text style={s.h}>{t.setup}</Text>
      {([
        ['accessibility', status.accessibility, native.openAccessibilitySettings],
        ['overlay', status.overlay, native.openOverlaySettings],
        ['battery', status.battery, native.openBatterySettings],
      ] as const).map(([k, ok, open]) => (
        <View key={k} style={[s.card, s.row]}>
          <Text style={s.label}>{t[k]}</Text>
          {ok ? <Text style={{color: C.green}}>✓ {t.ok}</Text> : (
            <Pressable style={s.btn} onPress={open}><Text style={s.btnTxt}>{t.grant}</Text></Pressable>
          )}
        </View>
      ))}
      {!ready && <Text style={s.mute}>Accessibility + overlay are required.</Text>}
    </ScrollView>
  );

  const rules = <RulesScreen />;
  const activity = <ActivityScreen />;

  const settings = (
    <ScrollView contentContainerStyle={s.pad} keyboardShouldPersistTaps="handled" automaticallyAdjustKeyboardInsets>
      <View style={[s.card, s.row]}>
        <Text style={s.label}>{t.language}</Text>
        <Pressable style={s.btn} onPress={() => setLang(lang === 'en' ? 'hi' : 'en')}>
          <Text style={s.btnTxt}>{lang === 'en' ? 'हिन्दी' : 'English'}</Text>
        </Pressable>
      </View>
      <Row label={t.floating} value={config.overlay} onChange={b => patch({overlay: b})} />
      <Row label={t.voice} value={config.voice} onChange={b => patch({voice: b})} />
      <Row label={t.vibrate} value={config.vibrate} onChange={b => patch({vibrate: b})} />
      <NumField label={t.delay} value={config.delayMs} onChange={n => patch({delayMs: n})} />
      <AppPicker label={t.pickApps} placeholder={t.search} selected={config.packages} onChange={v => patch({packages: v})} />
      <ListField label={t.acceptLabels} value={config.acceptTexts} onChange={v => patch({acceptTexts: v})} />
    </ScrollView>
  );

  const body = {home, rules, activity, test: <TestLab />, settings}[tab];
  return (
    <SafeAreaView style={s.root}>
      <StatusBar barStyle="light-content" />
      <KeyboardAvoidingView style={{flex: 1}} behavior="padding">
        <View style={{flex: 1}}>{body}</View>
        {!kb && (
          <View style={s.tabs}>
            {(['home', 'rules', 'activity', 'test', 'settings'] as Tab[]).map(k => (
              <Pressable key={k} style={s.tab} onPress={() => setTab(k)}>
                <Text style={{fontSize: 20}}>{ICON[k]}</Text>
                <Text style={{color: tab === k ? C.accent : C.mute, fontWeight: '600', fontSize: 12}}>{t[k]}</Text>
              </Pressable>
            ))}
          </View>
        )}
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const s = StyleSheet.create({
  root: {flex: 1, backgroundColor: C.bg},
  pad: {padding: 16, gap: 12},
  card: {backgroundColor: C.card, borderRadius: 12, padding: 14, gap: 6},
  row: {flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between'},
  label: {color: C.text, fontSize: 15, flexShrink: 1},
  mute: {color: C.mute, fontSize: 13},
  h: {color: C.text, fontSize: 18, fontWeight: '700', marginTop: 8},
  input: {backgroundColor: '#0F1115', color: C.text, borderRadius: 8, padding: 10, fontSize: 16},
  big: {borderRadius: 20, padding: 28, alignItems: 'center'},
  bigTxt: {color: '#fff', fontSize: 16},
  bigState: {color: '#fff', fontSize: 40, fontWeight: '800'},
  plus: {width: 44, height: 44, borderRadius: 22, alignItems: 'center', justifyContent: 'center'},
  plusTxt: {color: '#000', fontSize: 26, fontWeight: '800'},
  fab: {position: 'absolute', left: 16, right: 16, bottom: 12},
  btn: {backgroundColor: C.accent, borderRadius: 8, paddingHorizontal: 14, paddingVertical: 8, alignItems: 'center'},
  btnTxt: {color: '#000', fontWeight: '700'},
  tabs: {flexDirection: 'row', backgroundColor: C.card},
  tab: {flex: 1, alignItems: 'center', paddingVertical: 8, gap: 2},
  pill: {paddingHorizontal: 12, paddingVertical: 6, borderRadius: 16, backgroundColor: '#262A34'},
  pillTxt: {color: C.mute, fontSize: 13, fontWeight: '500'},
  statusBadge: {paddingHorizontal: 6, paddingVertical: 2, borderRadius: 4},
  statusBadgeTxt: {color: '#fff', fontSize: 11, fontWeight: '700'},
});
