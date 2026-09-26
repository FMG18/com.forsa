function requireEnv(name) {
  const value = process.env[name];
  if (!value) {
    throw new Error(name + " is not configured");
  }
  return value;
}

function baseUrl() {
  return requireEnv("ZAINCASH_BASE_URL").replace(/\/$/, "");
}

async function parseResponse(response) {
  const text = await response.text();
  let body = {};
  try {
    body = text ? JSON.parse(text) : {};
  } catch {
    body = { raw: text };
  }
  return { response, body };
}

async function getAccessToken() {
  const clientId = requireEnv("ZAINCASH_CLIENT_ID");
  const clientSecret = requireEnv("ZAINCASH_CLIENT_SECRET");
  const scope = process.env.ZAINCASH_SCOPE || "payment:read payment:write";

  const body = new URLSearchParams({
    grant_type: "client_credentials",
    client_id: clientId,
    client_secret: clientSecret,
    scope,
  });

  const result = await fetch(baseUrl() + "/oauth2/token", {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
    },
    body,
  });

  const { response, body: json } = await parseResponse(result);
  if (!response.ok) {
    throw new Error("ZainCash token request failed: " + JSON.stringify(json));
  }

  if (!json.access_token) {
    throw new Error("ZainCash token response did not include access_token");
  }

  return json.access_token;
}

async function initPayment({
  orderId,
  externalReferenceId,
  amountIqd,
  successUrl,
  failureUrl,
}) {
  const token = await getAccessToken();
  const serviceType = process.env.ZAINCASH_SERVICE_TYPE || "ForsaPromotion";

  const payload = {
    language: "ar",
    externalReferenceId,
    orderId,
    serviceType,
    amount: {
      value: amountIqd,
      currency: "IQD",
    },
    redirectUrls: {
      successUrl,
      failureUrl,
    },
  };

  const result = await fetch(
    baseUrl() + "/api/v2/payment-gateway/transaction/init",
    {
      method: "POST",
      headers: {
        Authorization: "Bearer " + token,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(payload),
    }
  );

  const { response, body: json } = await parseResponse(result);
  if (!response.ok) {
    throw new Error("ZainCash payment init failed: " + JSON.stringify(json));
  }

  const redirectUrl =
    json.redirectUrl ||
    json.data?.redirectUrl ||
    json.payment?.redirectUrl;

  const transactionId =
    json.transactionId ||
    json.data?.transactionId ||
    json.payment?.transactionId;

  if (!redirectUrl || !transactionId) {
    throw new Error("ZainCash init response is missing redirectUrl or transactionId");
  }

  return { redirectUrl, transactionId, raw: json };
}

async function inquiry(transactionId) {
  const token = await getAccessToken();

  const result = await fetch(
    baseUrl() +
      "/api/v2/payment-gateway/transaction/inquiry/" +
      encodeURIComponent(transactionId),
    {
      method: "GET",
      headers: {
        Authorization: "Bearer " + token,
      },
    }
  );

  const { response, body: json } = await parseResponse(result);
  if (!response.ok) {
    throw new Error("ZainCash inquiry failed: " + JSON.stringify(json));
  }

  return json;
}

async function verifyCallbackToken(token) {
  const apiKey = requireEnv("ZAINCASH_API_KEY");
  const { jwtVerify } = await import("jose");

  const result = await jwtVerify(
    token,
    new TextEncoder().encode(apiKey),
    { algorithms: ["HS256"] }
  );

  return result.payload;
}

function getClaim(payload, names) {
  for (const name of names) {
    const value = payload?.[name];
    if (value !== undefined && value !== null && String(value).trim() !== "") {
      return String(value);
    }
  }

  for (const containerName of ["data", "transaction", "payment"]) {
    const nested = payload?.[containerName];
    if (!nested || typeof nested !== "object") continue;

    for (const name of names) {
      const value = nested[name];
      if (value !== undefined && value !== null && String(value).trim() !== "") {
        return String(value);
      }
    }
  }

  return "";
}

module.exports = {
  getFirebaseAdmin: undefined,
  getAccessToken,
  initPayment,
  inquiry,
  verifyCallbackToken,
  getClaim,
};
