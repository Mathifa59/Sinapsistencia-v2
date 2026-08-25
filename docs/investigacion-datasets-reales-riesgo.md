# Investigación de datasets reales — modelo de riesgo médico-legal

Brief de investigación para complementar el dataset sintético del clasificador
de riesgo ([modelo-ml.md](modelo-ml.md)). **Objetivo:** calibrar los pesos de
`SPECIALTY_BASELINE` y los efectos de los factores de riesgo con evidencia
epidemiológica real — no reemplazar el dataset sintético fila por fila.

> Ver contexto completo: [modelo-ml.md §7 Limitaciones](modelo-ml.md#7-limitaciones-honestas-lo-que-un-jurado-preguntará)
> y [§8.2 Hoja de ruta](modelo-ml.md#82-otras-mejoras) ("validación con datos
> reales anonimizados y recalibración de umbrales").

---

## 1. La distinción clave (leer antes de buscar)

No existe un dataset real de mala praxis **por caso individual, para Perú**,
con las mismas columnas que usamos (`documentation_complete`,
`informed_consent`, etc.). Antes de asignar esta tarea, hay que separar dos
cosas:

| | ¿Es transferible entre países? | ¿Para qué sirve? |
|---|---|---|
| **Factores de riesgo** (especialidad, complejidad, consentimiento, documentación, quejas previas) | ✅ Sí — son relaciones clínicas/organizacionales, no legales | Calibrar los **pesos relativos** en `SPECIALTY_BASELINE` |
| **Resultado/etiqueta** (monto pagado, si hubo litigio, severidad del fallo) | ❌ No — refleja el sistema legal y la litigiosidad de cada país | **No** importar directo como "riesgo legal en Perú" |

EE.UU. es el sistema más litigioso del mundo, con montos de indemnización
mucho más altos que Perú; China tiene su propio sistema judicial. Usar esos
datasets para las **proporciones** entre especialidades es razonable ("cirugía
tiene ~3x más riesgo que medicina general" probablemente se sostiene entre
países); usar sus montos/tasas de litigio **tal cual** como "riesgo legal
peruano" no lo es.

**Expectativa realista de accuracy:** la literatura publicada sobre predicción
de mala praxis con datos reales ronda 60–70 % (ver estudio en la tabla abajo:
XGBoost sobre 963 casos reales de sentencias → 66 %). El 85 % es más realista
sobre un dataset sintético bien diseñado (donde controlamos la señal) que
sobre datos reales — no asumir que cambiar de dataset sube la métrica.

---

## 2. Fuentes de mala praxis (para calibrar factores)

| Fuente | Qué tiene | Link |
|---|---|---|
| **NPDB Public Use File** (EE.UU., HRSA, gratis) | Monto pagado, especialidad (`Field of License`), grupo de alegación, edad de paciente/practicante. Actualizado trimestralmente desde 1990. | [npdb.hrsa.gov/resources/publicData.jsp](https://www.npdb.hrsa.gov/resources/publicData.jsp) |
| Diccionario de campos del NPDB | Especificación de todas las columnas del PUF | [pufFormatSpecifications.jsp](https://www.npdb.hrsa.gov/resources/puf/pufFormatSpecifications.jsp) |
| **Kaggle — Medical Malpractice Insurance Dataset** | Severidad, especialidad, monto de reclamo, abogado privado (sí/no), demografía. Más chico y manejable que el NPDB. | [kaggle.com/datasets/gabrielsantello/medical-malpractice-insurance-dataset](https://www.kaggle.com/datasets/gabrielsantello/medical-malpractice-insurance-dataset) |
| Estudio académico (China, ortopedia, 2024) | 963 casos reales de sentencias judiciales, 21 variables médicas + 10 del paciente, RF/XGBoost/LightGBM. Referencia de accuracy realista (66 %) en datos reales. | [PMC11023448](https://pmc.ncbi.nlm.nih.gov/articles/PMC11023448/) |

## 3. Contexto peruano (solo agregado — no hay datos por caso)

| Fuente | Qué tiene | Link |
|---|---|---|
| SUSALUD — Datos Abiertos | Estadísticas agregadas de reclamos por tipo de servicio de salud | [datos.susalud.gob.pe](http://datos.susalud.gob.pe/) |
| SUSALUD — Reclamos y Quejas | Info del canal de reclamos (no es un dataset descargable) | [portal.susalud.gob.pe/blog/reclamos-quejas](http://portal.susalud.gob.pe/blog/reclamos-quejas/) |

⚠️ **No perder tiempo buscando** "dataset de mala praxis Perú fila por fila"
— no existe públicamente. Lo de SUSALUD es solo agregado/porcentajes (útil
como referencia de magnitud, no como filas de entrenamiento).

## 4. Registros de médicos y abogados (Perú)

| Fuente | Qué tiene | Link |
|---|---|---|
| CMP — Conoce a tu médico | +100k médicos colegiados, especialidad, estado de habilitación | [conoceatumedico.cmp.org.pe](https://conoceatumedico.cmp.org.pe/) |
| CAL — Buscador de abogados | Colegiatura y estado de habilitación (Lima) | [abogados.cal.org.pe/buscador](https://abogados.cal.org.pe/buscador) |

⚠️ Son buscadores **uno por uno** (verificación de colegiatura), no tienen
descarga masiva. Sirven para muestrear manualmente perfiles de referencia
(especialidades reales, distribución realista), no como dataset de
entrenamiento.

---

## 5. Checklist al evaluar cualquier fuente candidata

Antes de traer un dataset a revisión, confirmar:

- [ ] ¿Tiene un campo de **especialidad/área** claro y mapeable a las 20 de
      `MEDICAL_SPECIALTIES` (frontend) / `SPECIALTY_BASELINE` (ML)?
- [ ] ¿Tiene un **resultado/severidad** que se pueda convertir a una escala
      ordinal (bajo/moderado/alto/crítico)?
- [ ] ¿Cuántas filas hay en las categorías **graves**? (los datasets reales
      suelen quedar muy cortos en la clase minoritaria — igual que discutimos
      que le pasa al RF con la clase "moderado")
- [ ] ¿Hay algún proxy de complejidad del procedimiento o tipo de evento?
- [ ] Bajar el CSV + su documentación de campos y traerlo a revisión — **no**
      intentar entrenar nada todavía.

No vas a encontrar `informed_consent` o `documentation_complete` como
columnas booleanas listas en ninguna fuente pública real — eso normalmente
está enterrado en texto narrativo del caso, no como campo estructurado.

## 6. Cómo se usa esto después

1. Comparar los campos encontrados contra
   [`ml-service/app/risk/baselines.py`](../ml-service/app/risk/baselines.py)
   (`SPECIALTY_BASELINE`) y contra las columnas de
   [`generate_risk_dataset.py`](../ml-service/training/generate_risk_dataset.py).
2. Ajustar las **proporciones relativas** entre especialidades si los datos
   reales sugieren un orden distinto al asumido hoy.
3. Documentar la fuente y la calibración en `modelo-ml.md` §3, para que en la
   sustentación se pueda decir: *"el generador sintético fue calibrado con
   datos epidemiológicos reales de [fuente]"* — más defendible que "basado en
   literatura general".
