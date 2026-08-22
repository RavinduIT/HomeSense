// Firebase configuration for the hardware simulator.
//
// These values identify the project; they do not authorise anything on their
// own. Access is governed by database.rules.json, which confines a household
// to its members, and by the account this page signs in with.

window.HOMESENSE_CONFIG = {
  firebase: {
    apiKey: 'AIzaSyAA0JtaSLjXt4Qwh069DmULoHMHbmv6Vmo',
    authDomain: 'smarthome-aa2f0.firebaseapp.com',
    databaseURL: 'https://smarthome-aa2f0-default-rtdb.firebaseio.com',
    projectId: 'smarthome-aa2f0',
    storageBucket: 'smarthome-aa2f0.firebasestorage.app',
    messagingSenderId: '612831885519',
    appId: '1:612831885519:android:2431fa2c0b0de7bd431fba',
  },

  // Optional pre-selection. The simulator signs in with the same account as the
  // application and discovers the households that account belongs to, so this
  // only matters when an account belongs to more than one. A household
  // identifier is generated when the household is created, so it cannot be
  // known before then; leaving it null is normal.
  homeId: null,

  // The worker marks a node DISCONNECTED after 15s of silence, so the heartbeat
  // interval must sit comfortably below that.
  heartbeatMs: 5000,

  // How long the simulated relay takes to obey a command. Long enough to watch
  // the application's optimistic switch reconcile, short enough not to drag.
  actuationMs: 400,
};
