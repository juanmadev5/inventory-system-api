#!/bin/bash

# !Important
# Before use this test ensure you're created the table or if it is create, delete first
# and then use this test.

BASE_URL="http://localhost:5000"  # Change this if it is necessary
CATEGORY="TestCategory"           # Category for test

# 1. Test: Add the first product
echo "Testing POST /add endpoint for the first product"
curl -X POST "$BASE_URL/products/v1/add" \
  -H "Content-Type: application/json" \
  -d '{
        "name": "First Product",
        "description": "Description of the first product",
        "price": 100.0,
        "stock": 10,
        "category": "'$CATEGORY'"
      }'

echo -e "\n"

# 2. Test: Add the second product
echo "Testing POST /add endpoint for the second product"
curl -X POST "$BASE_URL/products/v1/add" \
  -H "Content-Type: application/json" \
  -d '{
        "name": "Second Product",
        "description": "Description of the second product",
        "price": 150.0,
        "stock": 5,
        "category": "'$CATEGORY'"
      }'

echo -e "\n"

# 3. Test: Get all products created
echo "Testing GET /all endpoint"
curl -X GET "$BASE_URL/products/v1/all"

echo -e "\n"

# 4. PUT test: Edit the first product (use ID 1)
echo "Testing PUT /:id endpoint for the first product"
curl -X PUT "$BASE_URL/products/v1/1" \
  -H "Content-Type: application/json" \
  -d '{
        "name": "Updated First Product",
        "description": "Updated description of the first product",
        "price": 110.0,
        "stock": 12,
        "category": "'$CATEGORY'"
      }'

echo -e "\n"

# 5. PUT test: Edit the second product (use ID 2)
echo "Testing PUT /:id endpoint for the second product"
curl -X PUT "$BASE_URL/products/v1/2" \
  -H "Content-Type: application/json" \
  -d '{
        "name": "Updated Second Product",
        "description": "Updated description of the second product",
        "price": 160.0,
        "stock": 8,
        "category": "'$CATEGORY'"
      }'

echo -e "\n"

# 6. DELETE test: Delete the first product (use ID 1)
echo "Testing DELETE /:id endpoint for the first product"
curl -X DELETE "$BASE_URL/products/v1/1"

echo -e "\n"

# 7. Test: Get all products after deletion
echo "Testing GET /all endpoint after deleting the first product"
curl -X GET "$BASE_URL/products/v1/all"

echo -e "\n"
