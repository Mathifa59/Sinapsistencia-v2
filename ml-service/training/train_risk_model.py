"""
Entrena el modelo de evaluacion de riesgo medico-legal (HU-29/HU-30/HU-35).

Pipeline:
  - RandomForestClassifier -> risk_level (bajo/moderado/alto/critico)
  - RandomForestRegressor  -> risk_score (0..1, usado para el detalle/severidad)

Guarda en ../models/risk_model.joblib:
  - preprocessor (ColumnTransformer)
  - clf (RandomForestClassifier)
  - reg (RandomForestRegressor)
  - feature_names (post-preprocesamiento, para feature importance)
  - metrics (precision/recall/f1 del set de validacion)

Tambien escribe ../models/risk_model_metrics.json con las metricas, listas
para insertar en la tabla `model_metrics` (HU-35).

Uso:
    python train_risk_model.py [--data ../data/risk_dataset.csv] [--out ../models/risk_model.joblib]
"""

import argparse
import json
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.ensemble import RandomForestClassifier, RandomForestRegressor
from sklearn.metrics import f1_score, precision_score, recall_score
from sklearn.model_selection import train_test_split
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
    parser.add_argument("--model-version", type=str, default="rf-v1")
    args = parser.parse_args()

    base = Path(__file__).parent
    data_path = base / args.data
    out_path = base / args.out
    metrics_path = base / args.metrics_out
    out_path.parent.mkdir(parents=True, exist_ok=True)

    df = pd.read_csv(data_path)
    for col in BOOLEAN_COLS:
        df[col] = df[col].astype(bool).astype(int)

    feature_cols = CATEGORICAL_NOMINAL + list(CATEGORICAL_ORDINAL) + BOOLEAN_COLS + NUMERIC_COLS
    X = df[feature_cols]
    y_level = df["risk_level"]
    y_score = df["risk_score"]

    X_train, X_test, y_level_train, y_level_test, y_score_train, y_score_test = train_test_split(
        X, y_level, y_score, test_size=0.2, random_state=42, stratify=y_level
    )

    preprocessor = build_preprocessor()
    X_train_t = preprocessor.fit_transform(X_train)
    X_test_t = preprocessor.transform(X_test)

    clf = RandomForestClassifier(n_estimators=200, max_depth=10, random_state=42, n_jobs=-1)
    clf.fit(X_train_t, y_level_train)

    reg = RandomForestRegressor(n_estimators=200, max_depth=10, random_state=42, n_jobs=-1)
    reg.fit(X_train_t, y_score_train)

    y_level_pred = clf.predict(X_test_t)
    precision = precision_score(y_level_test, y_level_pred, average="macro", zero_division=0)
    recall = recall_score(y_level_test, y_level_pred, average="macro", zero_division=0)
    f1 = f1_score(y_level_test, y_level_pred, average="macro", zero_division=0)

    names = feature_names(preprocessor)

    artifact = {
        "preprocessor": preprocessor,
        "clf": clf,
        "reg": reg,
        "feature_names": names,
        "level_order": LEVEL_ORDER,
        "model_version": args.model_version,
    }
    joblib.dump(artifact, out_path)

    metrics = {
        "model_name": "risk_classifier",
        "model_version": args.model_version,
        "precision_score": round(float(precision), 4),
        "recall_score": round(float(recall), 4),
        "f1_score": round(float(f1), 4),
        "dataset_size": int(len(df)),
        "notes": "RandomForestClassifier (risk_level) + RandomForestRegressor (risk_score) sobre dataset sintetico.",
    }
    metrics_path.write_text(json.dumps(metrics, indent=2, ensure_ascii=False), encoding="utf-8")

    print(f"Modelo guardado en {out_path.resolve()}")
    print(f"Metricas: {json.dumps(metrics, indent=2, ensure_ascii=False)}")
    print("\nTop 10 features por importancia (clasificador):")
    importances = clf.feature_importances_
    for name, imp in sorted(zip(names, importances), key=lambda x: -x[1])[:10]:
        print(f"  {name}: {imp:.4f}")


if __name__ == "__main__":
    main()
