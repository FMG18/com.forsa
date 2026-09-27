const { getFirebaseAdmin, getDb, requireAdmin } = require("../../lib/admin");

function clean(value) {
  return String(value ?? "").trim();
}

module.exports = async function handler(req, res) {
  if (req.method !== "GET") {
    return res.status(405).json({ success: false, error: "Method not allowed" });
  }

  try {
    await requireAdmin(req);
    const uid = clean(req.query?.uid);
    if (!uid) {
      return res.status(400).json({ success: false, error: "uid is required" });
    }

    const snap = await getDb().collection("users").doc(uid).get();
    if (!snap.exists) {
      return res.status(404).json({ success: false, error: "Employer not found" });
    }

    const user = snap.data() || {};
    if (user.role !== "صاحب عمل") {
      return res.status(400).json({ success: false, error: "Account is not an employer" });
    }

    const path = clean(user.verificationDocumentPath);
    if (!path || !path.startsWith("employerVerificationDocs/" + uid + "/")) {
      return res.status(404).json({ success: false, error: "Verification document not found" });
    }

    const file = getFirebaseAdmin().storage().bucket().file(path);
    const [exists] = await file.exists();
    if (!exists) {
      return res.status(404).json({ success: false, error: "Verification document file not found" });
    }

    const [url] = await file.getSignedUrl({
      version: "v4",
      action: "read",
      expires: Date.now() + 10 * 60 * 1000,
    });

    return res.status(200).json({
      success: true,
      data: {
        url,
        fileName: user.verificationDocumentName || path.split("/").pop(),
        expiresInSeconds: 600,
      },
    });
  } catch (error) {
    const status = Number(error.statusCode || 500);
    return res.status(status).json({
      success: false,
      error: status === 401 || status === 403 ? "Unauthorized" : "Failed to load verification document",
    });
  }
};
