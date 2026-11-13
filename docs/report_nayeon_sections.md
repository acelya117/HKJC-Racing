# Report draft: Nayeon's sections (3 Methodology, 6 Conclusion)

Draft of 13 Nov 2026 for the final report (template sections 3 and 6). Every number comes from a
committed artifact: fold results from `artifacts/folds/` (commit `1042a3d`, all runs passing the
run-gate), the baseline ladder from `artifacts/folds/primary/ladder.md`, selection frequencies
from `artifacts/folds/ablation_feature_selection_frequency.csv`, blend weights from
`analysis/overfitting_results/report.md`, and earlier development numbers from the 4 Nov
progress update and the checkpoint. Citations are IEEE-style numbers; the reference list is at
the end. Renumber them when the sections are merged.

---

## 3 Methodology

### 3.1 Overview

Our approach is a scaled-down version of Benter's two-stage method [1]. Stage 1 estimates each
runner's win probability from public pre-race information only, without the odds. Stage 2
combines these "fundamental" probabilities with the market's implied probabilities in a second
conditional logit, so its fitted weights show what the models add beyond the odds. Every model
outputs one probability per runner that sums to one within the race, and every model is scored
on the same races by per-race log loss (Section 5).

Pipeline: raw runners → feature builder (registry v3.1) → Stage 1: conditional logit and
LightGBM → Stage 2: blend with the market → per-fold scoring against the baseline ladder.

### 3.2 Baselines

We compare against a ladder of baselines of increasing strength, all fitted and scored on the
same chronological folds:

1. **Uniform:** p = 1/n for n starters.
2. **Draw-only:** a conditional logit on three draw features.
3. **Original conditional logit:** our 4 Nov model with its frozen 15-feature universe.
4. **Raw market:** final win odds o_i converted to q_i = (1/o_i) / Σ_j (1/o_j).
5. **Calibrated market:** p_i ∝ q_i^β, with β fitted by maximum likelihood on the Stage 2 period.
   The exponent can correct a favourite–longshot bias, where bettors overbet longshots [5],
   so it is a stricter benchmark than the raw market. It is the target of our primary comparison.

Shin's and power normalisation are also implemented in our evaluation tools as sensitivity
checks; the fold ladder uses the calibrated market above.

### 3.3 Feature design

All features use only earlier race days from the same source and are mostly expressed relative
to the race mean (Section 2.5). The model inputs come from a versioned registry: 32 candidates
for the conditional logit (six always included) and 38 for LightGBM (the same features plus
within-race ranks and field size). Two design choices matter for the method.

**Hierarchical shrinkage for track/distance form.** For a horse's win rate, top-three rate and
mean finishing percentile at today's track (T), distance band (D: ≤ 1200 m, ≤ 1650 m, ≤ 2000 m,
longer) and track × band (TD), each level is shrunk toward the level above:

  r_level = (S_level + k · r_parent) / (n_level + k),  k = 5,

where n_level and S_level are the horse's earlier-day starts and outcomes at that level. The
chain is TD → T → career → prior-day source rate, and D → career. A horse with no history at a
level inherits its parent, so these nine features have no missing values.

**Trajectory and interaction terms.** Last finish minus the three-run average, the three-run
finishing slope, layoff buckets, class-change direction, recent form × class change, and
jockey/trainer strength × field size were added as hypothesis-led terms. Running-style features
from in-race positions were built in Week 3 but excluded, because positions are only known
during the race.

### 3.4 Stage 1a: conditional logit

Following McFadden [2] and its use in handicapping [3], [1], runner i in race r has utility
u_ri = x_ri'b and

  p_ri = exp(u_ri) / Σ_{j ∈ r} exp(u_rj).

