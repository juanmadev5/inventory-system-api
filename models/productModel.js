import supabase from "../config/dbConfig.js";

const actionAddProduct = async (req) => {
  // Required fields
  const { name, description, price, stock, category } = req.body;
  if (!name || !price || !stock) {
    throw new Error("Missing required fields: name, price, stock");
  }

  // Validate that price and stock are numbers
  if (isNaN(price) || isNaN(stock)) {
    throw new Error("Price and stock must be valid numbers");
  }

  // Put the new product in database
  const { data, error } = await supabase
    .from("products")
    .insert([
      {
        name,
        description,
        price,
        stock,
        category,
      },
    ])
    .select();

  if (error) {
    throw new Error("Error inserting product: " + error.message);
  }

  return data;
};

const actionGetAllProducts = async () => {
  const { data, error } = await supabase.from("products").select();
  if (error) {
    throw new Error("Error getting products: " + error.message);
  }
  return data;
};

const actionGetProductsByCategory = async (req) => {
  const { category } = req.params;
  if (!category) {
    throw new Error("Missing required field: category");
  }

  const { data, error } = await supabase
    .from("products")
    .select("id, name, price, stock")
    .eq("category", category);

  if (error) {
    throw new Error("Error fetching products by category: " + error.message);
  }
  return data;
};

const actionEditProduct = async (req) => {
  const { id } = req.params;
  const { name, description, price, stock, category } = req.body;
  if (!name || !price || !stock) {
    throw new Error("Missing required fields: name, price, stock");
  }

  // Validate that price and stock are numbers
  if (isNaN(price) || isNaN(stock)) {
    throw new Error("Price and stock must be valid numbers");
  }

  const { data, error } = await supabase
    .from("products")
    .update({
      name,
      description,
      price,
      stock,
      category,
      updated_at: new Date(),
    })
    .eq("id", id)
    .select();

  if (error) {
    throw new Error("Error updating product: " + error.message);
  }

  if (!data || data.length === 0) {
    throw new Error("Product not found");
  }

  return data;
};

// Acción para eliminar un producto
const actionDeleteProduct = async (req) => {
  const { id } = req.params;
  const { data, error } = await supabase
    .from("products")
    .delete()
    .eq("id", id)
    .select();

  if (error) {
    throw new Error("Error deleting product: " + error.message);
  }

  return data;
};

export {
  actionAddProduct,
  actionGetAllProducts,
  actionGetProductsByCategory,
  actionEditProduct,
  actionDeleteProduct,
};
