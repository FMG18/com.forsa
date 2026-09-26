# Forsa Payment API

هذا المجلد يحتوي خدمة الدفع التي يستعملها تطبيق فرصة مع ZainCash.

المسارات:
- GET /api/health
- POST /api/payment/create
- GET /api/payment/status?orderId=...
- GET /api/payment/callback/success?orderId=...&token=...
- GET /api/payment/callback/failure?orderId=...&token=...
- POST /api/payment/webhook

متغيرات البيئة موجودة في payment-api/.env.example.

قواعد مهمة:
1. client_secret و API key لا يوضعان داخل Android.
2. الخادم يتحقق من Firebase ID token قبل إنشاء طلب دفع.
3. الخادم يحسب سعر الخطة من planId ولا يثق بالسعر القادم من التطبيق.
4. callback و webhook يتم التحقق من JWT بواسطة API key.
5. عند نجاح الدفع فقط يتم تحديث promotionOrders وتفعيل الإعلان المميز.
6. Webhook الإنتاج يجب أن يكون مختلفاً عن success/failure، وتسجيله يتم مع فريق ZainCash.
7. استعمل UAT أولاً ثم بدّل إلى Production credentials بعد اعتماد الحساب.

بعد الموافقة، القيم التي ستضاف إلى بيئة الخادم هي:
ZAINCASH_BASE_URL
ZAINCASH_CLIENT_ID
ZAINCASH_CLIENT_SECRET
ZAINCASH_API_KEY
ZAINCASH_SCOPE
ZAINCASH_SERVICE_TYPE
FORSA_PAYMENT_BASE_URL
FORSA_FIREBASE_SERVICE_ACCOUNT_JSON
FORSA_PAYMENT_DEEP_LINK
