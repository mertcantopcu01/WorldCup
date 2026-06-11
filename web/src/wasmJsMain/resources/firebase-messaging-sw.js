// Firebase Messaging Service Worker
// Bu dosya, web uygulaması kapalıyken arka planda bildirimleri almak için tarayıcı tarafından zorunlu tutulur.

importScripts('https://www.gstatic.com/firebasejs/10.14.0/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/10.14.0/firebase-messaging-compat.js');

importScripts('config.js');

if (self.firebaseConfig) {
  firebase.initializeApp(self.firebaseConfig);
}

const messaging = firebase.messaging();

messaging.onBackgroundMessage((payload) => {
  console.log('[firebase-messaging-sw.js] Arka plan bildirimi alındı: ', payload);
  
  const notificationTitle = payload.notification?.title || 'Dünya Kupası 2026';
  const notificationOptions = {
    body: payload.notification?.body || '',
    icon: '/favicon.ico' // Varsa logonuzun yolu
  };

  self.registration.showNotification(notificationTitle, notificationOptions);
});