The likelihood uses each race's winner (dead heats share the target). Features are standardised
with training-period means and standard deviations, and missing values are set to 0, the
training mean. The coefficients minimise the mean negative log-likelihood plus an elastic-net
penalty [6], λ(0.5‖b‖₁ + 0.5 b'Sb), where S is the pooled within-race covariance of the
features, so the penalty acts on utility differences between runners. We optimise with
L-BFGS-B [7].

Features are selected by greedy forward selection. It starts from six base features (jockey win
rate, three-run form, draw, draw × Happy Valley, carried weight, days since last run). Each step
adds the candidate that most reduces log loss on the inner-tune period, skips candidates with
|Spearman ρ| > 0.9 against a selected feature, and stops when the best gain is below 0.001. λ is
then chosen from {0, 0.003, 0.01} on the same inner-tune period, and the model is refitted on
the whole Stage 1 period.

### 3.5 Stage 1b: LightGBM

LightGBM [4] tests whether non-linear effects and interactions, such as weight mattering more
over long distances, add signal. Our proposal used a binary classifier whose per-runner
probabilities were renormalised within each race afterwards. We instead train on the per-race
softmax likelihood: the trees output a utility f(x_ri), and a custom objective supplies the
gradient and Hessian of −log softmax_r(f) at the winner, so training and evaluation use the same
loss. Extra inputs are within-race percentile ranks of five key rates and field size, and
monotone constraints follow each feature's expected sign (for example, better recent form never
lowers the utility).

The parameters were fixed before the fold runs: learning rate 0.02, 15 leaves, at least 200 rows
per leaf, 80 % row bagging, 70 % feature sampling, L2 penalty 10, and 63 bins. The number of trees
is chosen by early stopping on inner-tune per-race log loss (at most 4,000), scaled to the size
of the full Stage 1 period, and the model is refitted there. Inside folds, predictions average
the utilities of two seeds; a five-seed bag was reserved for a frozen final model.

### 3.6 Stage 2: blending with the market

Stage 2 is a conditional logit on log probabilities:

  s_ri = β log q_ri + α_CL log p_ri^CL + α_GBM log p_ri^GBM,  p_ri = exp(s_ri) / Σ_{j ∈ r} exp(s_rj).

If the models carry no information beyond the odds, α ≈ 0 and β alone calibrates the market;
α > 0 means the fundamental model adds signal [1]. The weights are fitted on the Stage 2 period
with a ridge penalty that shrinks them toward the market-only solution (β = 1, α = 0). The
penalty is chosen from {0, 0.003, 0.01, 0.03, 0.1} by forward validation inside Stage 2: each
month is scored by a blend fitted on the earlier months. We fit three blends (market + CL,
market + LightGBM, market + both). The pre-registered primary model is market + both.

### 3.7 Chronological folds and ablations

All fitting is chronological and separated by period (Table M1). In each fold, the Stage 1
models are fitted on S1. Its last four months form the inner-tune period, which is used for
feature selection, the CL penalty and LightGBM early stopping. The blend and the market
calibration are fitted on S2, and every model is scored once on the validation period. Folds k1
and k2 use Kaggle data; h1 fits Stage 1 on Kaggle plus early HKJC data and validates on HKJC.
Races from 1 April 2026 (h_test) are excluded from every fold. The decision rules are in
Section 4.1.

**Table M1.** Chronological folds.

| Fold | S1 (Stage 1 fit) | S2 (blend fit) | Validation | Validation races |
|---|---|---|---|---|
| k1 | Sep 2017 – Jan 2019 | Feb – Jul 2019 | Sep 2019 – Jan 2020 | 379 |
| k2 | Sep 2017 – Jul 2019 | Sep 2019 – Jan 2020 | Feb – Jun 2020 | 377 |
| h1 | Sep 2017 – Oct 2025 (Kaggle + HKJC) | Nov 2025 – Jan 2026 | Feb – Mar 2026 | 171 |

To separate feature families (sub-question 1), the whole pipeline is run with four feature
configurations: all features (primary), without trainer-rate features, without track/distance
features, and without both.

### 3.8 Alternatives and refinements

**Table M2.** Main changes during development.

