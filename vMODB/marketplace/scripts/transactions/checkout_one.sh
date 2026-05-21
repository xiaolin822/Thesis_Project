#!/bin/bash
set -e

id="$1"

curl -s -X PATCH -H "Content-Type: application/json" \
  -d '{"SellerId":"1","ProductId":"1","ProductName":"test","UnitPrice":"10",
       "FreightValue":"0","Quantity":"1","Voucher":"0","Version":"0"}' \
  "http://localhost:8080/cart/$id/add" > /dev/null

start_ms=$(date +%s%3N)

resp=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
  -H "Content-Type: application/json" \
  -d '{ "CustomerId": '"$id"', "FirstName":"t", "LastName":"t", "Street":"t",
        "Complement":"t","City":"t","State":"t","ZipCode":"t",
        "PaymentType":"CREDIT_CARD","CardNumber":"t","CardHolderName":"t",
        "CardExpiration":"t","CardSecurityNumber":"t","CardBrand":"t",
        "Installments":"1", "instanceId":"'"$id"'" }' \
  http://localhost:8091/cart)

end_ms=$(date +%s%3N)
latency=$((end_ms - start_ms))

# 成功判定：HTTP 200（按你服务实际返回调整）
if [ "$resp" = "200" ]; then
  echo "OK" >> run.ok           # 计数成功
else
  echo "ERR $resp" >> run.err    # 记录失败
fi

echo "$latency" >> latency_ms.csv  # 记录延迟