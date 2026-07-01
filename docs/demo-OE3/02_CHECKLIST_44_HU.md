# Checklist de cumplimiento — 44 Historias de Usuario

**Estado:** ✅ Implementado en Sinapsistencia v2 (post-cierre MVP)  
**Cobertura estimada:** 100 % (44/44)

| HU | Título | Evidencia en demo |
|----|--------|-------------------|
| HU-01 | Registro por rol | `/register` médico/abogado; admin en `/admin/users` |
| HU-02 | Inicio de sesión | `/login` + acceso rápido demo |
| HU-03 | Cierre de sesión | Menú / logout |
| HU-04 | Recuperación contraseña | `/forgot-password` → token → `/reset-password` |
| HU-05 | Rutas por rol | Intentar `/admin` como médico → bloqueado |
| HU-06 | Perfil médico | `/doctor/profile` — CMP obligatorio |
| HU-07 | Perfil abogado | `/lawyer/profile` — CAB, especialidades, áreas |
| HU-08 | Perfil admin | `/admin/profile` |
| HU-09 | Navegación por rol | Sidebar distinto por portal |
| HU-10 | Auditoría auth | Admin → Auditoría; login fallido registrado |
| HU-11 | Registrar consulta | Nueva consulta médico |
| HU-12 | Contexto simulado | Formulario contexto Ley 29733 |
| HU-13 | Listar consultas médico | `/doctor/cases` + filtros |
| HU-14 | Detalle consulta | `/doctor/cases/:id` detalle completo |
| HU-15 | Editar consulta pendiente | Botón Editar si estado=Pendiente |
| HU-16 | Estados consulta | Badges 6 estados + transiciones |
| HU-17 | Panel consultas abogado | `/lawyer/cases` |
| HU-18 | Aceptar/rechazar | `/lawyer/requests` — rechazo con motivo |
| HU-19 | Lista abogados recomendados | `/doctor/lawyers` |
| HU-20 | Solicitud atención | Solicitar contacto |
| HU-21 | Respuesta legal | Abogado → detalle → respuesta |
| HU-22 | Ver respuesta | Médico → detalle → respuestas |
| HU-23 | Cerrar consulta | Botón Cerrar consulta |
| HU-24 | Cargar documentos | Documentos + adjuntar archivo |
| HU-25 | Listar/descargar docs | Lista + botón Descargar |
| HU-26 | Eventos simulados | Agregar evento en detalle |
| HU-27 | Línea de tiempo | Sección Timeline en detalle |
| HU-28 | Historial consultas | Listados con filtros médico/abogado |
| HU-29 | Clasificar ML | Auto al crear consulta (sección Clasificación) |
| HU-30 | Priorizar consulta | Prioridad + justificación ML |
| HU-31 | Matching | Score % en abogados sugeridos |
| HU-32 | Explicar matching | "Por qué es compatible" |
| HU-33 | Mostrar ML al médico | Detalle consulta + abogados |
| HU-34 | Consultas por prioridad abogado | `/lawyer/cases` ordenadas |
| HU-35 | Métricas modelo | `/admin/metrics` tabla ML |
| HU-36 | Dashboard médico | `/doctor/dashboard` |
| HU-37 | Dashboard abogado | `/lawyer/dashboard` |
| HU-38 | Dashboard admin | `/admin/dashboard` |
| HU-39 | Reporte consulta | Botón Generar informe (impresión) |
| HU-40 | Control acceso docs | Solo roles autorizados |
| HU-41 | Validación archivos | Adjuntar archivo inválido → error |
| HU-42 | Bitácora auditoría | `/admin/audit` |
| HU-43 | Advertencias IA | Banner ámbar en ML/abogados/riesgo |
| HU-44 | Manual usuario | `/doctor/manual`, `/lawyer/manual`, `/admin/manual` |
