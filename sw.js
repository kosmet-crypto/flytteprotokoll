/* Flytteprotokoll service worker: gjør appen tilgjengelig uten nett.
   Øk VERSION når index.html, app.css, vendor/ eller ikonene endres. */
const VERSION = 'fp-v5';
const SHELL = [
  './',
  './index.html',
  './app.css',
  './manifest.json',
  './vendor/alpine.min.js',
  './vendor/alpine-collapse.min.js',
  './vendor/signature_pad.min.js',
  './vendor/jspdf.min.js',
  './vendor/jspdf.autotable.min.js',
  './icons/icon-192.png',
  './icons/icon-512.png',
  './icons/icon-maskable-512.png',
  './icons/apple-touch-icon.png'
];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(VERSION).then(c => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', e => {
  e.waitUntil(
    caches.keys()
      .then(keys => Promise.all(keys.filter(k => k !== VERSION).map(k => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET' || new URL(req.url).origin !== self.location.origin) return;

  // Sidelasting: nett først så oppdateringer kommer med, lagret kopi uten nett.
  if (req.mode === 'navigate') {
    e.respondWith(
      fetch(req).then(res => {
        if (res.ok) { const copy = res.clone(); caches.open(VERSION).then(c => c.put('./index.html', copy)); }
        return res;
      }).catch(() => caches.match('./index.html'))
    );
    return;
  }

  // Øvrige filer: lagret kopi først.
  e.respondWith(caches.match(req).then(hit => hit || fetch(req)));
});
