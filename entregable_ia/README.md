# Entregable final · Diplomado en Inteligencia Artificial

**Alerta temprana de ausentismo escolar con IA sobre la plataforma School_Plus**

Repositorio: https://github.com/caroaponte2614-tech/Sistema_De_Asistencia

| Archivo | Entregable |
|---|---|
| `PROYECTO_FINAL_IA.pdf` | Documento integrado: entregables 1 a 7, rúbrica y guía de entrega |
| `04_MODELO.ipynb` / `04_modelo.py` | Pipeline ejecutable (E3 análisis, E4 modelo, E5 evaluación) |
| `04_MODELO.pkl` | Modelo Random Forest guardado (modelo + variables + umbral) |
| `resultados.json` | Métricas que cita el documento |
| `fig/` | Gráficos del análisis y de la evaluación |
| `data/` | CSV original (con duplicados), CSV limpio y dataset del modelo |

## Cómo ejecutar

```bash
pip install pandas scikit-learn matplotlib joblib
cd entregable_ia
python 04_modelo.py          # o abrir 04_MODELO.ipynb y ejecutar todo
```

Regenera `data/`, `fig/`, `04_MODELO.pkl` y `resultados.json` (semilla fija: mismos resultados).

## Cargar el modelo

```python
import joblib
m = joblib.load("04_MODELO.pkl")
prob = m["modelo"].predict_proba(X[m["features"]])[:, 1]
alerta = prob >= m["umbral"]
```

> Los datos son **sintéticos** con el esquema exacto de `asistencia_registro` (migración V25), para no
> exponer datos de menores. El pipeline lee el mismo CSV que exporta `/docencia/asistencia/reportes`,
> así que se reemplaza por datos reales sin cambiar el código.

La plataforma (asistencia con QR) está en la raíz del repositorio; ver el `README.md` principal.
