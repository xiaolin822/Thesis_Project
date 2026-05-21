#!/bin/bash

TARGET_HOST="localhost"
STOCK_PORT=8082
CART_PORT=8080
PROXY_PORT=8091
SELLER_MS_PORT=5004

TOTAL_CONCURRENT_USERS=${1:-200}
PRODUCT_HOT_POOL_SIZE=3

# Step 1
echo "Pre-warming shared stock..."
for p_id in $(seq 1 $PRODUCT_HOT_POOL_SIZE); do
  curl -s -X POST -H "Content-Type: application/json" -d '{"seller_id": "1", "product_id": "'$p_id'", "qty_available" : 1000000, "qty_reserved" : 0, "order_count" : 0, "ytd": 0, "data" : "test", "version": "0"}' "http://$TARGET_HOST:$STOCK_PORT/stock" > /dev/null
done

INIT_SUCCESS=$(curl -s "http://$TARGET_HOST:$SELLER_MS_PORT/telemetry/metrics" | grep -o '"success_count":[0-9]*' | cut -d: -f2)
echo "Initial SellerMS processed count: $INIT_SUCCESS"
echo "-----------------------------------------------------------------"

START_TIME=$(date +%s%N)

for i in $(seq 1 $TOTAL_CONCURRENT_USERS)
do
  (
    PRODUCT_ID=$(( (i % PRODUCT_HOT_POOL_SIZE) + 1 ))
    curl -s -X PATCH -H "Content-Type: application/json" -d '{"SellerId": "1", "ProductId": "'$PRODUCT_ID'", "ProductName" : "hot", "UnitPrice" : "10", "FreightValue" : "0", "Quantity": "1", "Voucher" : "0", "Version": "0"}' "http://$TARGET_HOST:$CART_PORT/cart/$i/add" > /dev/null
    curl -s -X POST -H "Content-Type: application/json" -d '{ "CustomerId" : '$i', "instanceId" : "'$i'" }' "http://$TARGET_HOST:$PROXY_PORT/cart" > /dev/null
  ) &
done

wait
echo ">>> Client injection done. Waiting for pipeline drainage..."

PREV_COUNT=-1
while true; do
  CURRENT_TOTAL=$(curl -s "http://$TARGET_HOST:$SELLER_MS_PORT/telemetry/metrics" | grep -o '"success_count":[0-9]*' | cut -d: -f2)
  ACTUAL_SUCCESS=$((CURRENT_TOTAL - INIT_SUCCESS))

  if [ "$ACTUAL_SUCCESS" -eq "$PREV_COUNT" ] && [ "$ACTUAL_SUCCESS" -gt 0 ]; then
      END_TIME=$(date +%s%N)
      echo " Pipeline fully drained. Finalizing benchmark..."
      break
  fi
  PREV_COUNT=$ACTUAL_SUCCESS
  echo "  [Drainage] Current validated commits at SellerMS: $ACTUAL_SUCCESS / $TOTAL_CONCURRENT_USERS"
  sleep 1
done

DURATION_NS=$((END_TIME - START_TIME))
DURATION_SEC=$(echo "scale=3; $DURATION_NS / 1000000000" | bc)
GOODPUT=$(echo "scale=2; $ACTUAL_SUCCESS / $DURATION_SEC" | bc)
SUCCESS_RATE=$(echo "scale=2; ($ACTUAL_SUCCESS * 100) / $TOTAL_CONCURRENT_USERS" | bc)

echo "================================================================="
echo " End-to-End Causal Benchmark Summary:"
echo "-----------------------------------------------------------------"
echo " Dispatched Workload Users       : $TOTAL_CONCURRENT_USERS"
echo " Validated Commits at SellerMS   : $ACTUAL_SUCCESS"
echo " End-to-End Success Rate         : $SUCCESS_RATE %"
echo " Total E2E Execution Duration    : $DURATION_SEC seconds"
echo " 🌟 True End-to-End Goodput     : $GOODPUT valid-commits/sec"
echo "================================================================="