#!/bin/bash

param1=1

if [ $# -eq 0 ];
then
  echo "No arguments passed. Assuming one customer checkout only"
else
  param1="$1"
fi

echo "Adding product 1/1"

curl -X POST -H "Content-Type: application/json" -d '{"seller_id": "1", "product_id": "2", "name" : "productTest", "sku" : "skuTest", "category" : "categoryTest", "status" : "approved", "description": "descriptionTest", "price" : 10, "freight_value" : 0, "version": "0"}' localhost:8081/product

echo "Retrieving product 1/1"

curl -X GET localhost:8081/product/1/2

echo ""

echo "Adding stock item 1/1"

curl -X POST -H "Content-Type: application/json" -d '{"seller_id": "1", "product_id": "2", "qty_available" : 10, "qty_reserved" : 0, "order_count" : 0, "ytd": 0, "data" : "", "version": "0"}' localhost:8082/stock

echo "Retrieving stock item 1/1"

curl -X GET localhost:8082/stock/1/2

echo ""

for i in `seq 1 $param1`
do

  echo "Starting iteration $i..."
  echo ""



  curl -X PUT -H "Content-Type: application/json" -d '{"seller_id" : "1", "product_id" : "2", "name" : "productTest", "sku" : "skuTest", "category" : "categoryTest", "status" : "approved", "description" : "descriptionTest", "price" : 10, "freight_value" : 0, "version" : "0"}' localhost:8091/product/1/2

  echo ""

done

echo "Update product and price script done"
