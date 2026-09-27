const admin = require("firebase-admin");
const { getFirebaseAdmin, getDb } = require("../../lib/firebase");
const { getClaim } = require("../../lib/zaincash");
const { inquiry, reconcileOrder } = require("../../lib/payment");

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

    let resolvedStatus = order.status || "pending";
    let promotionExpiresAt = Number(order.promotionExpiresAt || 0);

    if (
      (resolvedStatus === "pending_payment" || resolvedStatus === "payment_initializing") &&
      order.transactionId
    ) {
      try {
        const inquiryBody = await inquiry(order.transactionId);
        const gatewayStatus =
          inquiryBody.currentStatus ||
          inquiryBody.status ||
          inquiryBody.data?.currentStatus ||
          inquiryBody.data?.status ||
          "";

        if (String(gatewayStatus).trim()) {
          const normalized = String(gatewayStatus).trim().toUpperCase();
          if (normalized === "SUCCESS" || normalized === "FAILED" || normalized === "EXPIRED" || normalized === "REFUNDED" || normalized === "PARTIALLY_REFUNDED") {
            const reconciled = await reconcileOrder({
              orderId,
              callbackPayload: {
                orderId,
                transactionId: order.transactionId,
                currentStatus: normalized,
              },
              callbackStatus: normalized === "SUCCESS" ? "success" : "failure",
            });
            resolvedStatus = reconciled.status || resolvedStatus;
            promotionExpiresAt = Number(reconciled.expiresAt || order.promotionExpiresAt || 0);
          }
        }
      } catch {
        // Keep the stored order status when the gateway is temporarily unavailable.
      }
    }

    let promotionActive = resolvedStatus === "paid" && promotionExpiresAt > Date.now();

    if (
      resolvedStatus === "paid" &&
      promotionExpiresAt > 0 &&
      promotionExpiresAt <= Date.now()
    ) {
      const jobRef = getDb().collection("jobs").doc(String(order.jobId || ""));
      const jobSnap = await jobRef.get();

      if (
        jobSnap.exists &&
        jobSnap.data().ownerUid === decoded.uid &&
        jobSnap.data().promotionExpiresAt === promotionExpiresAt
      ) {
        await jobRef.update({
          isFeatured: false,
          promotionStatus: "expired",
        });
      }

      promotionActive = false;
    }

    return res.status(200).json({
      success: true,
      data: {
        orderId,
        status: resolvedStatus || "pending",
        jobId: order.jobId || "",
        planId: order.planId || "",
        amountIqd: order.amountIqd || 0,
        promotionExpiresAt,
        promotionActive,
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
