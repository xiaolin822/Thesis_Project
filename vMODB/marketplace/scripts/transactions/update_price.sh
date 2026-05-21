#!/bin/bash

param1=1

if [ $# -eq 0 ];
then
  echo "No arguments passed. Assuming one customer checkout only"
else
  param1="$1"
fi

echo "Adding product 1/2"

curl -X POST -H "Content-Type: application/json" -d '{"seller_id": "1", "product_id": "2", "name" : "productTest", "sku" : "skuTest", "category" : "categoryTest", "status" : "approved", "description": "descriptionTest", "price" : 10, "freight_value" : 0, "version": "1"}' localhost:8081/product

echo "Retrieving product 1/2"

curl -X GET localhost:8081/product/1/2

echo ""

for i in `seq 1 $param1`
do

  echo "Updating price of product 1/1 iteration $i"

  curl -X PATCH -H "Content-Type: application/json" -d '{ "sellerId" : 1, "productId" : 2, "price" : 100, "instanceId" : "1" ,  "version": "1"}' localhost:8091/product/1/2

  echo ""

done

echo "Update price script done"
