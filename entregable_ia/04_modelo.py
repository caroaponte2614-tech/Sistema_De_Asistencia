"""
School_Plus · Alerta temprana de ausentismo
Pipeline completo: datos -> EDA -> limpieza -> features -> modelo -> evaluacion.
Equivale al notebook 03/04/05 del proyecto del diplomado.
"""
import json, os
import numpy as np
import pandas as pd
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from sklearn.model_selection import train_test_split, StratifiedKFold, cross_val_score
from sklearn.linear_model import LogisticRegression
from sklearn.ensemble import RandomForestClassifier
from sklearn.preprocessing import StandardScaler
from sklearn.pipeline import make_pipeline
from sklearn.metrics import (precision_score, recall_score, f1_score, roc_auc_score,
                             confusion_matrix, roc_curve, accuracy_score)
import joblib

OUT = os.path.dirname(os.path.abspath(__file__))
FIG = os.path.join(OUT, "fig"); os.makedirs(FIG, exist_ok=True)
DATA = os.path.join(OUT, "data"); os.makedirs(DATA, exist_ok=True)
rng = np.random.default_rng(2026)
R = {}

AZUL, AZUL2, ROJO, GRIS, VERDE, AMBAR = "#2563EB", "#0EA5E9", "#B91C1C", "#64748B", "#15803D", "#B45309"
plt.rcParams.update({"font.size": 9, "axes.spines.top": False, "axes.spines.right": False,
                     "axes.titleweight": "bold", "axes.titlesize": 10, "figure.dpi": 150})

