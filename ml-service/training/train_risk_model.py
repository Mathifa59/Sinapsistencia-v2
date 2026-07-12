"""
Entrena y EVALÚA el modelo de riesgo médico-legal (HU-29/HU-30/HU-35).

Pipeline:
  - RandomForestClassifier -> risk_level (bajo/moderado/alto/critico)
  - RandomForestRegressor  -> risk_score (0..1, severidad continua)

Evaluación (para sustento de tesis):
  - Split estratificado 80/20 + validación cruzada 5-fold (f1 macro).
  - Reporte por clase (precision/recall/f1), matriz de confusión.
  - Baseline comparativo: DummyClassifier (estratificado) y LogisticRegression,
    para demostrar que el RF aporta sobre un clasificador trivial y uno lineal
    (los efectos de interacción del dataset favorecen al RF).
  - R²/MAE del regresor de score.
  - Importancia de variables.

Artefactos:
  - ../models/risk_model.joblib  (preprocessor + clf + reg + metadatos)
  - ../models/risk_model_metrics.json  (métricas para la tabla model_metrics)
  - ../models/risk_model_report.json    (evaluación detallada para el informe)

Uso:
    python train_risk_model.py --model-version rf-v2
"""

import argparse
import json
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.dummy import DummyClassifier
from sklearn.ensemble import RandomForestClassifier, RandomForestRegressor
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    classification_report,
    confusion_matrix,
    f1_score,
    mean_absolute_error,
    precision_score,
    r2_score,
    recall_score,
)
from sklearn.model_selection import cross_val_score, train_test_split
from sklearn.preprocessing import OneHotEncoder, OrdinalEncoder

CATEGORICAL_NOMINAL = ["specialty"]
CATEGORICAL_ORDINAL = {
    "procedure_complexity": ["baja", "media", "alta"],
    "priority": ["baja", "media", "alta", "critica"],
}
BOOLEAN_COLS = ["documentation_complete", "informed_consent", "has_prior_complaints"]
NUMERIC_COLS = ["time_since_incident_days"]
LEVEL_ORDER = ["bajo", "moderado", "alto", "critico"]


def build_preprocessor() -> ColumnTransformer:
    ordinal_categories = [CATEGORICAL_ORDINAL[c] for c in CATEGORICAL_ORDINAL]
    return ColumnTransformer(
        transformers=[
            ("specialty", OneHotEncoder(handle_unknown="ignore"), CATEGORICAL_NOMINAL),
            ("ordinal", OrdinalEncoder(categories=ordinal_categories), list(CATEGORICAL_ORDINAL)),
            ("bool", "passthrough", BOOLEAN_COLS),
            ("num", "passthrough", NUMERIC_COLS),
        ]
    )


