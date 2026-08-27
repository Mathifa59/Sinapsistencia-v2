# CLAUDE.md — Sinapsistencia v2

Contexto permanente del proyecto. Léelo completo antes de tocar código.

---

## 1. Qué es este proyecto

Plataforma web de **mediación médico-legal**: conecta médicos con abogados de derecho médico,
gestiona consultas clínico-legales, documentos con trazabilidad y evaluación de riesgo asistida
por ML explicable. Caso de estudio: Clínica SANNA "El Golf", San Isidro, Lima.

Es una **tesis de bachillerato en Ingeniería de Software (UPC)**, código TP202610004.

Consecuencia práctica: el código no solo tiene que funcionar, tiene que ser **defendible ante
un jurado académico**. Reproducibilidad, trazabilidad y documentación de decisiones pesan tanto
como que corra.

### El núcleo de la tesis

La contribución central es **el emparejamiento médico–abogado**. La pregunta que el sistema
debe poder responder ante un evaluador es:

> ¿Por qué a este médico le recomiendas este abogado y no otro?

Todo lo demás es soporte. El clasificador de riesgo es un componente de apoyo: sugiere
prioridad, pero el médico declara su propia urgencia percibida y puede sobrescribir la
sugerencia en la plataforma. **No trates el riesgo como si fuera el eje del proyecto.**

---

## 2. Estado actual

- Migración Next.js + Supabase → Angular 21 + Spring Boot completada.
- **44/44 Historias de Usuario** implementadas y desplegadas en producción.
- **90/90 casos de prueba** cubiertos (CP001–CP090).
- Modelo de riesgo `rf-v2` en producción: RandomForest sobre 40 000 filas sintéticas
  balanceadas, F1 macro 0.788, CV 0.791 ± 0.003, R² del regresor 0.928.
- Matching en producción: TF-IDF + coseno combinado con score de desempeño.
- Trabajo activo: **validación empírica del matching**. Ver `docs/MATCHING-SPEC.md`.

---

## 3. Stack

| Capa | Tecnología |
|---|---|
| Frontend | Angular 21 (standalone, signals, `@if/@for`) + Tailwind v4 + spartan/ui + TanStack Query |
| Backend | Spring Boot 3.5 + Java 21 + Spring Data JPA + Spring Security 6 + Flyway |
| Base de datos | PostgreSQL 16 + pgvector |
| Storage | Cloudinary |
| ML | FastAPI (`ml-service/`) — RandomForest (riesgo) + TF-IDF/coseno (matching) |
| Correos | n8n + Gmail, webhooks fire-and-forget |
| CI/CD | GitHub Actions, 3 workflows con path filters |
| Despliegue | Frontend → Vercel · Backend + ML → Railway |

**No es Azure.** La memoria de TP1 menciona Azure como infraestructura planificada; la
realidad de producción es Railway + Vercel.

Migraciones Flyway: **V1 → V11**. La siguiente que crees es V12.

---

## 4. Contratos que NO se rompen

El MVP está desplegado, demostrado y documentado. Romper estos contratos obliga a modificar
backend, frontend, mockups y memoria.

### 4.1 Clasificador de riesgo — 7 variables de entrada

```
specialty (20 categorías), procedure_complexity, priority,
documentation_complete, informed_consent, has_prior_complaints,
time_since_incident_days
```

Salida: `risk_level` ∈ {bajo, moderado, alto, critico} + `risk_score` continuo [0,1].

**No agregues ni quites variables de entrada.** Si crees que hace falta, detente y pregunta.

### 4.2 Nomenclatura de modelos

`rf-v1` y `rf-v2` **ya están ocupados**. `rf-v2` es el modelo de riesgo actual en producción,
y la migración Flyway V9 ya registra sus métricas. Cualquier modelo nuevo de riesgo es `rf-v3`.

Los modelos de matching se versionan aparte: `match-v1`, `match-v2`, etc.

### 4.3 Degradación con fallback declarado

El backend nunca bloquea si ML o n8n están caídos:

- Clasificación → reglas `rules-v1`
- Matching → score determinístico por coincidencia de área + rating + casos
- Correo → omitido con `log.warn`

**Los fallbacks deben seguir funcionando.** Son parte de la defensa de arquitectura.

---

## 5. Restricciones de datos

### 5.1 Ley 29733 — ningún dato real de paciente

Los casos usan **contexto simulado**: código de referencia, edad referencial, área. Hay un
detector heurístico de PII que bloquea DNI, teléfonos y nombres propios en texto libre.

No introduzcas rutas que permitan ingresar datos identificables.

### 5.2 El CSV del NPDB nunca entra al repositorio

`NPDB2601.CSV` (225 MB, 1 911 185 registros) está sujeto al Data Use Agreement de HRSA:
obliga a eliminar los datos si HRSA lo solicita, a no identificar individuos y a exigir las
mismas condiciones a terceros.

Va en `.gitignore`. Al repo van el script y el hash SHA-256, nunca el archivo.

Cita obligatoria:

> National Practitioner Data Bank Public Use Data File, 31 de marzo de 2026, U.S. Department
> of Health and Human Services, Health Resources and Services Administration, Bureau of Health
> Workforce, Division of Practitioner Data Bank.

### 5.3 El sistema es de apoyo, no decisorio

Toda salida ML se acompaña de la nota ética HU-43. No introduzcas lenguaje determinista en
código, mensajes ni documentación.

---

## 6. Decisiones cerradas (no rediscutir)

1. **RandomForest** para riesgo. Elegido por interpretabilidad, requisito del dominio, no
   preferencia. No propongas XGBoost ni redes.
2. **TF-IDF + similitud del coseno** para matching. Funciona en arranque en frío, sin
   historial de interacciones.
3. **Cuatro niveles de riesgo.** Ya están en base de datos, API e interfaz.
4. **Disponibilidad del abogado es filtro duro**, no un componente ponderado del score.
5. El corpus sintético de riesgo **se conserva y se documenta**, no se borra. Es parte del
   relato metodológico.

---

## 7. Convenciones

- **Idioma:** código y variables en inglés; comentarios, docstrings y documentación en español.
- **Reproducibilidad:** `RANDOM_STATE = 42` en todo lo estocástico. Reejecutar produce
  resultados idénticos.
- **Sin números mágicos:** umbrales, pesos y rutas en módulo de configuración.
- **Artefactos versionados:** cada corrida escribe `metrics.json` con versiones de librerías,
  semilla, hash del dataset y fecha.

---

## 8. La regla más importante

**No inventes métricas. Nunca.**

Si un resultado no se ha calculado, deja el campo vacío o marcado como pendiente. Si un
experimento sale peor de lo esperado, repórtalo como salió.

Este proyecto se sustenta ante un jurado. Una cifra fabricada o ajustada para alcanzar una
meta es el único error del que no se puede volver.

Corolario: si un resultado sale sospechosamente bueno, **detente y busca la fuga de
información** antes de celebrarlo.
