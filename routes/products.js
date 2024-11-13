import express from "express";
const router = express.Router();
import productsController from "../controllers/productsController.js";

router.post("/add", productsController.addProduct); // add a product
router.get("/all", productsController.getAllProducts); // get all products
router.get("/:category", productsController.getProductsByCategory); // get products by category
router.put("/:id", productsController.editProduct); // edit a product
router.delete("/:id", productsController.deleteProduct); //delete a product

export default router;
