const { getDb } = require("../../lib/firebase");

function isAuthorized(req) {
  const secret = String(process.env.CRON_SECRET || "").trim();
  if (!secret) return false;
  return req.headers.authorization === "Bearer " + secret;
}

function isJobExpired(job, now) {
  const expiresAt = Number(job.expiresAt || 0);
  if (expiresAt > 0) return expiresAt <= now;

  const createdAt = Number(job.createdAt || 0);
  return createdAt > 0 && createdAt + 30 * 24 * 60 * 60 * 1000 <= now;
}

module.exports = async function handler(req, res) {
  if (req.method !== "GET") {
    return res.status(405).json({ success: false, error: "Method not allowed" });
  }

  if (!String(process.env.CRON_SECRET || "").trim()) {
    return res.status(503).json({ success: false, error: "CRON_SECRET is not configured" });
  }

  if (!isAuthorized(req)) {
    return res.status(401).json({ success: false, error: "Unauthorized" });
  }

  try {
    const db = getDb();
    const now = Date.now();
    const jobsSnap = await db.collection("jobs").get();

    let expiredJobs = 0;
    let expiredPromotions = 0;
    let notificationsCreated = 0;

    for (const doc of jobsSnap.docs) {
      const job = doc.data();
      const updates = {};

      if (job.isActive === true && isJobExpired(job, now)) {
        updates.isActive = false;
        updates.isExpired = true;
        updates.expiredAt = now;
        expiredJobs += 1;

        const expiresAt =
          Number(job.expiresAt || 0) ||
          Number(job.createdAt || 0) + 30 * 24 * 60 * 60 * 1000;

        if (job.ownerUid && expiresAt > 0) {
          const notificationRef = db
            .collection("notifications")
            .doc(doc.id + "-expired-" + expiresAt);

          const notificationSnap = await notificationRef.get();
          if (!notificationSnap.exists) {
            await notificationRef.set({
              targetUid: String(job.ownerUid),
              actorUid: "system",
              type: "job_expired",
              title: "انتهت مدة إعلانك",
              body: "انتهت مدة إعلان " + String(job.title || "الوظيفة") + " وتم إيقافه عن استقبال طلبات التقديم.",
              jobId: doc.id,
              applicationId: "",
              status: "expired",
              read: false,
              createdAt: now,
            });
            notificationsCreated += 1;
          }
        }
      }

      const promotionExpiresAt = Number(job.promotionExpiresAt || 0);
      if (
        job.promotionStatus === "active" &&
        promotionExpiresAt > 0 &&
        promotionExpiresAt <= now
      ) {
        updates.isFeatured = false;
        updates.promotionStatus = "expired";
        updates.promotionExpiredAt = now;
        expiredPromotions += 1;

        if (job.ownerUid) {
          const notificationRef = db
            .collection("notifications")
            .doc(doc.id + "-promotion-expired-" + promotionExpiresAt);

          const notificationSnap = await notificationRef.get();
          if (!notificationSnap.exists) {
            await notificationRef.set({
              targetUid: String(job.ownerUid),
              actorUid: "system",
              type: "promotion_expired",
              title: "انتهت ترقية إعلانك",
              body: "انتهت مدة ترويج إعلان " + String(job.title || "الوظيفة") + " وعاد إلى وضعه الطبيعي.",
              jobId: doc.id,
              applicationId: "",
              status: "expired",
              read: false,
              createdAt: now,
            });
            notificationsCreated += 1;
          }
        }
      }

      if (Object.keys(updates).length > 0) {
        await doc.ref.update(updates);
      }
    }

    return res.status(200).json({
      success: true,
      data: {
        processedJobs: jobsSnap.size,
        expiredJobs,
        expiredPromotions,
        notificationsCreated,
        ranAt: now,
      },
    });
  } catch (error) {
    return res.status(500).json({
      success: false,
      error: "Maintenance job failed",
    });
  }
};
