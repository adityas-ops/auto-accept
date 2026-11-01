export type Lang = 'en' | 'hi';

const en = {
  home: 'Home', rules: 'Rules', history: 'History', settings: 'Settings',
  autoAccept: 'Auto-accept rides', on: 'ON', off: 'OFF',
  setup: 'Setup', accessibility: 'Accessibility service', overlay: 'Floating bubble permission',
  battery: 'Ignore battery optimisation', grant: 'Grant', ok: 'Granted',
  minFare: 'Minimum fare (₹)', minPerKm: 'Minimum ₹ per km', maxPickup: 'Max pickup distance (km)',
  minTrip: 'Min trip distance (km)', maxTrip: 'Max trip distance (km)',
  zeroOff: '0 = no limit', bookingTypes: 'Booking types', bike: 'Bike', auto: 'Auto', cab: 'Cab', parcel: 'Parcel', boost: 'Boost / Saver',
  acceptUnknownFare: 'Accept rides without upfront fare (Rapido Bike)',
  acceptBoost: 'Accept Boost / Saver discounted rides',
  blocked: 'Blocked areas (comma separated)', delay: 'Tap delay (ms)',
  floating: 'Floating bubble', voice: 'Voice announcement', vibrate: 'Vibrate on accept',
  apps: 'Driver apps (package names, comma separated)', acceptLabels: 'Accept button labels',
  clear: 'Clear history', empty: 'No rides seen yet', accepted: 'Accepted', skipped: 'Skipped',
  allowed: 'Go-To areas — only accept rides mentioning these (comma separated)', test: 'Test Lab', pickApps: 'Watched apps', today: 'Today', rides: 'rides', earned: 'earned', testIntro: 'Practice without any driver app: turn on test mode, enable auto-accept on Home, then tap New request. The engine should tap Accept for you if the card passes your rules.', testMode: 'Test mode (watch RideAssist itself)', newReq: 'New request', engineTapped: 'Engine accepted this request', waiting: 'Waiting…', search: 'Search apps', language: 'Language', save: 'Save', saved: 'Saved',
  note: '0 means "no limit". Fare and distance are read from the ride card text, so check History after your first rides to confirm they parse correctly.',
};
export type Strings = typeof en;

const hi: Strings = {
  home: 'होम', rules: 'नियम', history: 'इतिहास', settings: 'सेटिंग्स',
  autoAccept: 'राइड ऑटो-एक्सेप्ट', on: 'चालू', off: 'बंद',
  setup: 'सेटअप', accessibility: 'एक्सेसिबिलिटी सर्विस', overlay: 'फ्लोटिंग बबल अनुमति',
  battery: 'बैटरी ऑप्टिमाइज़ेशन बंद करें', grant: 'अनुमति दें', ok: 'मिल गई',
  minFare: 'न्यूनतम किराया (₹)', minPerKm: 'न्यूनतम ₹ प्रति किमी', maxPickup: 'अधिकतम पिकअप दूरी (किमी)',
  minTrip: 'न्यूनतम सफ़र (किमी)', maxTrip: 'अधिकतम सफ़र (किमी)',
  zeroOff: '0 = कोई सीमा नहीं', bookingTypes: 'बुकिंग प्रकार', bike: 'बाइक', auto: 'ऑटो', cab: 'कैब', parcel: 'पार्सल', boost: 'बूस्ट / सेवर',
  acceptUnknownFare: 'बिना किराये वाली राइड लें (रैपिडो बाइक)',
  acceptBoost: 'बूस्ट / सेवर डिस्काउंट राइड लें',
  blocked: 'ब्लॉक इलाके (कॉमा से अलग)', delay: 'टैप देरी (ms)',
  floating: 'फ्लोटिंग बबल', voice: 'आवाज़ घोषणा', vibrate: 'एक्सेप्ट पर कंपन',
  apps: 'ड्राइवर ऐप (पैकेज नाम, कॉमा से अलग)', acceptLabels: 'एक्सेप्ट बटन के नाम',
  clear: 'इतिहास साफ़ करें', empty: 'अभी तक कोई राइड नहीं', accepted: 'स्वीकार', skipped: 'छोड़ी',
  allowed: 'गो-टू इलाके — सिर्फ़ इन्हें शामिल करने वाली राइड लें (कॉमा से अलग)', test: 'टेस्ट लैब', pickApps: 'निगरानी वाले ऐप', today: 'आज', rides: 'राइड', earned: 'कमाई', testIntro: 'बिना ड्राइवर ऐप के अभ्यास करें: टेस्ट मोड चालू करें, होम पर ऑटो-एक्सेप्ट चालू करें, फिर नई रिक्वेस्ट दबाएँ।', testMode: 'टेस्ट मोड (RideAssist खुद देखे)', newReq: 'नई रिक्वेस्ट', engineTapped: 'इंजन ने यह रिक्वेस्ट स्वीकार की', waiting: 'इंतज़ार…', search: 'ऐप खोजें', language: 'भाषा', save: 'सेव', saved: 'सेव हुआ',
  note: '0 का मतलब "कोई सीमा नहीं"। किराया और दूरी राइड कार्ड के टेक्स्ट से पढ़ी जाती है, इसलिए पहली राइड के बाद इतिहास में जाँच लें।',
};

export const dict: Record<Lang, Strings> = {en, hi};
