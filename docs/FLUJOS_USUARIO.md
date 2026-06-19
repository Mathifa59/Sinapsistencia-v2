# Flujos de usuario por rol — Sinapsistencia v2

> Mapeo exhaustivo de los procesos que un usuario puede realizar en la aplicación,
> organizados por rol (Médico, Abogado, Administrador). Las rutas del frontend están
> definidas en `frontend/src/app/app.routes.ts`, con guards en
> `frontend/src/app/core/auth/auth.guard.ts` (`authGuard`, `roleGuard`, `guestGuard`).
>
> Estados de consulta legal (HU-16, `frontend/src/app/shared/constants.ts`):
> `pendiente → clasificada → asignada → en_revision → respondida → cerrada`.

## Flujos comunes (antes de entrar al portal)

1. **Registro** — [register.component.ts:178](../frontend/src/app/features/auth/register/register.component.ts)
   Elige Médico o Abogado, datos básicos + datos profesionales (médico: especialidad/CMP/hospital;
   abogado: CAB + especialidades legales + áreas médicas de interés, mínimo 1 de cada).
   `POST /api/auth/register` → `201 Created` → redirige a login. El admin no se autorregistra.
2. **Login** — [login.component.ts:108](../frontend/src/app/features/auth/login/login.component.ts)
   Por email+password o botones de "acceso rápido demo" por rol. `POST /api/auth/login`,
   setea cookie httpOnly con JWT, redirige al dashboard de su rol.
3. **Logout** — `POST /api/auth/logout`, expira la cookie.
4. **Mi Perfil** (los 3 roles, contenido distinto) — `GET/PATCH /api/profile`,
   `POST /api/profile/avatar` (Cloudinary). Cada uno solo edita el suyo, salvo el admin.

---

## MÉDICO — `/doctor/*`

1. **Dashboard** — KPIs (consultas activas, docs pendientes, abogados sugeridos, total
   histórico) + consultas/documentos recientes. `GET /api/legal-cases`, `/api/documents`,
   `/api/matching/lawyers?doctorId=`.
2. **Mis Consultas** (listar/buscar) — `GET /api/legal-cases?doctorId={self}`.
3. **Crear consulta** — [case-form-modal.component.ts:154](../frontend/src/app/features/doctor/cases/case-form-modal.component.ts)
   Título, descripción, prioridad, especialidad, urgencia + "contexto simulado" anonimizado
   (Ley 29733). `POST /api/legal-cases` → nace en estado `pendiente`.
4. **Ver detalle de consulta** — documentos vinculados, contexto, abogado asignado (o botón
   "Buscar abogado"). Solo lectura para el médico.
5. **Documentos clínico-legales** (listar/crear) — tipos: historia clínica, consentimiento
   informado, informe médico, receta, orden de laboratorio, certificado médico, documento
   legal, otro. `POST /api/documents` crea v1 en estado `borrador`.
6. **Avanzar estado de un documento** — `borrador → pendiente_firma → firmado → archivado`.
   Al marcar "firmado" el backend genera una firma digital con hash SHA-256 real sobre la
   versión vigente.
7. **Abogados sugeridos (matching ML)** — [lawyers.component.ts:120](../frontend/src/app/features/doctor/lawyers/lawyers.component.ts)
   Lista con % de compatibilidad y razones explicadas (XAI); matches ≥80% se destacan.
   `GET /api/matching/lawyers?doctorId=` (TF-IDF/coseno vía ml-service, con fallback por
   especialidad si el servicio ML falla).
8. **Solicitar contacto con un abogado** — `POST /api/matching/contact-requests`. Bloqueado
   si ya hay una solicitud pendiente/aceptada con ese abogado.
9. **Evaluar riesgo médico-legal** — [risk.component.ts:248](../frontend/src/app/features/doctor/risk/risk.component.ts)
   `POST /api/ml/risk` (Random Forest) → score, nivel, factores XAI, recomendaciones.
   **Efecto colateral**: si el nivel resulta alto/crítico, dispara una alerta fire-and-forget
   a n8n.
10. **Editar Mi Perfil profesional** — especialidad, CMP, hospital, años de experiencia, bio,
    avatar.

---

## ABOGADO — `/lawyer/*`

1. **Dashboard Legal** — KPIs (solicitudes nuevas, casos activos, médicos disponibles,
   valoración) + solicitudes pendientes con Aceptar/Rechazar inline + "Casos que podrían
   interesarte" (relevant cases) filtrados por sus áreas médicas de interés.
