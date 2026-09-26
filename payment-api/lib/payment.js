const { getDb } = require("./firebase");
const { getPlan } = require("./plans");
const { initPayment, inquiry, getClaim, verifyCallbackToken } = require("./zaincash");
const crypto = require("crypto");

function publicBaseUrl() {
  const value = process.env.FORSA_PAYMENT_BASE_URL;
  if (!value) throw new Error("FORSA_PAYMENT_BASE_URL is not configured");
  return value.replace(/\/$/, "");
}

function deepLink() {
  return process.env.FORSA_PAYMENT_DEEP_LINK || "forsa://payment/result";
}

function normalizeStatus(value) {
  const status = String(value || "").trim().toUpperCase();
  if (status === "SUCCESS") return "paid";
  if (status === "FAILED" || status === "EXPIRED") return "failed";
  if (status === "REFUNDED") return "refunded";
  if (status === "PARTIALLY_REFUNDED") return "partially_refunded";
  return "pending";
}

async function createPayment({ uid, jobId, planId }) {
  const db = getDb();
  const plan = getPlan(planId);

  if (!plan) {
    throw Object.assign(new Error("Invalid promotion plan"), { statusCode: 400 });
  }

  const jobRef = db.collection("jobs").doc(jobId);
  const jobSnap = await jobRef.get();

  if (!jobSnap.exists) {
    throw Object.assign(new Error("Job not found"), { statusCode: 404 });
  }

  const job = jobSnap.data();
  if (job.ownerUid !== uid) {
    throw Object.assign(new Error("Job ownership check failed"), { statusCode: 403 });
  }

  const orderRef = db.collection("promotionOrders").doc();
  const orderId = orderRef.id;
  const externalReferenceId = crypto.randomUUID();

  const successUrl =
    publicBaseUrl() +
    "/api/payment/callback/success?orderId=" +
    encodeURIComponent(orderId);

  const failureUrl =
    publicBaseUrl() +
    "/api/payment/callback/failure?orderId=" +
    encodeURIComponent(orderId);

  await orderRef.set({
    ownerUid: uid,
    jobId,
    planId,
    amountIqd: plan.amountIqd,
    planTitle: plan.title,
    status: "payment_initializing",
    externalReferenceId,
    requestedAt: Date.now(),
  });

  try {
    const payment = await initPayment({
      orderId,
      externalReferenceId,
      amountIqd: plan.amountIqd,
      successUrl,
      failureUrl,
    });

    await orderRef.update({
      status: "pending_payment",
      transactionId: payment.transactionId,
      paymentRedirectUrl: payment.redirectUrl,
      gateway: "zaincash",
      updatedAt: Date.now(),
    });

    return {
      orderId,
      planId,
      amountIqd: plan.amountIqd,
      redirectUrl: payment.redirectUrl,
    };
  } catch (error) {
    await orderRef.update({
      status: "payment_init_failed",
      errorMessage: String(error.message || error).slice(0, 500),
      updatedAt: Date.now(),
    });
    throw error;
  }
}

