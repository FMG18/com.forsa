const admin = require("firebase-admin");

function getFirebaseAdmin() {
  if (admin.apps.length) {
    return admin.app();
  }

  const raw = process.env.FORSA_FIREBASE_SERVICE_ACCOUNT_JSON;
  if (!raw) {
    throw new Error("FORSA_FIREBASE_SERVICE_ACCOUNT_JSON is not configured");
  }

  const serviceAccount = JSON.parse(raw);

  return admin.initializeApp({
    credential: admin.credential.cert(serviceAccount),
  });
}

function getDb() {
  return getFirebaseAdmin().firestore();
}

module.exports = { getFirebaseAdmin, getDb };
