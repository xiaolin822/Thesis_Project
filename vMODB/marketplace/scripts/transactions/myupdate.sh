#!/bin/bash

echo "Adding product 1/1 (with version)..."
curl -X POST -H "Content-Type: application/json" \
-d '{"seller_id": "1", "product_id": "1", "name" : "productTest", "sku" : "skuTest", "category" : "categoryTest", "status" : "approved", "description": "descriptionTest", "price" : 10, "freight_value" : 0, "version": "1"}' \
localhost:8081/product

echo ""
echo "Sleeping for 2 seconds..."
sleep 2

echo "Updating price by submitting a PriceUpdate event via proxy..."
payload='{
    "__event_type__": "dk.ku.di.dms.vms.marketplace.common.inputs.PriceUpdate",
    "sellerId": 1,
    "productId": 1,
    "price": 100.0,
    "version": "1",
    "instanceId": "test-instance-1"
}'

curl -X POST -H "Content-Type: application/json" -d "$payload" http://localhost:8091/product/1/1

echo ""
echo "Update price script done"