async function reconcileOrder({ orderId, callbackPayload, callbackStatus }) {
  const db = getDb();
  const orderRef = db.collection("promotionOrders").doc(orderId);

  return db.runTransaction(async (tx) => {
    const orderSnap = await tx.get(orderRef);

    if (!orderSnap.exists) {
      throw Object.assign(new Error("Order not found"), { statusCode: 404 });
    }

    const order = orderSnap.data();

    const transactionId =
      getClaim(callbackPayload, ["transactionId"]) || order.transactionId || "";

    const externalReferenceId =
      getClaim(callbackPayload, ["merchantReferenceId", "externalReferenceId"]) ||
      order.externalReferenceId ||
      "";

    const callbackOrderId = getClaim(callbackPayload, ["orderId"]) || "";

    if (callbackOrderId && callbackOrderId !== orderId) {
      throw Object.assign(new Error("Callback order mismatch"), { statusCode: 400 });
    }

    if (
      order.transactionId &&
      transactionId &&
      order.transactionId !== transactionId
    ) {
      throw Object.assign(new Error("Callback transaction mismatch"), { statusCode: 400 });
    }

    if (
      order.externalReferenceId &&
      externalReferenceId &&
      order.externalReferenceId !== externalReferenceId
    ) {
      throw Object.assign(new Error("Callback reference mismatch"), { statusCode: 400 });
    }

    const gatewayStatus =
      getClaim(callbackPayload, ["currentStatus", "status"]) ||
      (callbackStatus === "success" ? "SUCCESS" : "FAILED");

    const normalized = normalizeStatus(gatewayStatus);
    const deepLinkBase = deepLink();

    const result = {
      status: normalized,
      jobId: order.jobId,
      planId: order.planId,
      transactionId,
      deepLink:
        deepLinkBase +
        "?orderId=" +
        encodeURIComponent(orderId) +
        "&status=" +
        encodeURIComponent(normalized),
    };

    if (normalized === "paid") {
      const jobRef = db.collection("jobs").doc(order.jobId);
      const jobSnap = await tx.get(jobRef);

      if (!jobSnap.exists) {
        throw Object.assign(new Error("Job not found"), { statusCode: 404 });
      }

      const job = jobSnap.data();
      if (job.ownerUid !== order.ownerUid) {
        throw Object.assign(new Error("Job ownership mismatch"), { statusCode: 403 });
      }

      const plan = getPlan(order.planId);
      if (!plan) {
        throw Object.assign(new Error("Invalid stored plan"), { statusCode: 400 });
      }

      const now = Date.now();
      const currentExpiry = Number(job.promotionExpiresAt || 0);
      const startsAt = Math.max(now, currentExpiry);
      const expiresAt = startsAt + plan.durationMs;

      tx.set(
        orderRef,
        {
          status: "paid",
          gatewayStatus,
          transactionId: transactionId || order.transactionId || "",
          externalReferenceId:
            externalReferenceId || order.externalReferenceId || "",
          paidAt: order.paidAt || now,
          promotionExpiresAt: expiresAt,
          updatedAt: now,
        },
        { merge: true }
      );

      tx.set(
        jobRef,
        {
          isFeatured: true,
          promotionType: order.planId,
          promotionStatus: "active",
          promotionExpiresAt: expiresAt,
        },
        { merge: true }
      );

      result.status = "paid";
      result.expiresAt = expiresAt;
      result.deepLink =
        deepLinkBase +
        "?orderId=" +
        encodeURIComponent(orderId) +
        "&status=success";
      return result;
    }

    tx.set(
      orderRef,
      {
        status: normalized,
        gatewayStatus,
        transactionId: transactionId || order.transactionId || "",
        externalReferenceId:
          externalReferenceId || order.externalReferenceId || "",
        updatedAt: Date.now(),
      },
      { merge: true }
    );

    return result;
  });
}

async function verifyAndReconcile({ orderId, token, callbackStatus }) {
  if (!token) {
    throw Object.assign(new Error("Missing callback token"), { statusCode: 400 });
  }

  const payload = await verifyCallbackToken(token);
  const payloadOrderId = getClaim(payload, ["orderId"]);

  if (payloadOrderId && payloadOrderId !== orderId) {
    throw Object.assign(new Error("Callback order mismatch"), { statusCode: 400 });
  }

  const result = await reconcileOrder({
    orderId,
    callbackPayload: payload,
    callbackStatus,
  });

  if (result.transactionId) {
    try {
      const inquiryBody = await inquiry(result.transactionId);
      const inquiryStatus =
        inquiryBody.currentStatus ||
        inquiryBody.status ||
        inquiryBody.data?.currentStatus ||
        inquiryBody.data?.status ||
        "";

      if (String(inquiryStatus).toUpperCase() === "SUCCESS" && result.status !== "paid") {
        const confirmed = await reconcileOrder({
          orderId,
          callbackPayload: {
            orderId,
            transactionId: result.transactionId,
            currentStatus: "SUCCESS",
          },
          callbackStatus: "success",
        });
        return confirmed;
      }
    } catch {
      // The verified callback remains usable if inquiry is temporarily unavailable.
    }
  }

  return result;
}

module.exports = {
  createPayment,
  reconcileOrder,
  verifyAndReconcile,
  publicBaseUrl,
  deepLink,
};