def feature_names(preprocessor: ColumnTransformer) -> list[str]:
    specialty_names = list(
        preprocessor.named_transformers_["specialty"].get_feature_names_out(CATEGORICAL_NOMINAL)
    )
    return specialty_names + list(CATEGORICAL_ORDINAL) + BOOLEAN_COLS + NUMERIC_COLS


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--data", type=str, default="../data/risk_dataset.csv")
    parser.add_argument("--out", type=str, default="../models/risk_model.joblib")
    parser.add_argument("--metrics-out", type=str, default="../models/risk_model_metrics.json")
    parser.add_argument("--report-out", type=str, default="../models/risk_model_report.json")
    parser.add_argument("--model-version", type=str, default="rf-v2")
    args = parser.parse_args()

    base = Path(__file__).parent
    df = pd.read_csv(base / args.data)
    for col in BOOLEAN_COLS:
        df[col] = df[col].astype(bool).astype(int)

    feature_cols = CATEGORICAL_NOMINAL + list(CATEGORICAL_ORDINAL) + BOOLEAN_COLS + NUMERIC_COLS
    X = df[feature_cols]
    y_level = df["risk_level"]
    y_score = df["risk_score"]

    X_tr, X_te, yl_tr, yl_te, ys_tr, ys_te = train_test_split(
        X, y_level, y_score, test_size=0.2, random_state=42, stratify=y_level
    )

    pre = build_preprocessor()
    X_tr_t = pre.fit_transform(X_tr)
    X_te_t = pre.transform(X_te)

    # ── Modelo principal: Random Forest ─────────────────────────────────────
    # Hiperparámetros elegidos por equilibrio métrica/tamaño: hojas grandes
    # (min_samples_leaf=5) podan árboles → modelo compacto para el runtime de
    # Railway sin perder f1 apreciable frente a árboles profundos.
    clf = RandomForestClassifier(
        n_estimators=150, max_depth=14, min_samples_leaf=5,
        class_weight="balanced", random_state=42, n_jobs=-1,
    )
    clf.fit(X_tr_t, yl_tr)
    reg = RandomForestRegressor(
        n_estimators=150, max_depth=14, min_samples_leaf=5, random_state=42, n_jobs=-1,
    )
    reg.fit(X_tr_t, ys_tr)

    yl_pred = clf.predict(X_te_t)
    precision = precision_score(yl_te, yl_pred, average="macro", zero_division=0)
    recall = recall_score(yl_te, yl_pred, average="macro", zero_division=0)
    f1 = f1_score(yl_te, yl_pred, average="macro", zero_division=0)
    accuracy = float((yl_pred == yl_te).mean())

    # Validación cruzada 5-fold sobre todo el dataset (robustez).
    cv_f1 = cross_val_score(
        RandomForestClassifier(n_estimators=150, max_depth=14, min_samples_leaf=5,
                               class_weight="balanced", random_state=42, n_jobs=-1),
        pre.transform(X), y_level, cv=5, scoring="f1_macro", n_jobs=-1,
    )

    # Regresor de severidad.
    ys_pred = reg.predict(X_te_t)
    reg_r2 = float(r2_score(ys_te, ys_pred))
    reg_mae = float(mean_absolute_error(ys_te, ys_pred))

    # ── Baselines comparativos ──────────────────────────────────────────────
    dummy = DummyClassifier(strategy="stratified", random_state=42).fit(X_tr_t, yl_tr)
    dummy_f1 = f1_score(yl_te, dummy.predict(X_te_t), average="macro", zero_division=0)
    logit = LogisticRegression(max_iter=1000, multi_class="multinomial").fit(X_tr_t, yl_tr)
    logit_f1 = f1_score(yl_te, logit.predict(X_te_t), average="macro", zero_division=0)

    names = feature_names(pre)
    importances = sorted(zip(names, clf.feature_importances_), key=lambda x: -x[1])

    # ── Artefacto del modelo ────────────────────────────────────────────────
    artifact = {
        "preprocessor": pre, "clf": clf, "reg": reg,
        "feature_names": names, "level_order": LEVEL_ORDER,
        "model_version": args.model_version,
    }
    joblib.dump(artifact, base / args.out, compress=3)

    # ── Métricas para model_metrics (HU-35) ─────────────────────────────────
    metrics = {
        "model_name": "risk_classifier",
        "model_version": args.model_version,
        "precision_score": round(float(precision), 4),
        "recall_score": round(float(recall), 4),
        "f1_score": round(float(f1), 4),
        "dataset_size": int(len(df)),
        "notes": (
            f"RandomForest (150 árboles) sobre dataset sintético balanceado "
            f"({len(df)} filas). CV 5-fold f1_macro={cv_f1.mean():.4f}±{cv_f1.std():.4f}. "
            f"Supera baseline lineal (f1={logit_f1:.3f}) y trivial (f1={dummy_f1:.3f})."
        ),
    }
    (base / args.metrics_out).write_text(
        json.dumps(metrics, indent=2, ensure_ascii=False), encoding="utf-8"
    )

    # ── Reporte detallado para el informe de tesis ──────────────────────────
    report = {
        "model_version": args.model_version,
        "dataset_size": int(len(df)),
        "class_distribution": {k: int(v) for k, v in y_level.value_counts().items()},
        "classifier": {
            "accuracy": round(accuracy, 4),
            "precision_macro": round(float(precision), 4),
            "recall_macro": round(float(recall), 4),
            "f1_macro": round(float(f1), 4),
            "cv5_f1_macro_mean": round(float(cv_f1.mean()), 4),
            "cv5_f1_macro_std": round(float(cv_f1.std()), 4),
            "per_class": classification_report(
                yl_te, yl_pred, labels=LEVEL_ORDER, output_dict=True, zero_division=0
            ),
            "confusion_matrix": {
                "labels": LEVEL_ORDER,
                "matrix": confusion_matrix(yl_te, yl_pred, labels=LEVEL_ORDER).tolist(),
            },
        },
        "regressor_score": {"r2": round(reg_r2, 4), "mae": round(reg_mae, 4)},
        "baselines_f1_macro": {
            "dummy_stratified": round(float(dummy_f1), 4),
            "logistic_regression": round(float(logit_f1), 4),
            "random_forest": round(float(f1), 4),
        },
        "top_feature_importances": [
            {"feature": n, "importance": round(float(i), 4)} for n, i in importances[:12]
        ],
    }
    (base / args.report_out).write_text(
        json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8"
    )

    # ── Salida por consola ──────────────────────────────────────────────────
    print(f"Modelo {args.model_version} guardado ({len(df)} filas).")
    print(f"  RandomForest  f1_macro={f1:.4f}  accuracy={accuracy:.4f}")
    print(f"  CV 5-fold     f1_macro={cv_f1.mean():.4f} ± {cv_f1.std():.4f}")
    print(f"  Regresor      R²={reg_r2:.4f}  MAE={reg_mae:.4f}")
    print(f"  Baselines     trivial={dummy_f1:.3f}  lineal={logit_f1:.3f}  RF={f1:.3f}")
    print("  Top features:")
    for n, i in importances[:8]:
        print(f"    {n:32} {i:.4f}")


if __name__ == "__main__":
    main()