2. **Responder solicitudes (Aceptar/Rechazar)** — `PATCH /api/matching/contact-requests`.
   **Efecto en cascada (HU-18)**: si la solicitud tenía una consulta vinculada sin abogado,
   al aceptar el backend asigna automáticamente al abogado y mueve la consulta a `en_revision`.
3. **Listar/filtrar solicitudes de contacto** — chips Todas/Pendiente/Aceptado/Rechazado.
   `GET /api/matching/contact-requests?lawyerId={self}` (solo ve las suyas).
4. **Médicos disponibles (directorio)** — búsqueda + modal de detalle (solo lectura).
   `GET /api/matching/doctors`.
5. **Editar Mi Perfil Legal** — CAB, especialidades legales, **áreas médicas de interés**
   (este campo es el que alimenta "Casos relevantes" del dashboard), bio, avatar.

---

## ADMINISTRADOR — `/admin/*`

1. **Panel de Administración (Dashboard)** — KPIs globales (usuarios, consultas, documentos,
   eventos de auditoría) + tabla de usuarios + feed de auditoría reciente.
2. **Gestionar usuarios** — listar/buscar, activar/desactivar (`PATCH /api/users/{id}`). Un
   usuario desactivado no puede iniciar sesión.
3. **Crear usuario** — puede crear médico, abogado **o admin** (a diferencia del registro
   público); no exige perfil profesional adicional.
4. **Ver y crear documentos del sistema** — sin restricción de autor; ve la columna "Autor";
   puede vincular cualquier consulta.
5. **Métricas del sistema** — distribución de consultas por estado/prioridad, documentos por
   estado, top especialidades, y estado del ml-service (`GET /api/ml/health`).
6. **Bitácora de auditoría** — búsqueda/filtro sobre todos los eventos `@Auditable`
   (login/logout, CRUD de consultas/documentos/usuarios, perfil, solicitudes de contacto).
7. **Editar Mi Perfil** — versión reducida (solo nombre + avatar, sin datos profesionales).

> El admin tiene bypass de ownership en todos los servicios (`cb.conjunction()`): puede
> filtrar por `userId`/`doctorId`/`authorId`/`lawyerId` en cualquier endpoint, aunque el
> frontend actual no expone toda esa capacidad (ej. no hay un "ver perfil de cualquier
> usuario" en la UI).

---

## Flujos cruzados (cómo interactúan los roles)

**A. Ciclo de vida de una consulta (médico → abogado)**
Médico crea consulta (`pendiente`) → ve abogados sugeridos por ML → solicita contacto →
abogado acepta → si la solicitud tenía consulta vinculada, esta pasa automáticamente a
`en_revision` con el abogado asignado. Alternativamente, un médico/admin puede asignar
abogado directamente vía `PUT /api/legal-cases/{id}` (pasa a `asignada`), o el abogado puede
auto-asignarse un caso sin abogado.

**B. Descubrimiento pasivo (casos relevantes)**
El abogado ve consultas `pendiente` sin abogado cuya especialidad médica coincide con sus
áreas de interés — independiente de que el médico lo haya contactado. El frontend aún no
tiene botón de "contactar" desde esa tarjeta.

**C. Recomendaciones ML persistidas (XAI)**
`POST /api/matching/lawyers` no solo calcula sino que persiste cada recomendación con
score/razones/feature importance, y si está vinculada a una consulta `pendiente`, la mueve
a `clasificada`.

**D. Alerta de riesgo → n8n**
Riesgo alto/crítico dispara webhook externo (fire-and-forget), invisible en la UI de
cualquier rol.

**E. Auditoría centralizada**
Toda acción relevante de médico/abogado/admin queda registrada y solo el admin la ve en
`/admin/audit`.

**F. Visibilidad de perfiles entre roles**
Médico y abogado no pueden ver el perfil completo (`/api/profile`) del otro — solo la
versión pública vía los directorios de matching (`DoctorCardDto`/`LawyerCardDto`), una capa
de anonimización parcial alineada con la Ley 29733.

---

### Archivos clave para verificación rápida

- Rutas: `frontend/src/app/app.routes.ts`
- Guards: `frontend/src/app/core/auth/auth.guard.ts`
- Constantes/enums: `frontend/src/app/shared/constants.ts`
- Controllers backend: `backend/src/main/java/pe/sinapsistencia/{auth,cases,documents,matching,profile,users,audit,ml}/web/*Controller.java`
- Services con lógica de ownership: `LegalCaseService.java`, `DocumentService.java`,
  `ProfileService.java`, `ContactRequestService.java`, `RecommendationService.java`,
  `RelevantCasesService.java`
- Glosario de contrato API: [API_ENDPOINTS.md](API_ENDPOINTS.md)
