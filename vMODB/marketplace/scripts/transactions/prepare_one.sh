#!/bin/bash

set -e
curl -s -X POST -H "Content-Type: application/json" \
  -d '{"seller_id":"1","product_id":"1","qty_available":100000,"qty_reserved":0,
       "order_count":10,"ytd":0,"data":"test","version":"0"}' \
  http://localhost:8082/stock > /dev/null
curl -s http://localhost:8082/stock/1/1 && echo


curl -X POST -H "Content-Type: application/json" -d '{"seller_id": "1", "product_id": "1", "qty_available" : 100000, "qty_reserved" : 0, "order_count" : 10, "ytd": 0, "data" : "test", "version": "0"}' localhost:8082/stock
curl -X GET localhost:8082/stock/1/1


