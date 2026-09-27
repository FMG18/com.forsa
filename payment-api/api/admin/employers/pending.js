const { getDb, requireAdmin } = require("../../lib/admin");

module.exports = async function handler(req, res) {
  if (req.method !== "GET") {
    return res.status(405).json({ success: false, error: "Method not allowed" });
  }

  try {
    await requireAdmin(req);
    const snap = await getDb()
      .collection("users")
      .where("role", "==", "صاحب عمل")
      .where("verificationStatus", "==", "pending")
      .limit(100)
      .get();

    return res.status(200).json({
      success: true,
      data: snap.docs.map((doc) => ({
        uid: doc.id,
        displayName: doc.get("displayName") || "",
        email: doc.get("email") || "",
        phone: doc.get("phone") || "",
        city: doc.get("city") || "",
        companyName: doc.get("companyName") || "",
        companyAbout: doc.get("companyAbout") || "",
        companyCity: doc.get("companyCity") || "",
        verificationRequestedAt: doc.get("verificationRequestedAt") || 0,
        verificationStatus: doc.get("verificationStatus") || "pending",
        verificationDocumentName: doc.get("verificationDocumentName") || "",
        hasVerificationDocument: Boolean(doc.get("verificationDocumentPath")),
      })),
    });
  } catch (error) {
    const status = Number(error.statusCode || 500);
    return res.status(status).json({
      success: false,
      error: status === 401 || status === 403 ? "Unauthorized" : "Failed to load pending employers",
    });
  }
};
