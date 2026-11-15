"""Slide figures for the presentation (market calibration, draw bias, primary gain, h_test).

Run from the repo root: python analysis/slide_figures.py  ->  docs/slides_figures/*.png
Numbers for the gain and h_test charts are copied from artifacts/folds/ablation_metrics.csv
and analysis/final_scoring_results/h_test/report.md.
"""
import sys, numpy as np, pandas as pd, matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib import font_manager as fm
OUT=sys.argv[1] if len(sys.argv) > 1 else "docs/slides_figures"
for f in fm.findSystemFonts():
    if "Inter" in f: fm.fontManager.addfont(f)
INK="#232831"; MUTED="#5B6470"; GRID="#E3E6EA"; OR="#D9661F"; BL="#3F6FB5"; GR="#9AA1A9"
plt.rcParams.update({"font.family":"Inter","font.size":10,"text.color":INK,"axes.labelcolor":MUTED,
  "xtick.color":MUTED,"ytick.color":MUTED,"axes.edgecolor":GRID,"axes.titleweight":"bold","axes.titlesize":11,
  "axes.titlecolor":INK,"axes.titlelocation":"left","axes.spines.top":False,"axes.spines.right":False,
  "axes.grid":True,"grid.color":GRID,"grid.linewidth":0.6,"figure.dpi":200,"savefig.dpi":200})
r=pd.read_parquet('data/clean data/runners_merged.parquet'); r['race_date']=pd.to_datetime(r.race_date)
s=r[r.race_valid.astype(bool)&r.started.astype(bool)&(r.race_date<'2026-04-01')].copy()
s['n']=s.groupby('race_id').race_id.transform('size'); s=s[s.n>=2]
s['win']=s.is_winner.astype(float)
# ---------- A: calibration + favourite-longshot (slide 2)
m=s[s.market_prob.astype(float)>0].copy(); m['mp']=m.market_prob.astype(float)
m=m[m.groupby('race_id').mp.transform('size')==m.n]; m['mp']/=m.groupby('race_id').mp.transform('sum')
fig,(a1,a2)=plt.subplots(1,2,figsize=(7.2,3.04),gridspec_kw={"wspace":0.28})
for src,col,lab in (("kaggle",OR,"Kaggle 2017–20"),("hkjc",BL,"HKJC Sep 2025–Mar 2026")):
    d=m[m.source==src].copy(); d['b']=pd.qcut(d.mp,10,labels=False)
    g=d.groupby('b').agg(x=('mp','mean'),y=('win','mean'),k=('win','size'))
    se=np.sqrt(g.y*(1-g.y)/g.k)
    a1.errorbar(g.x,g.y,yerr=1.96*se,fmt='o',ms=4,color=col,ecolor=col,elinewidth=1,capsize=0,label=f"{lab} (n = {len(d):,})")
a1.plot([0,0.45],[0,0.45],ls='--',color=GR,lw=1,label="Perfect calibration")
a1.set(xlim=(0,0.45),ylim=(0,0.45),xlabel="Market probability",ylabel="Actual win rate")
a1.set_title("Market probability vs actual win rate"); a1.legend(frameon=False,fontsize=7.5,loc="upper left")
m['dec']=pd.qcut(m.mp,10,labels=False)+1
g=m.groupby('dec').agg(w=('win','sum'),e=('mp','sum'))
g['r']=g.w/g.e; lo=(g.w-1.96*np.sqrt(g.w))/g.e; hi=(g.w+1.96*np.sqrt(g.w))/g.e
a2.bar(g.index,g.r,width=0.72,color=OR,zorder=2)
a2.errorbar(g.index,g.r,yerr=[g.r-lo,hi-g.r],fmt='none',ecolor=INK,elinewidth=0.9,capsize=2,zorder=3)
a2.axhline(1,ls='--',color=GR,lw=1,zorder=1)
a2.set(xticks=range(1,11),ylim=(0,1.52),xlabel="Market-probability decile (1 = longest odds)",ylabel="Actual / implied win rate")
a2.set_title("Actual-to-implied win rate by decile")
a2.text(1,hi.iloc[0]+0.04,f"{g.r.iloc[0]:.2f}",ha='center',fontsize=8.5,color=INK,fontweight='bold')
a2.text(0.5,1.42,f"Longest-odds decile wins {g.r.iloc[0]*100:.0f}% as often as implied",fontsize=8.5,color=INK,fontweight='bold')
a2.grid(axis='x',visible=False)
fig.text(0.01,-0.1,"Valid races before Apr 2026; error bars = 95% CI; dashed line = no bias.",fontsize=7,color=MUTED)
fig.savefig(f"{OUT}/slide2_market_calibration.png",bbox_inches='tight',facecolor='white'); plt.close(fig)
print("decile ratios",g.r.round(2).tolist())
# ---------- B: draw bias (slide 5)
s['draw']=pd.to_numeric(s.draw,errors='coerce'); s['exp']=1/s.n
panels=[("HV",1200,"Happy Valley 1200 m"),("HV",1650,"Happy Valley 1650 m"),("ST",1200,"Sha Tin 1200 m"),("ST",1400,"Sha Tin 1400 m")]
s['grp']=pd.cut(s.draw,[0,3,8,14],labels=["1–3","4–8","9–14"])
fig,axs=plt.subplots(1,4,figsize=(9.15,3.2),sharey=True,gridspec_kw={"wspace":0.1})
for ax,(tr,dist,name) in zip(axs,panels):
    d=s[(s.track_code==tr)&(s.distance_m.astype(float)==dist)]
    cnt=[]
    for j_,(src,col,lab) in enumerate((("kaggle",OR,"Kaggle 2017–20"),("hkjc",BL,"HKJC 2025–26"))):
        e=d[d.source==src]; cnt.append(e.race_id.nunique())
        g=e.groupby('grp',observed=False).agg(w=('win','sum'),x=('exp','sum'))
        rr=g.w/g.x; se=np.sqrt(g.w)/g.x; xs=np.arange(3)+(j_-0.5)*0.38
        ax.bar(xs,rr,width=0.36,color=col,label=lab,zorder=2)
        ax.errorbar(xs,rr,yerr=1.96*se,fmt='none',ecolor=INK,elinewidth=0.8,capsize=2,zorder=3)
        print(name,src,[round(v,2) for v in rr])
    ax.axhline(1,ls='--',color=GR,lw=1,zorder=1)
    ax.set_title(f"{name}\n{cnt[0]} + {cnt[1]} races",fontsize=9.5)
    ax.set(xticks=range(3),xticklabels=["1–3","4–8","9–14"],ylim=(0,2.2),xlabel="Draw (gate)"); ax.grid(axis='x',visible=False)
