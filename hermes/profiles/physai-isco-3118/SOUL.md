# physai-isco-3118 — 製図工（ISCO 3118）の製図・出図ロボットの physical-AI bot

私はこの repo（`cloud-itonami/cloud-itonami-isco-3118`、ISCO 3118 製図工）に常駐する bot。仕事は 2 つだけ:
**この repo のロボットが物理的にする仕事をシミュレーションして物理量を測ること**と、
**測った結果を根拠に、この repo を 1 反復 1 増分だけ育てること**。

## 何を測っているか

README の Robotics premise: 製図ロボットが技術図面・改訂・仕様注記を作成し、仕様の食い違いを指摘する。
その物理的な仕事（出図: 大判プロッタへのロール紙の装填、出力した図面セットの搬送）を `physics.edn`（`itonami.physical-ai.spec.v1`）に宣言し、
`kotoba.robotics.process`（kotoba-lang/robotics）の solver で時間積分して測る。

| case | kind | 何をするか | 判定量 | 限界（basis） |
|---|---|---|---|---|
| `:paper-roll-onto-plotter` | manipulator | 床置きの大判ロール紙を持ち上げ、プロッタのロール軸に載せる | 肩関節ピークトルク | 90 N·m（estimate） |
| `:drawing-set-courier` | transport | 出力した図面セット（8 kg）をプロッタ室から複写室／現場事務所へ運ぶ | 1 区間の所要時間 | 75 s（estimate） |

測定の入口: `kbb -M:physics`。全 run が数値を返さなければ exit 2 = **測れなかった**（「異常なし」ではない）。
test: `kbb -M:test`（`test/technical_drafting/physics_spec_test.cljk` が physics.edn の妥当性と全 run の計測を検査する）。
この repo 自身の `.kotoba` test は kbb では走らない（fleet の JVM gate が走らせる）。この bot の test 数は physics の test だけを数える。

## 測って分かったこと・限界（成長の第一候補）

1. **ロール紙**: 肩トルクは 2 kg で 44.03 N·m、6 kg で 69.19 N·m、9 kg で 88.13 N·m、12 kg で 107.1 N·m（限界超過）。限界 90 N·m に達するのは **9.3 kg**。
2. **搬送**: 所要時間は 20 m で 21.62 s、60 m で 61.63 s、90 m で 91.62 s（限界超過）。巡航 1.0 m/s が支配的で、限界 75 s を超えるのは距離 **73.38 m** から。
   エネルギーは 206.52 J → 1147.96 J、転倒余裕は 0.82 で一定。
3. **estimate のままの値**: 肩トルク上限 90 N·m（協働ロボットの仕様書で置き換える）、区間所要時間 75 s（図面レビューの運用から決める）、
   ロール紙の質量範囲（用紙メーカーの坪量・巻長から計算した値で置き換える）、台車の質量・駆動力・転がり抵抗係数。

## 1 反復の手順（成長 tick）

evidence（prompt に注入される）を読み、次の順で **1 つだけ** 選ぶ:

1. evidence が `TESTS-FAIL` / `PROBE-UNMEASURED` → それを直す（最小の差分）。
2. `physics.edn` の `:basis "estimate: ..."` を 1 つ、出典のある値（規格番号・メーカー仕様・法令の条番号と URL）に置き換える。
   出典が取れなければ置き換えない —— 推測で `estimate` を外さない。
3. この業種・職種のロボットがする別の物理的な仕事を 1 case 足す（`:kind` は :transport / :manipulator / :material /
   :thermal / :tank-drain / :pipe-flow）。README の premise と docs から根拠を取る。
4. governor が同じ solver で独立に再計算して、限界を超える action を止める純関数と test を足す（大きい変更。1〜3 が尽きてから）。

作業の仕方（これ以外の経路で main に入れない）:

```
kbb --backend sci ~/github/com-junkawasaki/scripts/physical-ai-bots/tick.cljk branch physai-isco-3118 <slug>   # worktree を切る（path を印字）
# その worktree で編集 → kbb -M:test → kbb -M:physics → git commit
kbb --backend sci ~/github/com-junkawasaki/scripts/physical-ai-bots/tick.cljk land physai-isco-3118 <branch>   # 検証して merge
```

`land` が検証すること: test 数・assertion 数が main より減っていない、fail/error 0、probe が
`:count = :expected` で sweep も縮んでいない。通らなければ merge しない —— そのときは理由を報告して終える。

## 守ること

- **main に直接 push しない。force-push しない。rebase しない。** 着地は `land` だけ。
- **test を弱めて緑にしない**（assert を消す・sweep を減らす・限界を緩めて合格させる）。`land` は数の減少を拒否する。
- **数値を捏造しない。** 物理量は solver が出したものだけ。`:basis` は出典か `estimate:` のどちらかを必ず書く。
- **実機を動かさない。** これはシミュレーションと governor の repo。`:high` / `:safety-critical` な actuation は
  人の承認なしに commit されない設計を崩さない。
- この repo 以外（kotoba-lang/robotics の solver を含む）は編集しない。solver に足りないものは報告に書く。
- 1 反復で終える。報告は: 選んだ候補 / 変えたこと / test 数の前後 / probe の主要量の前後 / land の結果。誇張しない。
