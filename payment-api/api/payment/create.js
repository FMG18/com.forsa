const admin = require("firebase-admin");
const { getFirebaseAdmin } = require("../../lib/firebase");
const { createPayment } = require("../../lib/payment");

function setCors(res) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");
  res.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
}

async function verifyUser(req) {
  const header = req.headers.authorization || "";
  if (!header.startsWith("Bearer ")) {
    throw Object.assign(new Error("Missing authorization"), { statusCode: 401 });
  }

  const token = header.slice("Bearer ".length).trim();
  if (!token) {
    throw Object.assign(new Error("Missing authorization"), { statusCode: 401 });
  }

  const app = getFirebaseAdmin();
  return admin.auth(app).verifyIdToken(token);
}

module.exports = async function handler(req, res) {
  setCors(res);

  if (req.method === "OPTIONS") return res.status(204).end();
  if (req.method !== "POST") {
    return res.status(405).json({ success: false, error: "Method not allowed" });
  }

  try {
    const decoded = await verifyUser(req);
    const jobId = String(req.body?.jobId || "");
    const planId = String(req.body?.planId || "");

    if (!jobId || !planId) {
      return res.status(400).json({
        success: false,
        error: "jobId and planId are required",
      });
    }

    const result = await createPayment({
      uid: decoded.uid,
      jobId,
      planId,
    });

    return res.status(200).json({ success: true, data: result });
  } catch (error) {
    const status = Number(error.statusCode) || 500;
    return res.status(status).json({
      success: false,
      error:
        status >= 500
          ? "Payment service is not configured or temporarily unavailable"
          : String(error.message || error),
    });
  }
};