axs[0].set_ylabel("Actual / expected win rate")
axs[0].legend(frameon=False,fontsize=8,loc="upper right")
fig.text(0.01,-0.08,"Race counts: Kaggle + HKJC. Expected = 1 / starters; error bars = 95% CI; dashed line = no draw effect. Valid races before Apr 2026.",fontsize=7,color=MUTED)
fig.savefig(f"{OUT}/slide5_draw_bias.png",bbox_inches='tight',facecolor='white'); plt.close(fig)
# ---------- C: primary gains (slide 10)
rows=[("k1","eligible",379,-0.000414,-0.00274,0.00171),("k2","eligible",377,0.000653,-0.00104,0.00230),
      ("h1","watch-only",171,0.00506,-0.00581,0.01464),("Pooled","descriptive",927,0.00103,-0.00126,0.00331)]
fig,ax=plt.subplots(figsize=(4.1,3.05))
for i,(f,st,n,gn,l,h) in enumerate(rows):
    y=len(rows)-1-i; col=OR if st=="eligible" else GR
    ax.plot([l,h],[y,y],color=col,lw=2,solid_capstyle='round'); ax.plot(gn,y,'o',ms=7,color=col,mec='white',mew=1.2)
    ax.text(0.0205,y,f"{gn:+.4f}",va='center',ha='right',fontsize=9,color=INK)
ax.set_yticks(range(len(rows))); ax.set_yticklabels([f"{r_[0]}\n{r_[1]}, {r_[2]} races" for r_ in rows][::-1],fontsize=8.5)
ax.axvline(0,color=INK,lw=1); ax.set_xlim(-0.008,0.0205); ax.grid(axis='y',visible=False)
ax.set_xlabel("Gain vs calibrated market (log loss; > 0 = model better)",fontsize=8.5)
ax.set_title("Primary blend gain, 95% race-day interval",fontsize=10)
ax.text(0.0002,-0.75,"Every interval includes zero",color=OR,fontsize=9,fontweight='bold',ha='left')
ax.set_ylim(-1,3.5)
fig.savefig(f"{OUT}/slide10_primary_gain.png",bbox_inches='tight',facecolor='white'); plt.close(fig)
# ---------- D: h_test retrospective (slide 11)
fig,(a1,a2)=plt.subplots(1,2,figsize=(9.15,3.1),gridspec_kw={"width_ratios":[1.25,1],"wspace":0.45})
ms=[("Uniform (1/starters)",2.5123,GR),("Conditional logit alone",2.4053,BL),("LightGBM alone",2.2583,BL),
    ("Raw market",2.0565,OR),("Calibrated market",2.0566,OR),("Accepted blend (market + logit)",2.0593,OR)]
for i,(nm,v,c) in enumerate(ms[::-1]):
    a1.barh(i,v,color=c,height=0.66,zorder=2); a1.text(v+0.02,i,f"{v:.4f}",va='center',fontsize=8.5)
a1.set_yticks(range(len(ms))); a1.set_yticklabels([x[0] for x in ms][::-1],fontsize=8.5); a1.set_xlim(0,2.9)
a1.set_xlabel("Per-race log loss (lower is better)"); a1.set_title("Log loss on 400 h_test races"); a1.grid(axis='y',visible=False)
gs=[("Raw market",0.0001,-0.0039,0.0041),("Accepted blend",-0.0027,-0.0102,0.0051)]
for i,(nm,g_,l,h) in enumerate(gs):
    a2.plot([l,h],[i,i],color=OR,lw=2,solid_capstyle='round'); a2.plot(g_,i,'o',ms=7,color=OR,mec='white',mew=1.2)
    a2.text(0.0122,i,f"{g_:+.4f}\n[{l:+.4f}, {h:+.4f}]",va='center',ha='right',fontsize=8)
a2.axvline(0,color=INK,lw=1); a2.set_yticks([0,1]); a2.set_yticklabels([x[0] for x in gs],fontsize=8.5)
a2.set_xlim(-0.014,0.0125); a2.set_ylim(-0.7,1.7); a2.grid(axis='y',visible=False)
a2.set_xlabel("Gain vs calibrated market (> 0 = better)"); a2.set_title("Gain with 95% race-day interval")
fig.text(0.01,-0.1,"Apr–Oct 2026, 40 race days. Scored once; the window predates the freeze, so this is a retrospective check.",fontsize=7,color=MUTED)
fig.savefig(f"{OUT}/slide11_htest.png",bbox_inches='tight',facecolor='white'); plt.close(fig)
