module.exports = async function handler(req, res) {
  return res.status(200).json({
    success: true,
    service: "Forsa Payment API",
    version: "1.0.0",
  });
};
