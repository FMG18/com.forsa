const { verifyAndReconcile } = require("../../../lib/payment");

function html(result, title) {
  const target = result?.deepLink || "forsa://payment/result";
  const encoded = JSON.stringify(target).replace(/</g, "\u003c");
  return `<!doctype html>
<html lang="ar" dir="rtl">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>${title}</title>
<style>body{font-family:Arial,sans-serif;padding:40px;text-align:center;background:#f7f7fb}main{max-width:480px;margin:auto;background:#fff;padding:28px;border-radius:20px}a{display:inline-block;margin-top:18px;padding:13px 22px;background:#5b3cc4;color:#fff;text-decoration:none;border-radius:12px}</style>
</head>
<body><main>
<h1>${title}</h1>
<p>تم التحقق من نتيجة العملية.</p>
<a id="back" href="#">العودة إلى تطبيق فرصة</a>
<script>
const target=${encoded};
document.getElementById("back").href=target;
setTimeout(()=>location.href=target,400);
</script>
</main></body></html>`;
}

module.exports = async function handler(req, res) {
  const orderId = String(req.query?.orderId || "");
  const token = String(req.query?.token || "");

  if (!orderId || !token) {
    return res.status(400).send("Invalid payment callback");
  }

  try {
    const result = await verifyAndReconcile({
      orderId,
      token,
      callbackStatus: "success",
    });

    return res
      .status(200)
      .setHeader("Content-Type", "text/html; charset=utf-8")
      .send(
        html(
          result,
          result.status === "paid"
            ? "تم الدفع وتفعيل الترقية"
            : "تمت معالجة نتيجة الدفع"
        )
      );
  } catch (error) {
    return res.status(Number(error.statusCode) || 400).send("تعذر التحقق من عملية الدفع");
  }
};
