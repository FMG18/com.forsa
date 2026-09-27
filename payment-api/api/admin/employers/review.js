const { getDb, requireAdmin } = require("../../lib/admin");

function clean(value) {
  return String(value ?? "").trim();
}

module.exports = async function handler(req, res) {
  if (req.method !== "POST") {
    return res.status(405).json({ success: false, error: "Method not allowed" });
  }

  try {
    const admin = await requireAdmin(req);
    const uid = clean(req.body?.uid);
    const decision = clean(req.body?.decision).toLowerCase();
    const note = clean(req.body?.note);

    if (!uid) {
      return res.status(400).json({ success: false, error: "uid is required" });
    }

    if (!["verify", "reject"].includes(decision)) {
      return res.status(400).json({
        success: false,
        error: "decision must be verify or reject",
      });
    }

    const db = getDb();
    const userRef = db.collection("users").doc(uid);
    const userSnap = await userRef.get();

    if (!userSnap.exists) {
      return res.status(404).json({ success: false, error: "Employer not found" });
    }

    const user = userSnap.data();
    if (
      user.role !== "صاحب عمل" ||
      user.roleConfirmed !== true
    ) {
      return res.status(400).json({
        success: false,
        error: "Account is not a confirmed employer",
      });
    }

    const nextStatus = decision === "verify" ? "verified" : "rejected";
    const now = Date.now();

    await userRef.update({
      verificationStatus: nextStatus,
      verificationUpdatedAt: now,
      verificationNote: note,
    });

    const notificationId =
      uid + "-verification-" + now + "-" + nextStatus;

    await db.collection("notifications").doc(notificationId).set({
      targetUid: uid,
      actorUid: "admin:" + admin.uid,
      type: "employer_verification",
      title: decision === "verify"
        ? "تم توثيق حساب صاحب العمل"
        : "تم رفض طلب توثيق صاحب العمل",
      body: note || (
        decision === "verify"
          ? "تم اعتماد حسابك كصاحب عمل موثّق."
          : "تم رفض طلب التوثيق. راجع بيانات الشركة وأعد إرسال الطلب."
      ),
      jobId: "",
      applicationId: "",
      status: nextStatus,
      read: false,
      createdAt: now,
    });

    return res.status(200).json({
      success: true,
      data: {
        uid,
        verificationStatus: nextStatus,
        verificationUpdatedAt: now,
      },
    });
  } catch (error) {
    const status = Number(error.statusCode || 500);
    return res.status(status).json({
      success: false,
      error: status === 401 || status === 403
        ? "Unauthorized"
        : "Failed to update employer verification",
    });
  }
};