| Change | Why we tried it | Outcome |
|---|---|---|
| Binary LightGBM → per-race softmax objective | A binary loss ignores that runners compete within a race; renormalising afterwards changes the probabilities the model was trained on | Kaggle tune log loss 2.1837 → 2.1768; adopted |
| Single tuning split → forward chronological folds | A feature should help repeatedly, not on one split | Adopted for all evidence |
| Market-offset LightGBM (start from the odds, learn adjustments) | A direct test for residual signal | +0.0013 over four Kaggle periods fell to about +0.00005 in a stricter rerun; kept only as a residual diagnostic |
| v2 → v3.1 features (track/distance, trajectory, interactions, jockey–trainer partnership) | Our roadmap judged that new pre-race information was more promising than model complexity | Better stand-alone CL; no gain in the blends (Section 5.3) |
| Running-style features; HKJC horse-profile fields | Positions are known only during the race; profile missingness encodes the future (Section 2.3) | Excluded |
| HKJC-only experiment with ratings and running style | Planned for sub-question 1 | Not implemented; no claim made |

### 3.9 Software

Python 3.13 with NumPy [8], pandas [9], SciPy [7] and LightGBM 4.7 [4]. Exact versions are
locked in `requirements-lock.txt` (Section 4.3).

---

## 6 Conclusion

Our question was whether a fundamental model built from public data can predict Hong Kong race
winners better than the HKJC betting odds. On our data the answer is no: we found no detectable
improvement over the calibrated market. On 927 validation races in three chronological folds,
the pre-registered primary blend (market + conditional logit + LightGBM) failed its rule, with
gains of −0.0004 in k1 and +0.0007 in k2 and every interval including zero. The best of 12
configuration–blend candidates, chosen after seeing the results, gained +0.0014 pooled
[−0.0007, +0.0036]. That is within what picking the best of 12 by chance produces
(p = 0.17), and its retrospective score on the 400 held-out races was −0.0027 [−0.0102, +0.0051].

The strongest supported findings are:

1. **The market dominates.** Moving from uniform probabilities to the market lowers pooled log
   loss by 0.510 (2.499 → 1.989). Our best stand-alone fundamental model, LightGBM, gains 0.306
   (2.193), about 60 % of that. Public pre-race data therefore carry real information, but
   almost all of it is already in the odds: blending adds at most about 0.001.
2. **Sub-question 1 (feature families).** The v3.1 features made the stand-alone conditional logit
   slightly better than our original model (2.1945 vs 2.1977). Yet removing the track/distance
   features improved the blend slightly in every fold, and removing trainer rates gave no
   consistent change. Beyond the six base features, only the trainer rate and the three-run
   finishing slope were selected in two of the three folds. The new features describe horses
   better but add nothing the market lacks.
3. **Sub-question 2 (LightGBM vs conditional logit).** LightGBM was marginally better on its own
   (2.1931 vs 2.1945) but not as a complement to the market. Pooled gains over the calibrated
   market were +0.0011 for market + CL, +0.0008 for market + LightGBM and +0.0010 for both, the
   best blend differed by fold, and the model weights changed sign between folds. We found no
   evidence that non-linearity captures extra residual signal.
4. **The pipeline is trustworthy.** Leakage tests, source isolation, a run-gate and a
   bit-identical rebuild make every number above reproducible from a clean commit.

What we demonstrated is a leakage-safe, reproducible forecasting pipeline and an upper bound:
with 927 races, the 95 % intervals rule out improvements over the calibrated market larger than
about 0.003–0.004 log loss on these folds. We did not demonstrate an edge, and we did not evaluate betting, so nothing
here supports profitable wagering after the HKJC takeout.

The most useful next step is a prospective test: score the frozen configuration once on races
run after the freeze date, which our scoring gate already enforces. More continuous HKJC data,
covering the 2020–2025 gap, would narrow the intervals. Beyond that, and as speculation rather
than a result, information that public results do not contain, such as sectional times,
time-stamped ratings or odds movements, seems more likely to add signal than further
transformations of the same results.

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

[8] C. R. Harris et al., "Array programming with NumPy," *Nature*, vol. 585, pp. 357–362, 2020.

[9] W. McKinney, "Data structures for statistical computing in Python," in *Proc. 9th Python in
Science Conf.*, 2010, pp. 56–61.
