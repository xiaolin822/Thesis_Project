import pandas as pd
import matplotlib.pyplot as plt
from io import StringIO

# 你的实验数据
csv_text = """system,concurrency,tx,count,avg,min,max,p50,p90,p95,p99
withkafka,8,1000,1000,78.358000,22,245,72,124,143,197
withkafka,16,1000,1000,85.382000,20,189,83,125,137,164
withkafka,4,1000,1000,41.828000,14,117,42,57,62,77
withkafka,2,1000,1000,26.905000,11,102,24,43,48,61
withkafka,16,2000,2000,73.062500,22,163,71,99,109,126
withkafka,16,4000,4000,69.447500,22,169,69,93,102,117
withkafka,16,8000,8000,68.577375,21,153,68,91,98,113
withkafka,1,1000,1000,20.518000,13,57,19,25,29,40
baseline,1,1000,1000,21.400000,11,63,19,30,39,52
baseline,2,1000,1000,23.141000,11,66,21,30,38,50
baseline,4,1000,1000,33.753000,13,83,31,50,56,66
baseline,8,1000,1000,53.388000,16,120,52,73,80,96
baseline,16,1000,1000,73.101000,24,181,71,104,114,137
"""

df = pd.read_csv(StringIO(csv_text))
df["concurrency"] = df["concurrency"].astype(int)
df["tx"] = df["tx"].astype(int)

# =========================
# Figure 1: avg vs concurrency (tx=1000)
# =========================
df_1000 = df[df["tx"] == 1000].copy()
df_1000 = df_1000.sort_values(["system", "concurrency"])

plt.figure()
for system in df_1000["system"].unique():
    sub = df_1000[df_1000["system"] == system]
    plt.plot(sub["concurrency"], sub["avg"], marker="o", label=system)
plt.xlabel("Concurrency")
plt.ylabel("Average latency (ms)")
plt.title("Average latency vs concurrency (tx=1000)")
plt.legend()
plt.grid(True)
plt.tight_layout()
plt.savefig("latency_avg_vs_concurrency_tx1000.png", dpi=300)
plt.close()

# =========================
# Figure 2: p95 vs concurrency (tx=1000)
# =========================
plt.figure()
for system in df_1000["system"].unique():
    sub = df_1000[df_1000["system"] == system]
    plt.plot(sub["concurrency"], sub["p95"], marker="o", label=system)
plt.xlabel("Concurrency")
plt.ylabel("P95 latency (ms)")
plt.title("P95 latency vs concurrency (tx=1000)")
plt.legend()
plt.grid(True)
plt.tight_layout()
plt.savefig("latency_p95_vs_concurrency_tx1000.png", dpi=300)
plt.close()

# =========================
# Figure 3: withkafka warm-up curve (concurrency=16)
# =========================
df_wk_16 = df[(df["system"] == "withkafka") & (df["concurrency"] == 16)].copy()
df_wk_16 = df_wk_16.sort_values("tx")

plt.figure()
plt.plot(df_wk_16["tx"], df_wk_16["avg"], marker="o", label="avg")
plt.plot(df_wk_16["tx"], df_wk_16["p95"], marker="s", label="p95")
plt.xlabel("Number of transactions (tx)")
plt.ylabel("Latency (ms)")
plt.title("withkafka, concurrency=16: warm-up effect")
plt.legend()
plt.grid(True)
plt.tight_layout()
plt.savefig("withkafka_conc16_warmup.png", dpi=300)
plt.close()
