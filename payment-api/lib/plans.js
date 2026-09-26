const PLANS = {
  boost_7: {
    title: "مميز 7 أيام",
    amountIqd: 5000,
    durationMs: 7 * 24 * 60 * 60 * 1000,
  },
  top_7: {
    title: "تثبيت 7 أيام",
    amountIqd: 8000,
    durationMs: 7 * 24 * 60 * 60 * 1000,
  },
  urgent_48: {
    title: "عاجل 48 ساعة",
    amountIqd: 3000,
    durationMs: 48 * 60 * 60 * 1000,
  },
};

function getPlan(planId) {
  return PLANS[planId] || null;
}

module.exports = { PLANS, getPlan };
