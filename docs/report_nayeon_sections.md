# Report draft: Nayeon's sections (3 Methodology, 6 Conclusion)

Draft of 13 Nov 2026, shortened 14 Nov; Methodology cut to under one page on 17 Nov (no repeats of Sections 2 and 4), for the final report (template sections 3 and 6). Every number comes from a
committed artifact: fold results from `artifacts/folds/` (commit `1042a3d`, all runs passing the
run-gate), the baseline ladder from `artifacts/folds/primary/ladder.md`, selection frequencies
from `artifacts/folds/ablation_feature_selection_frequency.csv`, blend weights from
`analysis/overfitting_results/report.md`, and earlier development numbers from the 4 Nov
progress update and the checkpoint. Citations are IEEE-style numbers; the reference list is at
the end. Renumber them when the sections are merged.

---

## 3 Methodology

### 3.1 Approach and baselines

We follow a scaled-down version of Benter's two-stage method [1]. Stage 1 estimates each runner's win probability from pre-race features only (Section 2.4), without the odds. Stage 2 blends these probabilities with the market's, so the blend weights show what the models add beyond the odds. Every model outputs probabilities that sum to one within a race. The baselines are uniform (1/n), a draw-only conditional logit, our original conditional logit (15 frozen features), the raw market q_i = (1/o_i) / Σ_j (1/o_j) from final odds o_i, and the calibrated market p_i ∝ q_i^β. The exponent β can correct the favourite–longshot bias [5], which makes the calibrated market the stricter target of our primary comparison.

### 3.2 Stage 1 models

**Conditional logit** [2], [3]. Runner i in race r has utility u_ri = x_ri'b and p_ri = exp(u_ri) / Σ_{j∈r} exp(u_rj). Standardised features are fitted with an elastic net [6] using L-BFGS-B [7]. Forward selection starts from six base features and adds the candidate that most lowers inner-tune log loss, until the gain falls below 0.001.

**LightGBM** [4] tests non-linear effects and interactions. Unlike the binary classifier in our proposal, whose outputs had to be renormalised per race, it is trained directly on the per-race softmax likelihood, so training and evaluation use the same loss. Monotone constraints follow each feature's expected sign, and the number of trees is set by early stopping on inner-tune log loss.

### 3.3 Stage 2 blend

s_ri = β log q_ri + α_CL log p_ri^CL + α_GBM log p_ri^GBM, normalised by a softmax within the race. A weight α ≈ 0 means that model adds nothing beyond the odds. A ridge penalty shrinks the weights toward the market-only solution (β = 1, α = 0). The pre-registered primary model blends the market with both models; the market + CL and market + LightGBM blends are also reported.

### 3.4 Ablations and alternatives

To measure which feature families matter (sub-question 1), the whole pipeline is rerun without trainer-rate features, without track/distance features, and without both. Two earlier designs were set aside: a single tuning split, replaced by the chronological folds (Section 4.1), and a LightGBM that learns adjustments to the odds, whose gain fell from +0.0013 to about +0.00005 in a stricter rerun, so we keep it only as a diagnostic.

---

## 6 Conclusion

Can a fundamental model built from public data predict Hong Kong race winners better than the
HKJC odds? On our data, no: we found no detectable improvement over the calibrated market. On
927 validation races, the pre-registered primary blend failed its rule (k1 −0.0004, k2 +0.0007,
every interval including zero). The best of 12 candidates, chosen after seeing the results,
gained +0.0014 [−0.0007, +0.0036], within what choosing the best of 12 by chance produces
(p = 0.17), and scored −0.0027 [−0.0102, +0.0051] on the 400 held-out races.

The strongest supported findings are:

1. **The market dominates.** It lowers pooled log loss by 0.510 relative to uniform (2.499 →
   1.989); our best stand-alone model, LightGBM, achieves 60 % of that (2.193). Public data carry
   real information, but almost all of it is already priced: blending adds about 0.001.
2. **Feature families (sub-question 1).** The v3.1 features improved the stand-alone
   conditional logit (2.1945 vs 2.1977 for our original model), but removing the track/distance
   features improved the blend slightly in every fold, and removing trainer rates changed
   nothing consistently. They describe horses better without adding what the market lacks.
3. **LightGBM vs conditional logit (sub-question 2).** LightGBM was marginally better alone
   (2.1931 vs 2.1945) but not alongside the market (pooled gains +0.0008 vs +0.0011), and blend
   weights changed sign between folds. Non-linearity captured no extra residual signal.
4. **The result is reproducible.** Leakage tests, a run-gate and a bit-identical rebuild tie
   every number to a clean commit.

We demonstrated a leakage-safe, reproducible pipeline whose intervals rule out improvements
larger than about 0.003–0.004 log loss on these folds. We did not demonstrate an edge, and we
did not evaluate betting, so nothing here supports profitable wagering after the HKJC takeout.
The most useful next step is a prospective test on races run after the freeze, which our
scoring gate already enforces. More continuous HKJC data would narrow the intervals; as
speculation, information absent from public results, such as sectional times or odds
movements, seems more promising than further transformations of the same results.

---

## Contribution table row (Nayeon)

| Member | Specific contributions | Evidence or output |
|---|---|---|
| Nayeon Kim | Week 3 features (form, jockey/trainer, running style) and correlation analysis; v3.1 track/distance hierarchical form, trajectory and interaction features with leakage tests; stamped raw-run build and verification; history-depth and missingness tables; report Sections 3 and 6 | `model_logic/track_distance_features.py`, `model_logic/trajectory_features.py`, `tests/test_v3_features_leakage.py`, `analysis/v3_build.py`, `analysis/history_depth_results/`, `docs/features_v3_track_distance.md` |

---

## References (IEEE)

[1] W. Benter, "Computer based horse race handicapping and wagering systems: A report," in
*Efficiency of Racetrack Betting Markets*, D. B. Hausch, V. S. Y. Lo, and W. T. Ziemba, Eds.
San Diego, CA, USA: Academic Press, 1994, pp. 183–198.

[2] D. McFadden, "Conditional logit analysis of qualitative choice behavior," in *Frontiers in
Econometrics*, P. Zarembka, Ed. New York, NY, USA: Academic Press, 1974, pp. 105–142.

[3] R. N. Bolton and R. G. Chapman, "Searching for positive returns at the track: A multinomial
logit model for handicapping horse races," *Management Science*, vol. 32, no. 8,
pp. 1040–1060, 1986.

[4] G. Ke et al., "LightGBM: A highly efficient gradient boosting decision tree," in *Advances in
Neural Information Processing Systems 30*, 2017, pp. 3146–3154.

[5] E. Snowberg and J. Wolfers, "Explaining the favorite–long shot bias: Is it risk-love or
misperceptions?," *Journal of Political Economy*, vol. 118, no. 4, pp. 723–746, 2010.

[6] H. Zou and T. Hastie, "Regularization and variable selection via the elastic net,"
*Journal of the Royal Statistical Society: Series B*, vol. 67, no. 2, pp. 301–320, 2005.

[7] P. Virtanen et al., "SciPy 1.0: Fundamental algorithms for scientific computing in Python,"
*Nature Methods*, vol. 17, pp. 261–272, 2020.
