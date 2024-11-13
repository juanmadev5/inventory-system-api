import {
  actionAddProduct,
  actionGetAllProducts,
  actionGetProductsByCategory,
  actionEditProduct,
  actionDeleteProduct,
} from "../models/productModel.js";
import "dotenv/config";

class ProductController {
  constructor() {}

  async addProduct(req, res) {
    try {
      const data = await actionAddProduct(req);
      res.status(200).json({
        status: "success",
        message: "Product added successfully",
        data: data,
      });
    } catch (e) {
      res.status(500).json({
        status: "error",
        message: "Internal server error",
        details: e.message,
      });
    }
  }

  async getAllProducts(req, res) {
    try {
      const data = await actionGetAllProducts();
      res.status(200).json({
        status: "success",
        data: data,
      });
    } catch (e) {
      res.status(500).json({
        status: "error",
        message: "Error on internal server",
        details: e.message,
      });
    }
  }

  async getProductsByCategory(req, res) {
    try {
      const data = await actionGetProductsByCategory(req);
      res.status(200).json({
        status: "success",
        data: data,
      });
    } catch (e) {
      res.status(500).json({
        status: "error",
        message: "Error on internal server",
        details: e.message,
      });
    }
  }

  async editProduct(req, res) {
    try {
      const data = await actionEditProduct(req);
      res.status(200).json({
        status: "success",
        message: "Product updated successfully",
        data: data,
      });
    } catch (e) {
      res.status(500).json({
        status: "error",
        message: "Error on internal server",
        details: e.message,
      });
    }
  }

  async deleteProduct(req, res) {
    try {
      const data = await actionDeleteProduct(req);
      res.status(200).json({
        status: "success",
        message: "Product deleted successfully",
        data: data,
      });
    } catch (e) {
      res.status(500).json({
        status: "error",
        message: "Error on internal server",
        details: e.message,
      });
    }
  }
}

export default new ProductController();
