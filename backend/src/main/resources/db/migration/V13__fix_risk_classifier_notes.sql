-- ============================================================================
-- V13: Corrige una afirmación falsa en las notas del clasificador de riesgo.
--
-- V9 (ya aplicada, no se edita) sembró notes con "Supera baseline lineal
-- (f1=0.792)". Falso: rf-v2 tiene f1_macro=0.7877, la regresión logística
-- 0.7918/0.792 -- el RandomForest queda LIGERAMENTE POR DEBAJO del baseline
-- lineal, no lo supera. La tabla model_metrics (HU-35) del panel de
-- administrador queda contradiciendo docs/modelo-ml.md §7.2, que ya lo
-- declaraba con honestidad.
--
-- Mismo defecto corregido en ml-service/models/risk_model_metrics.json y en
-- la plantilla que genera esa nota (ml-service/training/train_risk_model.py),
-- que antes afirmaba "Supera baseline lineal" sin comparar de verdad.
-- ============================================================================

UPDATE model_metrics
SET notes = 'RandomForest (150 árboles) sobre dataset sintético balanceado (40000 filas). CV 5-fold f1_macro=0.7911±0.0033. Supera ampliamente el baseline trivial (f1=0.250) y queda a la par de la regresión logística (f1=0.792), lo que indica que la estructura del generador es mayormente aditiva. Se mantiene RandomForest por explicabilidad y manejo nativo de variables categóricas.'
WHERE model_name = 'risk_classifier' AND model_version = 'rf-v2';