# ── 1. Datos: simulador con el esquema exacto de asistencia_registro (V25) ──────────
N_EST, SEMANAS, CLASES_POR_EST, SES_SEMANA = 480, 16, 5, 3
grados = np.repeat(np.arange(6, 12), N_EST // 6)
grupo = np.tile(np.repeat(["A", "B"], N_EST // 12), 6)
est = pd.DataFrame({
    "estudiante_id": [f"E{i:04d}" for i in range(N_EST)],
    "grado": grados, "grupo": grupo,
    "jornada": np.where(grupo == "A", "Mañana", "Tarde"),
    "sin_celular": rng.random(N_EST) < 0.10,
})
base = np.clip(rng.beta(20, 1.6, N_EST), 0.6, 0.995)
deriva = rng.random(N_EST) < 0.24                       # estudiantes que "se van cayendo"
ini = rng.integers(3, 14, N_EST)
mag = rng.uniform(0.10, 0.45, N_EST)
enfermo = np.where(rng.random(N_EST) < 0.18, rng.integers(1, 17, N_EST), -1)
cambios_cel = rng.poisson(0.25 + 0.5 * deriva)

dias = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"]
filas = []
for i in range(N_EST):
    for c in range(CLASES_POR_EST):
        for w in range(1, SEMANAS + 1):
            for s in range(SES_SEMANA):
                dia = (c + s * 2) % 5
                franja = (c + s) % 6            # 0..5 = bloque del día
                p = base[i]
                if deriva[i] and w >= ini[i]:
                    p -= mag[i] * min(1.0, (w - ini[i] + 1) / 4)
                if dia in (0, 4): p -= 0.03
                if franja == 5: p -= 0.025
                presente = rng.random() < p
                obs = None
                if w == enfermo[i] and rng.random() < 0.85:
                    estado, metodo, obs = "JUSTIFICADO", "CIERRE", "Incapacidad médica"
                elif presente:
                    tarde = rng.random() < (0.05 + 0.09 * deriva[i] + 0.02 * (franja == 0))
                    estado = "TARDE" if tarde else "PRESENTE"
                    if est.sin_celular[i]:
                        # el profesor lo marca a mano... o se le olvida (sesgo de registro)
                        if rng.random() < 0.08:
                            estado, metodo = "AUSENTE", "CIERRE"
                        else:
                            metodo = "MANUAL"
                    else:
                        metodo = "QR" if rng.random() < 0.86 else "CODIGO"
                else:
                    estado, metodo = "AUSENTE", "CIERRE"
                    if rng.random() < 0.07:
                        estado, obs = "JUSTIFICADO", rng.choice(
                            ["Cita médica", "Calamidad familiar", "Representación deportiva", "Transporte"])
                filas.append((f"S{i % 24:02d}-{c}-{w:02d}-{s}", est.estudiante_id[i], f"C{grados[i]}{grupo[i]}{c}",
                              w, dias[dia], franja, estado, metodo, obs))

df = pd.DataFrame(filas, columns=["sesion_id", "estudiante_id", "clase_id", "semana", "dia_semana",
                                  "franja", "estado", "metodo", "observacion"])
df["dispositivo_id"] = np.where(df.metodo.isin(["QR", "CODIGO"]), "D-" + df.estudiante_id, None)
df["ip"] = np.where(df.metodo.isin(["QR", "CODIGO"]), "10.0.%d.%d" % (1, 1), None)
# defecto realista: dos exportaciones de reportes con rangos de fechas solapados
dup = df.sample(frac=0.006, random_state=1)
crudo = pd.concat([df, dup], ignore_index=True)
crudo.to_csv(os.path.join(DATA, "asistencia_original.csv"), index=False)

# ── 2. EDA ──────────────────────────────────────────────────────────────────────────
R["eda"] = {
    "filas": int(len(crudo)), "columnas": int(crudo.shape[1]),
    "estudiantes": int(crudo.estudiante_id.nunique()), "clases": int(crudo.clase_id.nunique()),
    "duplicados": int(crudo.duplicated().sum()),
    "nulos": {c: round(100 * crudo[c].isna().mean(), 1) for c in ["observacion", "dispositivo_id", "ip", "estado"]},
    "estados": {k: round(100 * v, 1) for k, v in crudo.estado.value_counts(normalize=True).items()},
    "metodos": {k: round(100 * v, 1) for k, v in crudo.metodo.value_counts(normalize=True).items()},
}

# ── 3. Limpieza ────────────────────────────────────────────────────────────────────
limpio = crudo.drop_duplicates().copy()
limpio["asistio"] = limpio.estado.isin(["PRESENTE", "TARDE"]).astype(int)
limpio["cuenta"] = (limpio.estado != "JUSTIFICADO").astype(int)
limpio["tarde"] = (limpio.estado == "TARDE").astype(int)
limpio["ausente"] = (limpio.estado == "AUSENTE").astype(int)
limpio = limpio.merge(est[["estudiante_id", "grado", "jornada", "sin_celular"]], on="estudiante_id")
R["limpieza"] = {"eliminados": int(len(crudo) - len(limpio)), "final": int(len(limpio))}

# ── 4. Features (semanas 1-8) y etiqueta (semanas 9-16) ────────────────────────────
def pct(g):
    c = g.cuenta.sum()
    return g.asistio.sum() / c if c else np.nan

h1, h2 = limpio[limpio.semana <= 8], limpio[limpio.semana > 8]

def racha_max(serie):
    m = a = 0
    for v in serie:
        a = a + 1 if v else 0
        m = max(m, a)
    return m

def features(g):
    sem = g.groupby("semana").apply(pct, include_groups=False).dropna()
    pend = np.polyfit(sem.index, sem.values, 1)[0] if len(sem) > 2 else 0
    orden = g.sort_values(["clase_id", "semana", "sesion_id"])
    return pd.Series({
        "pct_asistencia": pct(g),
        "pct_tardanza": g.tarde.sum() / max(1, g.asistio.sum()),
        "ausencias_ult2sem": g[g.semana >= 7].ausente.sum(),
        "racha_max_ausencias": orden.groupby("clase_id").ausente.apply(racha_max).max(),
        "tendencia_semanal": pend,
        "variabilidad_semanal": sem.std(),
        "justificadas": (g.estado == "JUSTIFICADO").sum(),
        "pct_lunes_viernes_aus": g[g.dia_semana.isin(["MONDAY", "FRIDAY"])].ausente.mean(),
    })

X = h1.groupby("estudiante_id").apply(features, include_groups=False)
X["cambios_celular"] = cambios_cel
y_pct = h2.groupby("estudiante_id").apply(pct, include_groups=False)
y = (y_pct < 0.80).astype(int).rename("riesgo")
ds = X.join(y).join(est.set_index("estudiante_id")[["grado", "jornada", "sin_celular"]])
ds.to_csv(os.path.join(DATA, "dataset_modelo_limpio.csv"))
limpio.drop(columns=["ip"]).to_csv(os.path.join(DATA, "asistencia_limpio.csv"), index=False)

R["stats"] = {
    "pct_medio": round(100 * ds.pct_asistencia.mean(), 1),
    "pct_mediana": round(100 * ds.pct_asistencia.median(), 1),
    "pct_p10": round(100 * ds.pct_asistencia.quantile(0.10), 1),
    "tardanza_media": round(100 * ds.pct_tardanza.mean(), 1),
    "prevalencia_riesgo": round(100 * ds.riesgo.mean(), 1),
    "n_riesgo": int(ds.riesgo.sum()),
    "corr_pct_y": round(ds[["pct_asistencia", "riesgo"]].corr().iloc[0, 1], 2),
    "corr_tend_y": round(ds[["tendencia_semanal", "riesgo"]].corr().iloc[0, 1], 2),
    "corr_tard_y": round(ds[["pct_tardanza", "riesgo"]].corr().iloc[0, 1], 2),
    "corr_cel_y": round(ds[["cambios_celular", "riesgo"]].corr().iloc[0, 1], 2),
    "pct_sin_cel": round(100 * ds.sin_celular.mean(), 1),
    "aus_sin_cel": round(100 * (1 - limpio[limpio.sin_celular].pipe(pct)), 1),
    "aus_con_cel": round(100 * (1 - limpio[~limpio.sin_celular].pipe(pct)), 1),
    "riesgo_sin_cel": round(100 * ds[ds.sin_celular].riesgo.mean(), 1),
    "riesgo_con_cel": round(100 * ds[~ds.sin_celular].riesgo.mean(), 1),
    "riesgo_manana": round(100 * ds[ds.jornada == "Mañana"].riesgo.mean(), 1),
    "riesgo_tarde": round(100 * ds[ds.jornada == "Tarde"].riesgo.mean(), 1),
}

# ── Gráficos EDA (6) ───────────────────────────────────────────────────────────────
fig, ax = plt.subplots(figsize=(4.2, 2.6))
ax.hist(100 * ds.pct_asistencia, bins=30, color=AZUL)
ax.axvline(80, color=ROJO, ls="--", lw=1); ax.text(80.5, ax.get_ylim()[1] * .9, "umbral 80 %", color=ROJO, fontsize=8)
ax.set(title="1. Asistencia por estudiante (sem. 1-8)", xlabel="% asistencia", ylabel="estudiantes")
fig.tight_layout(); fig.savefig(f"{FIG}/g1.png"); plt.close(fig)

semanal = limpio.merge(ds[["riesgo"]], left_on="estudiante_id", right_index=True) \
    .groupby(["riesgo", "semana"]).apply(pct, include_groups=False).unstack(0) * 100
fig, ax = plt.subplots(figsize=(4.2, 2.6))
ax.plot(semanal.index, semanal[0], color=AZUL, marker="o", ms=3, label="sin riesgo")
ax.plot(semanal.index, semanal[1], color=ROJO, marker="o", ms=3, label="en riesgo (real)")
ax.axvspan(8.5, 16.5, color=GRIS, alpha=.08); ax.text(9, semanal.min().min() + 1, "periodo a predecir", fontsize=7, color=GRIS)
ax.set(title="2. Asistencia semanal promedio", xlabel="semana", ylabel="%"); ax.legend(frameon=False, fontsize=7)
fig.tight_layout(); fig.savefig(f"{FIG}/g2.png"); plt.close(fig)

orden_d = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"]
heat = limpio[limpio.cuenta == 1].pivot_table(index="dia_semana", columns="franja", values="ausente", aggfunc="mean").loc[orden_d] * 100
fig, ax = plt.subplots(figsize=(4.2, 2.6))
im = ax.imshow(heat.values, cmap="Blues", aspect="auto")
ax.set_xticks(range(6), [f"B{i+1}" for i in range(6)]); ax.set_yticks(range(5), ["Lun", "Mar", "Mié", "Jue", "Vie"])
for (r, c), v in np.ndenumerate(heat.values): ax.text(c, r, f"{v:.1f}", ha="center", va="center", fontsize=7)
ax.set(title="3. % ausencia por día y bloque horario"); fig.colorbar(im, ax=ax, shrink=.8)
fig.tight_layout(); fig.savefig(f"{FIG}/g3.png"); plt.close(fig)

mt = pd.crosstab(limpio.metodo, limpio.estado, normalize="index").reindex(["QR", "CODIGO", "MANUAL", "CIERRE"]) * 100
fig, ax = plt.subplots(figsize=(4.2, 2.6))
izq = np.zeros(len(mt))
for est_, col in zip(["PRESENTE", "TARDE", "AUSENTE", "JUSTIFICADO"], [AZUL, AZUL2, ROJO, AMBAR]):
    if est_ in mt: ax.barh(mt.index, mt[est_], left=izq, color=col, label=est_.capitalize()); izq += mt[est_].values
ax.set(title="4. Estado según método de registro", xlabel="% de registros")
ax.legend(frameon=False, fontsize=6, ncol=4, loc="lower center", bbox_to_anchor=(.5, -.55))
fig.tight_layout(); fig.savefig(f"{FIG}/g4.png"); plt.close(fig)

cols = ["pct_asistencia", "pct_tardanza", "ausencias_ult2sem", "racha_max_ausencias", "tendencia_semanal",
        "variabilidad_semanal", "justificadas", "cambios_celular", "riesgo"]
cm_ = ds[cols].astype(float).corr()
fig, ax = plt.subplots(figsize=(4.2, 3.2))
im = ax.imshow(cm_, cmap="RdBu_r", vmin=-1, vmax=1)
et = ["%asist", "%tarde", "aus2sem", "racha", "tendenc", "variab", "justif", "cel", "RIESGO"]
ax.set_xticks(range(len(et)), et, rotation=60, fontsize=7); ax.set_yticks(range(len(et)), et, fontsize=7)
for (r, c), v in np.ndenumerate(cm_.values): ax.text(c, r, f"{v:.1f}", ha="center", va="center", fontsize=5.5)
ax.set_title("5. Correlaciones entre variables"); fig.colorbar(im, ax=ax, shrink=.7)
fig.tight_layout(); fig.savefig(f"{FIG}/g5.png"); plt.close(fig)

fig, ax = plt.subplots(figsize=(4.2, 3.2))
grp = [("Con celular", ds[~ds.sin_celular]), ("Sin celular", ds[ds.sin_celular]),
       ("Mañana", ds[ds.jornada == "Mañana"]), ("Tarde", ds[ds.jornada == "Tarde"])]
vals = [100 * g.riesgo.mean() for _, g in grp]; ns = [len(g) for _, g in grp]
b = ax.bar([g for g, _ in grp], vals, color=[AZUL, AMBAR, AZUL, AZUL2])
for bb, v, n in zip(b, vals, ns): ax.text(bb.get_x() + bb.get_width() / 2, v + .5, f"{v:.0f}%\nn={n}", ha="center", fontsize=7)
ax.set(title="6. Prevalencia de riesgo por grupo", ylabel="% en riesgo", ylim=(0, max(vals) * 1.35))
fig.tight_layout(); fig.savefig(f"{FIG}/g6.png"); plt.close(fig)

# ── 5. Modelo ─────────────────────────────────────────────────────────────────────
FEATS = ["pct_asistencia", "pct_tardanza", "ausencias_ult2sem", "racha_max_ausencias", "tendencia_semanal",
         "variabilidad_semanal", "justificadas", "pct_lunes_viernes_aus", "cambios_celular"]
Xm, ym = ds[FEATS].fillna(0), ds.riesgo
Xtr, Xte, ytr, yte, itr, ite = train_test_split(Xm, ym, ds.index, test_size=0.3, stratify=ym, random_state=7)
cv = StratifiedKFold(5, shuffle=True, random_state=7)
modelos = {
    "Regresión logística": make_pipeline(StandardScaler(), LogisticRegression(class_weight="balanced", max_iter=1000)),
    "Random Forest": RandomForestClassifier(n_estimators=300, max_depth=6, min_samples_leaf=5,
                                            class_weight="balanced", random_state=7),
}
R["cv"] = {}
for n, m in modelos.items():
    s = cross_val_score(m, Xtr, ytr, cv=cv, scoring="roc_auc")
    R["cv"][n] = {"auc_media": round(s.mean(), 3), "auc_std": round(s.std(), 3)}

def metr(y_true, pred, prob=None):
    d = {"precision": precision_score(y_true, pred, zero_division=0), "recall": recall_score(y_true, pred),
         "f1": f1_score(y_true, pred), "accuracy": accuracy_score(y_true, pred)}
    if prob is not None: d["auc"] = roc_auc_score(y_true, prob)
    tn, fp, fn, tp = confusion_matrix(y_true, pred).ravel()
    d.update(tp=int(tp), fp=int(fp), fn=int(fn), tn=int(tn))
    return {k: (round(v, 3) if isinstance(v, float) else v) for k, v in d.items()}

R["test"], probs = {}, {}
for n, m in modelos.items():
    m.fit(Xtr, ytr); probs[n] = m.predict_proba(Xte)[:, 1]
    R["test"][n] = metr(yte, (probs[n] >= .5).astype(int), probs[n])

# baseline: la regla que hoy usa coordinación a ojo
regla = ((Xte.pct_asistencia < .85) | (Xte.racha_max_ausencias >= 3)).astype(int)
R["test"]["Regla manual (coordinación)"] = metr(yte, regla)

elegido = "Random Forest" if R["cv"]["Random Forest"]["auc_media"] >= R["cv"]["Regresión logística"]["auc_media"] else "Regresión logística"
R["elegido"] = elegido
mod = modelos[elegido]; p = probs[elegido]
joblib.dump({"modelo": mod, "features": FEATS, "umbral": .5}, os.path.join(OUT, "04_MODELO.pkl"))

# importancia
if elegido == "Random Forest":
    imp = pd.Series(mod.feature_importances_, FEATS).sort_values()
else:
    imp = pd.Series(np.abs(mod[-1].coef_[0]), FEATS).sort_values()
R["importancia"] = {k: round(v, 3) for k, v in imp.sort_values(ascending=False).items()}
lr = modelos["Regresión logística"]
R["coef_lr"] = {k: round(v, 2) for k, v in zip(FEATS, lr[-1].coef_[0])}

# ── 6. Evaluación ─────────────────────────────────────────────────────────────────
fig, axs = plt.subplots(1, 2, figsize=(8.4, 2.9))
cmx = confusion_matrix(yte, (p >= .5).astype(int))
axs[0].imshow(cmx, cmap="Blues")
for (r, c), v in np.ndenumerate(cmx): axs[0].text(c, r, v, ha="center", va="center", fontsize=12,
                                                  color="white" if v > cmx.max() / 2 else "black")
axs[0].set_xticks([0, 1], ["Pred. sin riesgo", "Pred. riesgo"]); axs[0].set_yticks([0, 1], ["Real sin riesgo", "Real riesgo"])
axs[0].set_title(f"Matriz de confusión · {elegido}")
for n, col in zip(modelos, [AZUL2, AZUL]):
    fpr, tpr, _ = roc_curve(yte, probs[n]); axs[1].plot(fpr, tpr, color=col, label=f"{n} (AUC {R['test'][n]['auc']:.2f})")
rb = R["test"]["Regla manual (coordinación)"]
axs[1].scatter([rb["fp"] / (rb["fp"] + rb["tn"])], [rb["recall"]], color=ROJO, zorder=3, label="Regla manual")
axs[1].plot([0, 1], [0, 1], color=GRIS, ls=":", lw=1)
axs[1].set(title="Curva ROC (conjunto de prueba)", xlabel="Falsos positivos", ylabel="Sensibilidad (recall)")
axs[1].legend(frameon=False, fontsize=7)
fig.tight_layout(); fig.savefig(f"{FIG}/g7.png"); plt.close(fig)

fig, ax = plt.subplots(figsize=(4.2, 2.8))
nombres = {"pct_asistencia": "% asistencia", "pct_tardanza": "% tardanza", "ausencias_ult2sem": "ausencias últ. 2 sem",
           "racha_max_ausencias": "racha máx. ausencias", "tendencia_semanal": "tendencia semanal",
           "variabilidad_semanal": "variabilidad semanal", "justificadas": "faltas justificadas",
           "pct_lunes_viernes_aus": "% aus. lunes/viernes", "cambios_celular": "cambios de celular"}
ax.barh([nombres[i] for i in imp.index], imp.values, color=AZUL)
ax.set(title=f"Importancia de variables · {elegido}"); fig.tight_layout(); fig.savefig(f"{FIG}/g8.png"); plt.close(fig)

# equidad por grupo
te = ds.loc[ite].copy(); te["pred"] = (p >= .5).astype(int); te["prob"] = p
def grupo_m(g):
    tp = ((g.pred == 1) & (g.riesgo == 1)).sum(); fn = ((g.pred == 0) & (g.riesgo == 1)).sum()
    fp = ((g.pred == 1) & (g.riesgo == 0)).sum(); tn = ((g.pred == 0) & (g.riesgo == 0)).sum()
    return {"n": int(len(g)), "recall": round(tp / max(1, tp + fn), 2), "fpr": round(fp / max(1, fp + tn), 2),
            "tasa_alerta": round(g.pred.mean(), 2)}
R["equidad"] = {"Con celular": grupo_m(te[~te.sin_celular]), "Sin celular": grupo_m(te[te.sin_celular]),
                "Mañana": grupo_m(te[te.jornada == "Mañana"]), "Tarde": grupo_m(te[te.jornada == "Tarde"])}

# validación manual: 25 casos aleatorios del conjunto de prueba
muestra = te.sample(25, random_state=3)
muestra["ok"] = muestra.pred == muestra.riesgo
R["manual"] = {"aciertos": int(muestra.ok.sum()), "n": 25}
R["manual_casos"] = [
    {"id": i, "pct": round(100 * r.pct_asistencia, 1), "tend": round(100 * r.tendencia_semanal, 2),
     "racha": int(r.racha_max_ausencias), "aus2": int(r.ausencias_ult2sem), "prob": round(r.prob, 2),
     "pred": int(r.pred), "real": int(r.riesgo), "real_pct": round(100 * y_pct[i], 1), "sin_cel": bool(r.sin_celular)}
    for i, r in muestra.iterrows()]
# casos edge: errores con mayor confianza
err = te[te.pred != te.riesgo].copy(); err["conf"] = (err.prob - .5).abs()
R["edge"] = [
    {"id": i, "pct": round(100 * r.pct_asistencia, 1), "tend": round(100 * r.tendencia_semanal, 2),
     "racha": int(r.racha_max_ausencias), "aus2": int(r.ausencias_ult2sem), "prob": round(r.prob, 2),
     "pred": int(r.pred), "real": int(r.riesgo), "real_pct": round(100 * y_pct[i], 1),
     "sin_cel": bool(r.sin_celular), "justif": int(r.justificadas), "ini_deriva": int(ini[int(i[1:])]) if deriva[int(i[1:])] else None}
    for i, r in err.sort_values("conf", ascending=False).head(6).iterrows()]

# umbral alterno (prioriza no dejar pasar a nadie)
for u in (.3, .4):
    R[f"umbral_{u}"] = metr(yte, (p >= u).astype(int))
R["n_test"], R["n_train"] = int(len(yte)), int(len(ytr))

# ejemplos de predicción
R["ejemplos"] = [{"id": i, "prob": round(r.prob, 2), "pct": round(100 * r.pct_asistencia, 1),
                  "tend": round(100 * r.tendencia_semanal, 2), "real": int(r.riesgo)}
                 for i, r in te.sort_values("prob", ascending=False).iloc[[0, 5, 20, 60, 100, 140]].iterrows()]

with open(os.path.join(OUT, "resultados.json"), "w", encoding="utf-8") as f:
    json.dump(R, f, ensure_ascii=False, indent=1, default=lambda o: o.item() if hasattr(o, "item") else str(o))
print(json.dumps(R, ensure_ascii=False, indent=1, default=str))
