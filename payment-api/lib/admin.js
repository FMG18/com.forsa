const { getFirebaseAdmin, getDb } = require("./firebase");

function bearerToken(req) {
  const header = String(req.headers.authorization || "");
  if (!header.startsWith("Bearer ")) return "";
  return header.slice(7).trim();
}

function adminUidSet() {
  return new Set(
    String(process.env.ADMIN_UIDS || "")
      .split(",")
      .map((value) => value.trim())
      .filter(Boolean)
  );
}

async function requireAdmin(req) {
  const token = bearerToken(req);
  if (!token) {
    throw Object.assign(new Error("Missing authorization token"), {
      statusCode: 401,
    });
  }

  const decoded = await getFirebaseAdmin().auth().verifyIdToken(token);
  if (!adminUidSet().has(decoded.uid)) {
    throw Object.assign(new Error("Admin access required"), {
      statusCode: 403,
    });
  }

  return decoded;
}

module.exports = { getDb, requireAdmin };
