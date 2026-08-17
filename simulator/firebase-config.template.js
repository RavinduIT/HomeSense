// Copy this file to `firebase-config.js` and paste your project's web config.
// Firebase console -> Project settings -> Your apps -> Web app -> Config.
//
// `firebase-config.js` is gitignored. These values are not secrets in the
// usual sense - they identify the project, they do not authorise anything.
// What protects the data is `database.rules.json`, not this file.

window.HOMESENSE_CONFIG = {
  firebase: {
    apiKey: 'YOUR_WEB_API_KEY',
    authDomain: 'YOUR_PROJECT_ID.firebaseapp.com',
    databaseURL:
      'https://YOUR_PROJECT_ID-default-rtdb.asia-southeast1.firebasedatabase.app',
    projectId: 'YOUR_PROJECT_ID',
    storageBucket: 'YOUR_PROJECT_ID.appspot.com',
    messagingSenderId: '000000000000',
    appId: '1:000000000000:web:0000000000000000000000',
  },

  // Optional. The simulator signs in with the same account as the mobile
  // application and discovers the households that account belongs to, so this
  // is normally left null: it only pre-selects one when an account belongs to
  // several. A household identifier is generated when the household is created
  // in the application, so it cannot be known in advance.
  homeId: null,

  // How often each simulated node sends a heartbeat, in milliseconds.
  // The worker marks a node DISCONNECTED after 15s of silence, so this must
  // be comfortably under that.
  heartbeatMs: 5000,

  // How long the simulated relay takes to obey a command, in milliseconds.
  // Long enough to see the app's optimistic switch reconcile; short enough
  // that the demo does not drag.
  actuationMs: 400,
};
