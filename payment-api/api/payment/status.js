const admin = require("firebase-admin");
const { getFirebaseAdmin, getDb } = require("../../lib/firebase");

function setCors(res) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");
  res.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
}

async function verifyUser(req) {
  const header = req.headers.authorization || "";
  if (!header.startsWith("Bearer ")) {
    throw Object.assign(new Error("Missing authorization"), { statusCode: 401 });
  }

  const token = header.slice("Bearer ".length).trim();
  return admin.auth(getFirebaseAdmin()).verifyIdToken(token);
}

module.exports = async function handler(req, res) {
  setCors(res);

  if (req.method === "OPTIONS") return res.status(204).end();
  if (req.method !== "GET") {
    return res.status(405).json({ success: false, error: "Method not allowed" });
  }

  try {
    const decoded = await verifyUser(req);
    const orderId = String(req.query?.orderId || "");
    if (!orderId) {
      return res.status(400).json({ success: false, error: "orderId is required" });
    }

    const snap = await getDb().collection("promotionOrders").doc(orderId).get();
    if (!snap.exists) {
      return res.status(404).json({ success: false, error: "Order not found" });
    }

    const order = snap.data();
    if (order.ownerUid !== decoded.uid) {
      return res.status(403).json({ success: false, error: "Forbidden" });
    }

    return res.status(200).json({
      success: true,
      data: {
        orderId,
        status: order.status || "pending",
        jobId: order.jobId || "",
        planId: order.planId || "",
        amountIqd: order.amountIqd || 0,
        promotionExpiresAt: order.promotionExpiresAt || 0,
      },
    });
  } catch (error) {
    const status = Number(error.statusCode) || 500;
    return res.status(status).json({
      success: false,
      error:
        status >= 500
          ? "Payment status is temporarily unavailable"
          : String(error.message || error),
    });
  }
};
