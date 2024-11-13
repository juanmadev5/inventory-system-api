import "dotenv/config";
import app from "./config/middleware.js";
import routesProducts from "./routes/products.js";

app.use("/products/v1", routesProducts);

try {
  const port = process.env.PORT || 3000;
  app.listen(port, () => console.log("server active on port " + port));
} catch (e) {
  console.log(e);
}
