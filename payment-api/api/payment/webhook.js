const crypto = require("crypto");
const { getDb } = require("../../lib/firebase");
const { verifyCallbackToken, getClaim } = require("../../lib/zaincash");
const { reconcileOrder } = require("../../lib/payment");

module.exports = async function handler(req, res) {
  if (req.method !== "POST") {
    return res.status(405).json({ success: false, error: "Method not allowed" });
  }

  try {
    const token = String(req.body?.webhook_token || "");
    if (!token) {
      return res.status(400).json({ success: false, error: "Missing webhook_token" });
    }

    const payload = await verifyCallbackToken(token);
    const eventId =
      getClaim(payload, ["eventId"]) ||
      crypto.createHash("sha256").update(token).digest("hex");

    const db = getDb();
    const eventRef = db.collection("paymentWebhookEvents").doc(eventId);

    const result = await db.runTransaction(async (tx) => {
      const eventSnap = await tx.get(eventRef);
      if (eventSnap.exists) return { duplicate: true };

      tx.set(eventRef, {
        createdAt: Date.now(),
        eventType: getClaim(payload, ["eventType"]) || "STATUS_CHANGED",
        orderId: getClaim(payload, ["orderId"]),
      });
      return { duplicate: false };
    });

    if (result.duplicate) {
      return res.status(200).json({ success: true, duplicate: true });
    }

    const orderId = getClaim(payload, ["orderId"]);
    if (!orderId) {
      return res.status(400).json({ success: false, error: "Missing orderId" });
    }

    const reconciled = await reconcileOrder({
      orderId,
      callbackPayload: payload,
      callbackStatus: "success",
    });

    return res.status(200).json({
      success: true,
      duplicate: false,
      status: reconciled.status,
    });
  } catch (error) {
    return res.status(Number(error.statusCode) || 400).json({
      success: false,
      error: String(error.message || error),
    });
  }
};
