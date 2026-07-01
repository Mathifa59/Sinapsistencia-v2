# Guión de grabación — OE3 (versión corta)

**Duración objetivo:** 7–10 minutos  
**Enfoque:** Solo el **núcleo** que demuestra el OE3. El resto de las 44 HU queda en el informe (`02_CHECKLIST_44_HU.md`), no en el video.

---

## Qué debe probar el OE3 (mapa mínimo)

| Pilar OE3 | Qué mostrar en video | HU de respaldo (no repetir en cámara) |
|-----------|----------------------|--------------------------------------|
| **Registro / acceso multi-rol** | Login médico + cambio a abogado | HU-01 a HU-05, HU-09 → mencionar en voz |
| **Gestión de consultas** | Crear consulta → detalle → listado | HU-11 a HU-16, HU-28 |
| **Integración ML** | Clasificación al crear + matching con % | HU-29 a HU-33, HU-43 (banner) |
| **Conexión médico ↔ abogado** | Solicitud → aceptar → respuesta → cierre | HU-17 a HU-23 |
| **OE3-I2** | Listado consultable + 1 petición &lt; 2 s | HU-13, HU-17 |

**Fuera del video (solo informe):** perfiles completos, documentos, auditoría, métricas admin, manual, recuperación de contraseña, dashboards detallados.

---

## Preparación (antes de grabar)

1. Servicios activos → `01_COMANDOS_PREVIOS.md`
2. Cuentas demo listas (botones en `/login`)
3. **Opcional:** tener ya una consulta creada por si falla ML en vivo; el guión asume flujo en vivo

---

## 1. Introducción (30 s)

> "Sinapsistencia es un prototipo de mediación médico-legal. En este video demostramos el **OE3**: acceso por roles, gestión de consultas, conexión médico-abogado e integración de Machine Learning."

No mostrar arquitectura ni stack salvo que el evaluador lo pida aparte.

---

## 2. Acceso por rol (45 s) · OE3 pilar 1

1. `/login` → botón **Acceso rápido — Médico** (`doctor.demo@sinapsistencia.pe`).
2. Mostrar dashboard médico (sidebar distinto por rol).
3. **En voz:** *"Existen tres portales — médico, abogado y administrador — con rutas protegidas por rol."*

**No grabar:** registro, forgot-password, intento `/admin`, auditoría.

---

## 3. Crear consulta + ML (2 min) · OE3 pilares 2 y 4

1. `/doctor/cases` → **Nueva consulta**.
2. Título, descripción breve, **contexto simulado** (1–2 campos).
3. Guardar → abrir **detalle** de la consulta creada.
4. Señalar en pantalla:
   - Estado (ej. **Clasificada**)
   - Sección **Clasificación ML** (categoría, prioridad, confianza)
   - **Justificación de prioridad** si aparece
5. Volver al listado → mostrar la consulta en el historial (filtro opcional, 5 s).

**Frase clave:** *"Al registrar la consulta, el modelo clasifica y prioriza automáticamente."*

**No grabar:** editar consulta, módulo de riesgo por separado, filtros extensos.

---

## 4. Matching y solicitud de contacto (1 min 30 s) · OE3 pilares 3 y 4

1. `/doctor/lawyers`.
2. Leer **banner ético** ámbar (HU-43) — una frase en voz.
3. Mostrar **un abogado** con % de compatibilidad y razón XAI.
4. **Solicitar contacto** → confirmar mensaje de éxito.

**No grabar:** `/doctor/risk`, métricas en admin.

---

## 5. Respuesta del abogado (2 min) · OE3 pilar 3

1. Cerrar sesión → **Acceso rápido — Abogado**.
2. `/lawyer/requests` → **Aceptar** la solicitud pendiente.
3. `/lawyer/cases` → abrir la consulta → **Registrar respuesta legal** (texto breve).
4. Guardar.

**No grabar:** rechazo con motivo, panel completo de prioridades.

---

## 6. Cierre del ciclo médico (1 min 30 s) · OE3 pilar 2

1. Logout → login **médico** de nuevo.
2. Detalle de la misma consulta:
   - Ver **respuesta legal**
   - **Marcar como revisada** (opcional, 5 s)
   - **Cerrar consulta** con motivo breve
3. Mostrar **línea de tiempo** (scroll rápido, 10 s) — evidencia de trazabilidad sin bloque aparte.

**No grabar:** documentos, informe PDF, eventos manuales.

---

## 7. OE3-I2 — Rendimiento e historial (45 s)

1. Abrir DevTools → pestaña **Network**.
2. Refrescar `/doctor/cases` → señalar **una petición API &lt; 2 s**.
3. **En voz:** *"El historial de consultas es consultable para médico y abogado en sus respectivos listados."*

**No grabar:** múltiples endpoints ni análisis largo de red.

---

## 8. Cierre (30 s)

> "Demostramos el OE3: acceso multi-rol, flujo completo de una consulta médico-legal, recomendación y conexión con abogado vía ML, y respuesta hasta el cierre. Las 44 historias del backlog están implementadas en el prototipo; el detalle por HU consta en la documentación adjunta."

---

## Checklist mínimo de grabación (8 ítems)

- [ ] Login médico (portal correcto)
- [ ] Consulta creada + clasificación ML visible
- [ ] Matching con % y banner ético
- [ ] Solicitud de contacto enviada
- [ ] Abogado acepta y responde
- [ ] Médico cierra consulta
- [ ] Timeline visible
- [ ] Network: al menos 1 respuesta &lt; 2 s + listado historial

---

## Flujo en una línea (referencia rápida)

```
Login médico → Nueva consulta → Detalle (ML) → Abogados (match + solicitar)
→ Login abogado → Aceptar → Responder → Login médico → Cerrar + timeline
→ Network &lt; 2 s → Cierre
```

**Tiempo total estimado:** ~8 minutos hablando con ritmo normal.

---

## Archivos de apoyo

| Archivo | Uso |
|---------|-----|
| `01_COMANDOS_PREVIOS.md` | Levantar servicios |
| `02_CHECKLIST_44_HU.md` | Evidencia completa 44/44 (informe, no video) |
| `04_EVIDENCIAS_CAPTURAS.md` | Capturas para el informe |
