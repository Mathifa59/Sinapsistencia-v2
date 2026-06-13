# Estado de verificación — Sinapsistencia v2

> Última actualización: 2026-06-12

Este documento resume qué se probó end-to-end (con la app corriendo localmente:
PostgreSQL + Backend Spring Boot + Frontend Angular) y qué queda pendiente de
verificar.

## ✅ Verificado y funcionando

### Autenticación
- **Login** (3 roles demo: Médico, Abogado, Administrador) → `POST /api/auth/login → 200 OK`,
  redirección al dashboard correcto según rol.
- **Logout** → `POST /api/auth/logout → 200 OK`, limpia sesión y vuelve a `/login`.
- **Registro de cuenta nueva** (`/register`, antes no existía — se construyó en esta sesión):
  - **Médico**: nombre, email, password, especialidad médica → `POST /api/auth/register → 201 Created`
    → redirige a `/login` → login con las credenciales nuevas → `200 OK` → dashboard médico
    renderiza correctamente (stats en 0, estados vacíos).
  - **Abogado**: nombre, email, password, especialidades legales (chips) + áreas médicas de
    interés (chips) → `POST /api/auth/register → 201 Created` → login → `200 OK` → dashboard
    legal renderiza correctamente, incluyendo "Casos que podrían interesarte" basado en el
    área médica seleccionada (Cardiología).
- `guestGuard` funciona: un usuario autenticado que navega a `/login` o `/register` es
  redirigido a su dashboard.

### Dashboard Médico
- Bug corregido: la sección "Actividad reciente" llamaba a `GET /api/audit?pageSize=4`
  (endpoint solo-admin, `@PreAuthorize("hasRole('ADMIN')")`), devolviendo `403 Forbidden`
  para médicos/abogados. Se eliminó esa sección y su query del dashboard del médico.
  Verificado: el dashboard ya no hace ninguna llamada a `/api/audit` y carga sin errores
  (`/api/legal-cases`, `/api/documents`, `/api/matching/lawyers` → todos `200 OK`).

### Dashboard Legal (Abogado)
- Carga correctamente tras login/registro: stats en 0, "Solicitudes pendientes",
  "Casos en seguimiento" y "Casos que podrían interesarte" en estado vacío/coherente.

## ⏳ Pendiente de verificar

### ML Service (`ml-service/`)
- **No está corriendo** (`http://localhost:8000` no responde).
- Está implementado: modelo de riesgo (Random Forest, ya entrenado — `models/risk_model.joblib`)
  y matching (TF-IDF + similitud coseno).
- El backend lo consume vía `MlProxyService` (`ML_SERVICE_URL`, default `http://localhost:8000`).
- Mientras no esté levantado:
  - Módulo de **Riesgo** (HU-29/30) probablemente falle o quede vacío.
  - **Recomendaciones de abogados / casos relevantes** (HU-31/32) probablemente fallen o queden vacías.
- Para levantarlo:
  ```bash
  cd ml-service
  python -m venv .venv && .venv/Scripts/activate   # Windows
  pip install -r requirements.txt
  uvicorn app.main:app --reload --port 8000
  ```

### Refactor del módulo `matching` (backend)
- `MatchingService.java` fue eliminado y dividido en 4 servicios nuevos:
  `ContactRequestService`, `MatchingDirectoryService`, `RecommendationService`,
  `RelevantCasesService`. El `MatchingController` ya los referencia y el backend
  compila/corre sin errores (probado indirectamente vía dashboards).
- **No probado en navegador**: flujo completo de matching médico ↔ abogado
  (listado de abogados sugeridos, solicitudes de contacto, "casos relevantes" del abogado).

### Otros pendientes
- Cambios sin commitear: nuevo `/register`, fix audit 403, refactor `matching`,
  `ml-service/` nuevo. Falta decidir cómo organizarlos en commits/PRs.
- No se probó el flujo de **lawyer → solicitar contacto con médico** ni la respuesta
  del médico a esa solicitud (`ContactRequestService`).
- No se probó el panel de **Administrador** (usuarios, métricas, auditoría) en esta sesión.

## Cuentas para probar

### Cuentas demo (seed, ya existentes)

| Rol | Email | Password |
|-----|-------|----------|
| Médico | `doctor.demo@sinapsistencia.pe` | `Demo123!` |
| Abogado | `lawyer.demo@sinapsistencia.pe` | `Demo123!` |
| Administrador | `admin.demo@sinapsistencia.pe` | `Demo123!` |

También hay botones de "Acceso rápido (demo)" en `/login` para entrar directo con
estos 3 roles sin escribir credenciales.

### Cuentas creadas durante esta verificación (vía `/register`)

| Rol | Nombre | Email | Password | Notas |
|-----|--------|-------|----------|-------|
| Médico | Ana Torres Quispe | `ana.torres.preview@sinapsistencia.pe` | `Demo1234!` | Especialidad: Cardiología |
| Abogado | Carlos Mendoza Ríos | `carlos.mendoza.preview@sinapsistencia.pe` | `Demo1234!` | Especialidad legal: Derecho Médico · Área médica de interés: Cardiología |

Ambas son cuentas reales en la base de datos (creadas vía el endpoint
`POST /api/auth/register`), útiles para probar el flujo de un usuario nuevo
sin datos previos (consultas, documentos, etc. en 0).
