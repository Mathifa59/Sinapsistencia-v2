# Especificación de correcciones y preparación para validar el OE4

**Proyecto:** Sinapsistencia — TP202610004.  
**Fecha del documento:** 2026-09-30.  
**Revisión:** 2 — encargo técnico autosuficiente, actualizado por solicitud de Renato.  
**Estado:** especificación de implementación; no acredita cambios ejecutados ni resultados del estudio.  
**Destinatarios:** compañero implementador y Claude, responsables del código; Renato, responsable de BD, infraestructura y organización de la validación.  
**Caso de referencia:** `Caso-9ea50615`, UUID `9ea50615-80e5-40fb-a924-55a68dffbe29`.  
**Registro de la revisión:** [REC-006](../../../asesoria/01_Recomendaciones/2026-09-30/REC-006.md). Esta ficha es trazabilidad del documento, no lectura necesaria para implementar.

## 1. Propósito, alcance y límites

[RECOMENDACIÓN] Corregir las inconsistencias identificadas, conservar evidencia de las decisiones ML y verificar que los flujos del médico y del abogado permitan ejecutar el protocolo del OE4 sin defectos que invaliden las tareas.

Este documento es el contexto completo del encargo. Claude solo necesita el código fuente de frontend, backend y servicio ML. No se presupone acceso a esta conversación, skills, AGENTS.md, DOCX de tesis, conectores Railway, navegador autenticado, credenciales ni BD productiva. Las rutas y estructuras marcadas como nuevas deben implementarse; no se afirma que existan hoy. Los ejemplos de JSON y pseudocódigo describen contratos, no son cambios ya ejecutados.

**Responsabilidad de Claude y del compañero:** editar el código, actualizar DTO/tipos/OpenAPI, implementar entidades/repositorios/servicios/controladores/componentes y pruebas, y entregar un contrato de esquema para que Renato prepare la BD. Avanzar con estas instrucciones sin pedir aprobación para cada cambio ni esperar confirmación del alcance. Usar mocks y fixtures cuando falten servicios reales.

**Responsabilidad de Renato:** crear/modificar tablas, columnas, constraints e índices en la BD, gestionar migraciones y copias, configurar Railway/Resend/DNS, preparar cuentas y datos, integrar/desplegar y organizar el estudio. Claude documenta lo que requiere su código; no se conecta a la BD productiva, no ejecuta SQL allí y no realiza resets de contraseñas. Esta división de trabajo no es una pausa de aprobación: completar el código y señalar qué verificación depende de la BD preparada por Renato.

**Tres estados diferentes deben mantenerse separados:**

1. **Software corregido:** pasan las pruebas de cada corrección y las regresiones.
2. **Sistema preparado para evaluar:** ambiente, cuentas, escenarios, instrumentos y protocolo están disponibles y congelados.
3. **OE4 validado:** se ejecutó el estudio y los resultados reales cumplen los criterios aprobados. Puede ejecutarse correctamente y obtener resultados inferiores a los umbrales; deben reportarse.

[HECHO] Renato fijó cuatro criterios: SUS ≥70/100; facilidad favorable ≥80 %; utilidad favorable ≥80 %; reducción temporal ≥40 % en tareas AS-IS/TO-BE equivalentes. El plan existente añade evaluación del matching mediante `Precision@3 ≥0.70`. Este quinto indicador se considera aquí una condición de la propuesta documental; no se presenta como una nueva exigencia oficial de la UPC.

[HECHO] El diseño de estudio disponible propone veinte médicos, tres participantes de piloto y dos adjudicadores. Son antecedentes metodológicos, no tamaños obligatorios acreditados por la UPC. Renato gestiona el protocolo y los instrumentos; esas tareas no bloquean el desarrollo especificado aquí.

## 2. Contexto de arquitectura, contratos y flujo

### 2.1 Componentes y convenciones

- `frontend/`: Angular 21, TypeScript, TanStack Angular Query para queries/mutations y Vitest como herramienta instalada. Los componentes contienen lógica y templates inline. `core/api/ApiService` desenvuelve las respuestas de negocio; los métodos de los wrappers retornan directamente `data`, no todo el sobre.
- `backend/`: Java/Spring Boot, controladores REST, servicios transaccionales, JPA/Hibernate y PostgreSQL. Los paquetes están bajo `backend/src/main/java/pe/sinapsistencia/`. `AuthenticatedUser` representa el usuario del token; `Profile` representa la cuenta. Los IDs de médico/abogado usados por matching son **IDs de cuenta Profile**, no los IDs independientes de DoctorProfile/LawyerProfile.
- `ml-service/`: Python/FastAPI, Pydantic, scikit-learn. Spring lo consume con `MlProxyService`. No sustituirlo ni cambiar su entrenamiento para estas correcciones.
- La BD real usa tablas `cases`, `profiles`, `match_recommendations`, `contact_requests` y `ml_classifications`. `/api/legal-cases` es una ruta HTTP; **no existe por ello una tabla llamada legal_cases**.
- El sobre Spring es `{ "success": true, "data": ... }` o `{ "success": false, "error": "..." }`. Python retorna su payload sin ese sobre. Spring normaliza snake_case a camelCase para riesgo; matching recibe un `JsonNode` con snake_case y lo transforma al DTO frontend.
- `application.yml` configura `hibernate.ddl-auto: validate` y Flyway habilitado al arranque. Claude mantiene esa configuración: no usar `update/create` para modificar la BD automáticamente. Tampoco empaquetar migraciones nuevas en el directorio ejecutable de Flyway por este encargo; entregar a Renato la especificación de esquema. Nuevas entidades necesitan ese esquema para un arranque real, aunque compilen y puedan probarse con dobles.

### 2.2 Flujo de negocio que debe conservarse

Médico registra consulta → backend clasifica y guarda riesgo → médico consulta un ranking guardado → solicita contacto → abogado acepta → caso `asignada` → abogado inicia revisión `en_revision` → publica orientación `respondida` → médico lee/marca revisada → cierra `cerrada`. La revisión de la respuesta no es un estado adicional del caso. Se conservan también rechazo/cancelación y los permisos por propietario/asignación.

El clasificador de riesgo usa siete entradas estructuradas, no interpreta la descripción para calcular riesgo. El matching sí usa título/descripción/tipo/especialidad en TF-IDF, más datos del perfil médico y de los abogados. No confundir ambos modelos.

### 2.3 Tareas del estudio, para interpretar los IDs posteriores

- T01: acceder con cuenta médica de validación.
- T02: registrar consulta con datos simulados.
- T03: interpretar riesgo y explicación.
- T04: revisar abogados recomendados y razones.
- T05: enviar solicitud de contacto/asignación.
- T06: consultar historial y documentos del escenario.

La programación y sus pruebas deben permitir estas tareas; las encuestas y el estudio con participantes los gestiona Renato. No desarrollar un sistema completo de encuestas como condición para cerrar el código.

### 2.4 Cómo trabajar sin nuestras herramientas

Inspeccionar los archivos citados en el editor o terminal que esté disponible. No hacen falta conectores para reconstruir el flujo ni pedir a Codex que ejecute operaciones. Buscar nombres de métodos si cambian los números de línea. Conservar cambios ajenos. Si hay Git, registrar SHA local y entregar diff/commit; si no hay Git, entregar lista de archivos cambiados y contenido verificable. No afirmar coincidencia con producción sin evidencia: esa comprobación final corresponde a Renato.

## 3. Evidencia de partida y lo que no se ha demostrado

[EVIDENCIA] La revisión previa del caso de referencia encontró clasificación `rf-v2`, puntuación `0.9952`, nivel crítico, selección de un abogado sintético, aceptación, inicio de revisión, respuesta legal y revisión de esa respuesta por el médico. El caso permanecía en `respondida`; no se observó cierre ni documentos asociados.

[EVIDENCIA] En los registros del período del caso hubo fallos 403 de Resend: alerta de riesgo, aviso de solicitud recibida y aviso de solicitud respondida. El mensaje identificó el dominio `gmail.com` como remitente no verificado. No se publica aquí una dirección privada ni el valor completo de una variable oculta.

[EVIDENCIA] La consulta de salud revisada respondió `status: online` y modelo cargado. El componente de métricas no reconoce `online`. La solicitud de contacto del caso no contenía puntuación ML; no se obtuvo un registro persistido que reconstruyera el ranking completo que vio el médico.

[PROBLEMA] Estos hallazgos afectan disponibilidad percibida, explicabilidad, seguimiento y evidencia del recorrido. No demuestran por sí solos que toda la aplicación esté defectuosa ni que el modelo de riesgo sea incorrecto.

**Límites de la revisión:** no se ejecutó una nueva batería consolidada de pruebas ni se hizo una auditoría exhaustiva de todos los módulos. La existencia de tests, casos CP001–CP090 y workflows CI no demuestra que hayan pasado. Los registros de la revisión previa deben archivarse anonimizados por el equipo; este documento no sustituye su evidencia original.

## 4. H-01 — Estado de salud ML incorrecto en la interfaz

**Tipo:** defecto confirmado de contrato frontend/backend. **Prioridad:** alta para T03 y confianza del usuario.

### Evidencia y archivos

- `frontend/src/app/features/admin/metrics/metrics.component.ts`, `mlStatusLabel`: solo `ok` o `healthy` se muestran como activos; los errores también se llaman inactivos.
- `backend/src/main/java/pe/sinapsistencia/ml/application/MlProxyService.java`, `health`: devuelve `online` si las consultas concluyen y `offline` ante excepción.
- `backend/src/main/java/pe/sinapsistencia/ml/web/MlController.java`: GET `/api/ml/health`.
- Además, `health` construye una fábrica HTTP con timeout de tres segundos, pero sus llamadas usan otro `restClient`. El límite anunciado en el comentario no queda aplicado por esa fábrica.

### Requisitos funcionales

- RF-01.1: `online` se muestra como **Activo**; `offline`, como **Inactivo**; carga, como **Comprobando**.
- RF-01.2: fallo de red o estado no reconocido se muestra como **No se pudo comprobar**, con opción de reintentar. No afirmar caída del servicio a partir de cualquier error del navegador.
- RF-01.3: distinguir conectividad del proceso de disponibilidad del modelo. Si el proceso responde pero el modelo no está listo, mostrar indisponibilidad del modelo o estado degradado.
- RF-01.4: este indicador no reemplaza la versión y el origen de la clasificación almacenada de un caso.

### Especificación técnica

Conservar `online/offline` en el contrato existente para minimizar incompatibilidades. Añadir información de preparación del modelo si falta. Revisar el estado devuelto por `/health` y `/api/v1/model/info`, no solo la ausencia de excepción. Centralizar el mapeo del estado a texto, icono y color.

**Cambios concretos en `MlProxyService`:** crear un `healthRestClient` dedicado en el constructor, con la misma base URL del servicio ML y connect/read timeout de 3 s; usarlo dentro de `health`, eliminando la fábrica local que no se utiliza. Mantener el cliente de inferencia con sus límites actuales. Para la comprobación concurrente, acotar el tiempo total a 4 s, usar ejecutor acotado y manejar interrupción/cancelación; no confiar en `cancel(true)` como garantía de cerrar I/O. El cliente tiene que mantener sus límites de conexión/lectura. `/health` Python debe devolver `status: ok` y `/api/v1/model/info`, `status: loaded`.

Conservar `status: online` cuando el proceso responde; añadir `modelReady: boolean` y `message` para el modelo no listo. Si no se obtiene salud del proceso, devolver `offline`. En la UI, `online + modelReady false` se representa como **Degradado**. Para compatibilidad, si `modelReady` falta, derivarlo de `model.status === 'loaded'`; no asumir que el modelo está listo por la presencia de un objeto.

**Cambios concretos en `metrics.component.ts`:** sustituir los tres cálculos separados por un único computed que retorne `{ label, icon, color }` y del que dependan tarjeta/texto/icono/color. Orden: loading → error de query → `offline` → `online` con readiness → estado desconocido. Un botón de reintento llama `mlHealthQuery.refetch()`; no necesita BD.

Fixture de payload Spring para la prueba de éxito:

```json
{"success":true,"data":{"status":"online","modelReady":true,"model":{"status":"loaded","modelVersion":"rf-v2","contentModel":"tfidf-cosine","collaborativeModel":"none","riskModel":"random_forest"}}}
```

El wrapper frontend ya desenvuelve `data`; el componente debe recibir el objeto interno. Evitar leer `data.data`.

### Aceptación

- CA-01-A: con `online` y modelo listo, texto e icono indican activo.
- CA-01-B: con `offline`, no aparece activo.
- CA-01-C: error HTTP/red y estado desconocido no generan un falso diagnóstico de modelo caído.
- CA-01-D: una dependencia lenta concluye dentro del límite configurado; una clasificación histórica sigue consultable aunque ML esté caído.

## 5. H-02 — Ranking sin historial reproducible ni vínculo con la selección

**Tipo:** brecha confirmada de trazabilidad. **Prioridad:** alta para T04/T05 y adjudicación del matching.

### Evidencia y archivos

- `frontend/src/app/features/doctor/lawyers/lawyers.component.ts` usa `matchingApi.recommendations`.
- `frontend/src/app/core/api/matching.api.ts`: ese método hace GET `/api/matching/lawyers?doctorId=…&caseId=…`.
- `backend/src/main/java/pe/sinapsistencia/matching/application/RecommendationService.java`: GET recalcula sin guardar; POST `/api/matching/lawyers` llama a `generateAndPersist`.
- Ese POST guarda filas de `MatchRecommendation`, pero devuelve IDs temporales `rec-…`, no las claves persistidas. No hay un identificador de ejecución ni orden explícito del ranking.
- `matching/domain/MatchRecommendation.java` y su repositorio: existen razones, factores y versión; no constituyen un historial completo de ejecuciones.
- `matching/application/ContactRequestService.java`, `matching/domain/ContactRequest.java` y `matching/web/MatchingController.java`: la creación no enlaza una recomendación ni asigna `mlScore`.
- `cases/application/CaseWorkflowService.java`, `getDetail`: para el médico propietario llama también a `recommendationService.recommendations`; `getReport` llama a `getDetail`. **Corregir solo la pantalla de abogados dejaría el recálculo oculto en detalle y reporte.**

### Requisitos funcionales

- RF-02.1: el médico puede generar recomendaciones para una consulta propia y ver el resultado **guardado**.
- RF-02.2: recargar o volver al caso conserva orden, puntuaciones, razones y fecha. No vuelve a inferir de forma silenciosa.
- RF-02.3: una regeneración explícita crea una ejecución nueva y conserva las anteriores.
- RF-02.4: solicitar contacto desde una tarjeta vincula la solicitud al abogado y a la recomendación concreta mostrada.
- RF-02.5: el historial muestra qué ranking originó la selección. Un administrador autorizado puede consultar la evidencia; no modificarla mediante la lectura.
- RF-02.6: cambios posteriores de perfil, disponibilidad o corpus no alteran la fotografía anterior. Si el abogado dejó de estar disponible, informar y aplicar la validación actual al solicitar contacto.
- RF-02.7: un directorio sin ranking y una recomendación histórica no disponible se identifican expresamente. No fabricar puntuaciones ni reconstruir como original un resultado recalculado.

### Modelo de datos objetivo y división con Renato

[RECOMENDACIÓN] Claude implementa `RecommendationRun` y extiende entidades/repositorios; Renato crea `recommendation_runs` y las columnas del contrato BD del apartado 12. Usar esos mismos nombres en los mapeos para evitar dos esquemas incompatibles:

- Ejecución: UUID, médico, consulta, fecha UTC, estado `processing/completed/failed`, versión del algoritmo, versión del contrato/pipeline, pesos, hash de corpus e inputs, número de candidatos y clave de idempotencia. Guardar también inputs y candidatos utilizados como JSONB; **un hash sin la fotografía no basta**.
- Recomendación: UUID real, ejecución, abogado, posición, puntuación total normalizada, componentes, razones, términos/factores y fotografía de los datos relevantes del perfil mostrado.
- Solicitud: FK opcional a la recomendación, además de la consulta ya existente. La puntuación se copia o se consulta desde el registro del servidor; nunca se acepta como verdad una cifra enviada por el cliente.
- Rangos: puntuaciones normalizadas entre 0 y 1, con precisión al menos igual a la respuesta ML. Se propone `NUMERIC(7,6)` para nuevos campos; no atribuir seis decimales de precisión al modelo, que actualmente redondea a cuatro.
- Restricciones: unicidad de posición y abogado dentro de una ejecución; unicidad de la clave de idempotencia en su ámbito; FK e índices de consulta por caso/fecha. Preservar las reglas existentes de solicitud pendiente y probar concurrencia.

No reinterpretar la columna legacy `score` como escala 0–1 sin migración: actualmente almacena un porcentaje entero. Mantener compatibilidad o añadir campos explícitos. Las filas antiguas sin ejecución quedan identificadas como legacy; sus metadatos faltantes permanecen nulos/desconocidos.

### API y transacciones propuestas

1. Reutilizar POST `/api/matching/lawyers` para una generación explícita, con `caseId` y `idempotencyKey` en el body. Exigir consulta en el flujo evaluado. Si se conserva el modo legacy sin consulta, mantenerlo fuera del flujo nuevo y sin crear evidencia falsamente asociada a un caso.
2. Añadir GET de historial y de ejecución guardada. **Rutas propuestas, no existentes:** `/api/matching/recommendation-runs?caseId=…` y `/api/matching/recommendation-runs/{runId}`. Retornar IDs reales, posición, componentes y fecha. El frontend elige la ejecución pertinente del historial sin usar el GET legacy como evidencia guardada.
3. Ampliar la creación de solicitud con `recommendationId` opcional. Para el flujo de ranking debe ser obligatorio; el directorio puede conservar un camino explícito sin recomendación.
4. Validar en servidor que médico, consulta, abogado y ejecución corresponden entre sí. Denegar IDs ajenos, puntuaciones manipuladas y combinaciones inconsistentes. La autorización se basa en el usuario autenticado, no en `fromDoctorId`.
5. Realizar una llamada ML por ejecución adquirida; guardar resultados completos y marcar `completed` en la misma transacción. El fallback por especialidad existente puede completar una ejecución con origen `fallback`, señalado en API/UI; no identificarlo como `tfidf-cosine+perf-v2`. Si falla también el fallback o la persistencia, conservar estado `failed` sin un ranking parcial visible.
6. Repetir la misma clave devuelve el mismo resultado; reutilizarla con otros inputs produce conflicto. Una regeneración intencional usa nueva clave. Manejar dos solicitudes simultáneas sin filas duplicadas.
7. GET nunca crea ni modifica recomendaciones. **No sustituir simplemente el GET por POST dentro de una query que se reejecuta en cada montaje/refresco.** Usar una mutación controlada y lecturas separadas.
8. Cuando cambie una entrada relevante, señalar que la ejecución anterior corresponde a otra versión; permitir regenerar explícitamente sin borrarla. Definir la huella incluyendo caso, perfil, corpus, preprocesamiento y versión/weights, no solo el UUID de la consulta.

### Cambios concretos por clase y componente

**Backend:**

1. Crear `matching/domain/RecommendationRun.java`, `matching/infrastructure/RecommendationRunRepository.java` y DTOs `RecommendationRunSummaryDto`/`RecommendationRunDto` en `matching/web/dto/`. Consultas de repositorio por `doctorId + idempotencyKey`, por `caseId` ordenadas por fecha/UUID y por `runId` con sus recomendaciones.
2. Ampliar `MatchRecommendation`: `run`, `rank`, `scoreRaw`, `contentScoreRaw`, `performanceScoreRaw`, `lawyerSnapshotJson`, `matchedSpecialties`. Los campos raw son `BigDecimal`; usar conversión del número ML sin redondearlo primero a entero. Conservar `score`, `reasons`, `factors`, `algorithmVersion` legacy. Añadir consultas de `MatchRecommendationRepository` por ejecución, ordenadas por rank.
3. Refactorizar `computeRecommendations` para devolver un resultado interno sin IDs temporales: inputs, corpus, modelInfo, origen y lista con los números crudos. `generateAndPersist` transforma ese resultado en entidades, hace flush y construye DTOs desde UUIDs reales. Obtener rank de la posición de la respuesta; desempatar determinísticamente por ID cuando el backend ordene su fallback.
4. Implementar adquisición de ejecución: insertar `processing` en transacción breve con unique `(doctor_id,idempotency_key)`; ante colisión, leer la existente fuera de la transacción fallida. Mismo `request_hash` completado devuelve su resultado; distinto devuelve 409; `processing` devuelve 409 con código/mensaje de operación en curso; `failed` informa fallo y requiere nueva clave para una nueva ejecución. No invocar ML antes de adquirir la ejecución. Finalizar resultados + estado en una segunda transacción atómica y registrar fallos en una transacción independiente. Evitar self-invocation de `@Transactional`; usar un bean separado o `TransactionTemplate`.
5. Si el proceso se interrumpe, una ejecución `processing` antigua debe pasar a `failed` mediante recuperación acotada —por ejemplo, tras 2 min sin progreso—; no relanzar inferencia automáticamente bajo la misma clave. No ofrecer garantía absoluta de exactly-once ante caídas del proceso; sí impedir duplicados de filas y llamadas concurrentes por clave.
6. Añadir los GET al `MatchingController`. Aplicar autorización antes de retornar datos: médico propietario, administrador o abogado efectivamente asignado al caso conforme a permisos del detalle. La consulta histórica no cambia estados de negocio ni llama al ML. Adaptar también GET `/api/matching/lawyers` con `doctorId/caseId` para retornar la última ejecución guardada o lista vacía con mensaje de historial no disponible; el directorio sin `doctorId` conserva su función. No dejar un GET legacy que siga recalculando como si fuera evidencia histórica.
7. En `CaseWorkflowService.getDetail`, reemplazar la llamada de recálculo por lectura de la última ejecución completada del caso o la que originó su solicitud/asignación. `getReport` hereda esa lectura. Sin ejecución histórica, devolver `recommendations: null` y una indicación de no disponible; no generar en el GET.
8. En `MatchingController.CreateContactRequestBody`, añadir `recommendationId` y `selectionSource` (`recommendation` o `directory`). Extender firma de `ContactRequestService.createContactRequest` y `ContactRequestResponse` con `recommendationId` y `recommendationRunId`. Para selección del ranking, validar la relación completa, `completed`, médico/caso, disponibilidad y usuario activo del abogado; asignar FK y `mlScore = scoreRaw × 100`, escala 2. Se conserva así la unidad porcentaje de la columna actual. El frontend no envía `mlScore`.
9. Una selección de directorio nueva exige `selectionSource: directory`, sin recommendationId. El caso sigue validándose por propietario. Bodies legacy sin los nuevos campos se mantienen compatibles y su origen queda explícitamente `legacy_untracked`; no simular que son una selección ML. Guardar el origen en `selection_source`. Mantener el control de duplicados vigente; usar bloqueo de la consulta al asignar/aceptar para evitar dos abogados asignados por una carrera.

**Frontend:**

1. En `MatchingApi`, añadir `recommendationRuns(caseId)` y `recommendationRun(runId)`. `generateRecommendations` recibe `{caseId,idempotencyKey}`. Los wrappers devuelven el `data` ya desenvuelto.
2. En `lawyers.component.ts`, sustituir `recommendationsQuery` por lectura del historial + ejecución guardada. Query key incluye usuario, caseId y runId. Mostrar **Generar recomendaciones** cuando no haya ejecución; **Actualizar recomendaciones** para crear otra. Deshabilitar durante la mutation. Crear UUID de idempotencia una vez por acción; conservarlo al reintentar esa acción, renovar solo en una generación nueva.
3. Las tarjetas recomendadas se construyen y ordenan desde la ejecución, con `lawyerSnapshotJson` convertido al DTO de tarjeta. No mezclarlas con el directorio vivo para reconstruir scores/biografías del pasado. El directorio permanece separado y sin falsa puntuación ML.
4. `getMatchScore`/`getMatchReasons` y mapas de `filteredLawyers` consultan el run seleccionado. La compatibilidad decimal se deriva de raw; el ID enviado en contacto es el UUID de esa recomendación, no un ID `rec-…` ni la posición del array.
5. Extender `contactMutation` con `recommendationId` y `selectionSource`, invalidar contactos/detalle al éxito y mostrar estado de notificación por separado. Al navegar a otro caso, no reutilizar el run, la clave ni el ID seleccionado del anterior.
6. Añadir un selector de ejecuciones con fecha/origen y etiqueta histórica; si inputs actuales cambian, mostrar **Recomendación de una versión anterior del caso**. La tarjeta conserva sus datos históricos; un 409 por abogado no disponible invita a generar otro ranking sin borrar el anterior.

### Contrato HTTP nuevo — ejemplo sin identidades reales

POST `/api/matching/lawyers` recibe:

```json
{"caseId":"<uuid-caso>","idempotencyKey":"<uuid-accion>"}
```

Devuelve 201 para creación y 200 para reintento completado, con el sobre habitual. `data` contiene:

```json
{
  "runId":"<uuid-ejecucion>","caseId":"<uuid-caso>","status":"completed",
  "origin":"ml","modelUsed":"tfidf-cosine+perf-v2","pipelineVersion":"matching-pipeline-v3",
  "createdAt":"2026-09-30T23:57:00Z","weights":{"content":0.7,"performance":0.3},
  "recommendations":[{
    "id":"<uuid-recomendacion>","rank":1,"doctorId":"<uuid-profile-medico>",
    "lawyer":{"userId":"<uuid-profile-abogado>","fullName":"Perfil sintético"},
    "score":52,"contentScore":40,"performanceScore":80,"collaborativeScore":0,
    "scoreRaw":0.52,"contentScoreRaw":0.4,"performanceScoreRaw":0.8,
    "modelUsed":"tfidf-cosine+perf-v2","reasons":["Coincidencia de área"],
    "matchedSpecialties":[],"featureImportance":[],"createdAt":"2026-09-30T23:57:00Z"
  }],
  "advisoryNote":"Las recomendaciones son apoyo a la decisión; la elección la realiza una persona."
}
```

`lawyer` en la implementación tiene los campos completos del `LawyerCardDto` actual, guardados como fotografía; el ejemplo abreviado no autoriza omitirlos. GET de historial retorna resúmenes con `runId`, fecha, status, origen y versión; GET de run retorna la estructura anterior. Filtrar/sanear lo que puede ver cada rol; no exponer el corpus completo ni inputs privados en un endpoint público.

POST `/api/matching/contact-requests` desde una tarjeta recibe:

```json
{"toLawyerId":"<uuid-profile-abogado>","caseId":"<uuid-caso>","message":"Solicitud de prueba","recommendationId":"<uuid-recomendacion>","selectionSource":"recommendation"}
```

**Corpus vacío — corrección específica relacionada:** `MlProxyService.recommendations` omite actualmente `lawyers` cuando la lista está vacía; Python convierte una lista vacía en `None` y usa perfiles estáticos. En el flujo BD, cero abogados disponibles debe producir **cero candidatos vivos**, no activar perfiles estáticos ajenos. En Spring, enviar el campo si `lawyers != null`, aun vacío. En `ml-service/app/main.py` y `app/matching/model.py`, distinguir `None` de `[]`: `None` conserva el modo estático legacy; `[]` retorna recomendaciones vacías con `corpus: live` y tamaño 0, sin ajustar TF-IDF sobre cero filas. Añadir prueba de este caso.

**Dos huellas diferentes:** `request_hash` identifica el comando estable del cliente —caseId y parámetros explícitos de generación— para comparar reintentos. `input_hash`/`corpus_hash` identifican las fotografías usadas y permiten detectar antigüedad. No rechazar un reintento completado porque cambió después la biografía de un abogado: la misma clave y el mismo comando recuperan el run original. Tampoco cambiar un run completado por una huella nueva.

### Aceptación

- CA-02-A: generar, recargar y consultar desde otra sesión autorizada conserva UUIDs, orden y valores; contar llamadas confirma que GET no invoca ML.
- CA-02-B: reintento de una generación y dos peticiones concurrentes con la misma clave producen una sola ejecución.
- CA-02-C: regenerar crea otra ejecución y deja accesible la anterior.
- CA-02-D: cambiar biografía, disponibilidad o corpus no altera el ranking guardado; la solicitud aplica la disponibilidad actual.
- CA-02-E: solicitud creada desde una tarjeta conserva el vínculo correcto y puntuación de servidor; IDs de otro caso/médico o abogado no coincidente se rechazan.
- CA-02-F: casos históricos, incluido el de referencia, siguen consultables y sus faltantes se rotulan sin inventar el ranking original.
- CA-02-G: fallo ML o transaccional no deja un conjunto parcial identificado como completado; cero candidatos muestra un mensaje claro, no una recomendación falsa.

## 6. H-03 — Componentes del matching incompletos y explicación ambigua

**Tipo:** defecto confirmado de transporte/representación. **Prioridad:** alta, junto con H-02.

[EVIDENCIA] `ml-service/app/matching/model.py` combina `0.70 × content_score + 0.30 × performance_score`. La respuesta incluye `performance_score`, pero `RecommendationService` no lo transporta y `RecommendationDto` conserva `collaborativeScore`. En el ML actual, `collaborative_score` vale cero; no hay recomendador colaborativo activo.

### Requisitos y especificación

- RF-03.1: mostrar total y componentes de contenido/desempeño, sus pesos y una explicación comprensible. Mostrar **compatibilidad**, no probabilidad de éxito legal ni de aceptación del abogado.
- RF-03.2: preservar valores normalizados recibidos antes de convertirlos a porcentajes. Una cifra redondeada en la tarjeta no debe ser la fuente de persistencia.
- RF-03.3: incorporar `performanceScore` al DTO, API, tipos frontend, historial y exportación. Mantener el campo colaborativo solo por compatibilidad temporal, marcado obsoleto; no mostrarlo como parte activa del cálculo.
- RF-03.4: guardar pesos y versión junto a cada ejecución. No asumir siempre 70/30 al interpretar ejecuciones futuras.
- RF-03.5: explicar las razones de área, experiencia, valoración y casos usando el perfil guardado. Los términos TF-IDF son evidencia de similitud; no constituyen causalidad.

[EVIDENCIA] El desempeño actual usa valoración, casos y experiencia: `0.50 × rating/5 + 0.30 × min(log(1+casos)/log(61),1) + 0.20 × min(años/20,1)`, con recortes definidos en el código. Disponibilidad es un filtro del backend, no un peso adicional. Con perfiles sintéticos, tampoco representa desempeño profesional real acreditado.

Actualizar `frontend/openapi.json` y regenerar los clientes con el procedimiento del repositorio; no corregir únicamente un archivo generado. Verificar compatibilidad del DTO antes del despliegue del frontend.

**Cambios concretos:** ampliar `RecommendationDto` con `performanceScore` legacy porcentual y los tres campos raw `BigDecimal`. Conservar `score`, `contentScore` y `collaborativeScore` para no romper consumidores existentes; los porcentajes enteros son adaptación de salida, nunca entrada a persistencia. En el parser Spring exigir `score`, `content_score` y `performance_score` para el modelo compuesto; validar finitud/rango, evitar `path(...).asDouble(0)` como sustituto de un campo faltante. Una respuesta inválida activa el fallback identificado, no una explicación numérica falsa.

Para ejecución `origin: ml`, pesos 70/30 y desglose deben venir identificados en `modelInfo` o en metadata versionada del pipeline; añadirlos en `ml-service/app/main.py` al `model_info` retornado. Para `origin: fallback`, `contentScoreRaw`/`performanceScoreRaw` son null si la fórmula no genera esos componentes y no se prueba la fórmula 70/30. Mantener su algoritmo real: base 60 por coincidencia de área, hasta 25 por rating y 15 por casos con saturación en 50; señalar sus unidades porcentuales. No disfrazarlo como modelo compuesto.

En la tarjeta, añadir bloque **Cómo se calcula**: porcentaje de contenido, porcentaje de desempeño y pesos. Por ejemplo, contenido 40 % × 70 % + desempeño 80 % × 30 % = compatibilidad 52 %. Mostrar null como **No disponible**, y ocultar componentes del fallback que no existen. Mantener texto de apoyo a la decisión.

`frontend/package.json` tiene `generate:api`; si hay terminal, ejecutar `npm run generate:api` tras actualizar OpenAPI. Si Claude no tiene generador, editar coherentemente contrato y tipos usados y documentar la regeneración pendiente; no presentar los archivos generados como sincronizados cuando no lo están. Buscar todos los constructores del record Java para evitar errores de compilación al añadir campos.

### Aceptación

- CA-03-A: con contenido 0.40 y desempeño 0.80, total 0.52 y visualización 52 %; el registro conserva componentes y pesos.
- CA-03-B: con respuesta real, total coincide con la fórmula dentro del error de redondeo de los componentes; usar tolerancia documentada de `0.0001` para el modelo actual.
- CA-03-C: el usuario no ve un algoritmo colaborativo activo ni confunde compatibilidad con garantía de resultado.
- CA-03-D: dato ausente no se transforma silenciosamente en cero; se informa contrato incompleto o fallo conforme al diseño.

## 7. H-04 — Redondeo y significado del score de riesgo

**Tipo:** defecto confirmado de presentación; aclaración de interpretación. **Prioridad:** media, necesaria para T03.

### Evidencia y archivos

- `cases/application/CaseClassificationService.java`: la justificación usa `%.0f%%`.
- `frontend/src/app/features/doctor/cases/case-form-modal.component.ts` y `shared/features/case-detail/case-detail.component.ts`: usan redondeo entero del score.
- `backend/src/main/java/pe/sinapsistencia/notifications/MailTemplates.java`: el correo de riesgo también redondea a entero.
- `ml-service/app/risk/model.py`: nivel proviene del clasificador y score continuo del regresor; no son el mismo resultado.

### Requisitos y especificación

- RF-04.1: representar `0.9952` como **99,52 %**, de forma coherente en creación, detalle, nuevas justificaciones, correos y reportes afectados.
- RF-04.2: conservar el score crudo y el nivel del modelo. No recalcular etiquetas usando umbrales nuevos ni cambiar la clasificación por un ajuste visual.
- RF-04.3: describirlo como puntuación estimada de riesgo; no llamarlo certeza, probabilidad de demanda ni confianza del modelo. `confidence = null` no se sustituye por el score ni por F1.
- RF-04.4: identificar `rf-v2` o `rules-v1` en la evidencia. El respaldo por reglas no debe presentarse como inferencia RF exitosa.
- RF-04.5: las importancias globales multiplicadas por valores del caso se rotulan como explicación heurística. No llamarlas SHAP/LIME ni afirmar que su suma reproduce el score individual.

Usar un formateador coherente y probar límites 0 y 1. Conservar la precisión almacenada. La barra puede tener un ancho aproximado, pero su etiqueta debe ser precisa. No reescribir eventos o justificaciones históricos: el detalle puede mostrar el valor numérico original con formato correcto y aclarar que el texto histórico conserva su redondeo.

**Implementación concreta frontend:** crear un helper puro compartido `frontend/src/app/shared/utils/ml-score.util.ts`: recibe `number | null | undefined`, rechaza no finitos/fuera de 0–1, y formatea `value * 100` con `Intl.NumberFormat('es-PE', {minimumFractionDigits:2,maximumFractionDigits:2}) + '%'`. Pruebas verifican valor y dos decimales según ese locale; si el locale usa punto decimal, no forzar una coma mediante reemplazo global. La representación española con coma usada aquí expresa el valor, no una obligación de contradecir el locale de la app. Reutilizarlo en modal, detalle, ranking y reporte; el ancho de barra puede usar `score * 100` con clamp de presentación.

En Java, cambiar `%.0f%%` por formato de dos decimales en nuevas justificaciones y `MailTemplates`, manteniendo `Locale.ROOT` o el locale explícito del producto. Revisar `getReport` y `generateReport` del detalle: `CaseReportDto` frontend tiene hoy `MlClassificationDto` local incompleto, sin los campos de riesgo de su tipo extendido. Unificar tipos para no perder score/nivel/inputs en el reporte. No alterar el cálculo de RF ni el valor `confidence` ausente.

En `MlProxyService.riskAssessment` y su consumidor validar presencia/rango/finitud de score, nivel admitido y versión: una respuesta malformada no debe convertirse en riesgo 0 por un default numérico. Tratarla como fallo del servicio y conservar el fallback `rules-v1` con score ausente, conforme al flujo existente. Añadir fixture de JSON incompleto para probarlo.

### Aceptación

- CA-04-A: `0.9952`, `0`, `1` y valor ausente muestran respectivamente 99,52 %, 0,00 %, 100,00 % —o separador decimal del locale configurado— y ausencia explícita, nunca NaN ni un cero inventado.
- CA-04-B: creación y detalle presentan el mismo resultado persistido; consultar no vuelve a clasificar.
- CA-04-C: al simular ML caído, se conserva el fallback y su identidad; no aparece confianza ficticia.
- CA-04-D: pesos, dataset, niveles y score del modelo no cambian para conseguir un resultado más favorable.

## 8. H-05 — Capturar complejidad independiente y entradas históricas

**Tipo:** corrección de captura/integración especificada en este encargo. **Prioridad:** alta antes de congelar la versión de estudio.

[EVIDENCIA] `CaseClassificationService.deriveComplexity` convierte urgencia alta/crítica en complejidad alta. El modal replica la regla. `CreateCaseRequest` no tiene una entrada independiente de complejidad, aunque ML espera `procedure_complexity` y la documentación la describe como reportada.

[RECOMENDACIÓN] Implementar **complejidad baja/media/alta independiente**. No dejar esta corrección esperando una consulta a Renato. El formulario explica que complejidad describe características del procedimiento y urgencia describe necesidad temporal percibida; ambas son entradas reportadas, no diagnósticos legales. La revisión experta de la redacción para el estudio la coordina Renato sin bloquear el código.

Para implementar:

1. Añadir campo al formulario, `CreateCaseRequest`, dominio `LegalCase`, migración y DTO de consulta. Revisar los DTO de lectura de los factores existentes: `CaseResponse` tampoco expone actualmente los tres booleanos usados por riesgo.
2. Validar enum en backend y capturar por separado urgencia y complejidad. El formulario no debe cambiar una al cambiar la otra.
3. Mantener las tres categorías del contrato ML y los booleanos actuales. No introducir una categoría ML “desconocida” sin diseño de modelo. El nuevo selector empieza sin selección y exige elegir para la creación frontend. Conservar compatibilidad de bodies legacy omitidos mediante inferencia identificada como legacy; no atribuirla al médico. Mantener defaults backend actuales de booleanos para clientes antiguos (`documentationComplete/informedConsent` omitidos → true; `hasPriorComplaints` omitido → false). El formulario nuevo debe enviar respuestas explícitas y no afirmar que un default es un hecho clínico confirmado.
4. Enviar al ML la complejidad registrada; eliminar la deducción del nuevo flujo. El servicio ML ya acepta las tres categorías: este cambio no obliga por sí solo a reentrenar el modelo, pero sí a revisar la integración y sus resultados con referencia experta.
5. Persistir fotografía de las siete entradas efectivamente evaluadas, fecha de evaluación, origen de complejidad y versión del pipeline. Preservar tanto urgencia percibida como prioridad sugerida y cualquier modificación humana posterior.
6. No recalcular ni sobrescribir clasificaciones antiguas. Complejidad histórica inferida no se convierte por migración en una declaración del médico. Nuevas reevaluaciones, si se habilitan, tienen ID/versionado y eventos propios.
7. La pantalla de análisis lee inputs y resultado persistidos. Evitar recomputar días desde el incidente con el reloj del navegador como si fueran la entrada histórica del backend; registrar días y fecha/zona usados realmente.

### Cambios concretos por archivo

- `frontend/src/app/features/doctor/cases/case-form-modal.component.ts`: añadir control `procedureComplexity` con `Validators.required`, selector sin preselección y opciones baja/media/alta. Incluirlo en `createMutation` y reset. En `startAnalysis`, reemplazar la deducción desde urgency por `detail.classification.inputSnapshot.procedure_complexity`; mostrar **Entrada histórica no disponible** cuando falta, sin reconstruirla en el navegador. Usar también allí booleans/días guardados.
- `backend/.../cases/web/dto/CreateCaseRequest.java`: añadir `String procedureComplexity`; parsear con enum `CaseComplexity` existente, rechazar valores fuera de catálogo. En `LegalCaseService.create`, guardarlo junto con `complexitySource = reported`; si un body antiguo lo omite, guardar null en el caso y derivar solo al clasificar con `complexitySource = inferred_from_urgency_legacy`.
- `backend/.../cases/domain/LegalCase.java`: nuevos campos de BD `procedure_complexity` y `complexity_source`, getters/setters; conservar urgencia y prioridad separadas. El DTO `CaseResponse` expone ambos y los tres booleanos, para leer/editar fielmente el caso.
- `backend/.../cases/application/CaseClassificationService.java`: utilizar complejidad del caso cuando exista; `deriveComplexity` queda exclusivamente como adaptación legacy. Construir y guardar `inputSnapshot` con exactamente el payload de siete variables enviado a ML, más origen de complejidad, fecha y zona. Usar reloj inyectable/`Clock` con zona de negocio `America/Lima` para días desde fecha; futuro se valida en el formulario/API, no se disfraza de fecha válida silenciosamente. Preservar comportamiento legacy documentado para lecturas.
- `backend/.../ml/domain/MlClassification.java`: añadir `input_snapshot` JSONB y `pipeline_version`; exponerlos en `cases/web/dto/MlClassificationDto.java`. `modelVersion` sigue `rf-v2` o `rules-v1`; la versión nueva de pipeline, por ejemplo `risk-input-pipeline-v3`, registra integración distinta sin fingir reentrenamiento del modelo.
- `frontend/src/app/core/api/cases.api.ts`: ampliar/unificar `MlClassificationDto`, `MlClassificationExtended`, `CaseDetailDto`, `CaseReportDto`, `EditCaseBody`; eliminar duplicaciones que pierden los campos nuevos. Actualizar el contrato y los tipos generados de CreateCaseRequest/CaseResponse.
- `backend/.../cases/web/dto/EditCaseRequest.java`, `CaseWorkflowService.edit` y modal de edición en `frontend/.../shared/features/case-detail/case-detail.component.ts`: incluir complejidad y booleanos. Omisión en edición significa **conservar**, no resetear. El formulario se inicializa desde `CaseResponse`, no defaults de caso nuevo. Conservar permisos y estados editables actuales.
- Al editar entradas relevantes de un caso ya clasificado, mantener la clasificación anterior y marcarla como correspondiente a inputs anteriores. Implementar POST nuevo `/api/legal-cases/{id}/reclassify` para médico propietario en estado editable antes de asignación: clasificar de nuevo, añadir registro/evento, no sobrescribir la anterior. En `getDetail`, devolver la última por `createdAt DESC, id DESC` y conservar acceso histórico mediante GET nuevo `/api/legal-cases/{id}/classifications`; la actualización de riesgo invalida ranking actual por huella pero no borra runs. La actualización manual de prioridad permanece visible en evento; no convertir una lectura en reclasificación.

Para esos endpoints, añadir métodos `CasesApi.reclassify(id)` y `CasesApi.classifications(id)`, controlador `LegalCaseController` y métodos de `MlClassificationRepository`: lectura latest con orden `createdAt DESC,id DESC` y lista histórica con el mismo orden. En el detalle, mostrar historial seleccionable y botón **Reevaluar riesgo** solo para médico propietario en estado editable; mutation POST, seguida de invalidación de detalle/historial, nunca dentro de una query GET. Añadir `isStale` calculado al DTO de detalle/clasificación: comparar entradas de riesgo y fecha del evento con la fotografía; cambios de notas/título no afectan riesgo, aunque sí pueden afectar el ranking. Si la fotografía legacy falta, el estado es desconocido, no una coincidencia fabricada. No exigir BD extra para ese booleano calculado.

Ejemplo del payload Python que debe conservarse como fotografía (las tres categorías no dependen entre sí):

```json
{"specialty":"Psiquiatría","procedure_complexity":"baja","priority":"alta","documentation_complete":true,"informed_consent":true,"has_prior_complaints":false,"time_since_incident_days":0}
```

El input de prioridad usa urgencia percibida, no la prioridad previamente sugerida por RF: evitar retroalimentar al modelo con su propia salida en una reevaluación. Registrar también `evaluatedAt`, `eventDate`, `timeZone` y `complexitySource` como metadata fuera de las siete variables de inferencia.

### Aceptación

- CA-05-A: un caso con urgencia alta y complejidad baja llega a ML con ambos valores distintos y conserva esa fotografía.
- CA-05-B: omisión/valor inválido en el nuevo flujo recibe validación clara; la compatibilidad legacy tiene conducta documentada, no una conversión oculta.
- CA-05-C: cambiar la fecha de consulta del historial no cambia las entradas históricas.
- CA-05-D: las clasificaciones antiguas permanecen intactas; la procedencia de los datos se puede explicar.
- CA-05-E: un especialista revisa escenarios de distintos niveles. No se declara error de RF solo porque una descripción breve parezca de bajo riesgo; el clasificador actual usa siete entradas estructuradas y no interpreta semánticamente la descripción para riesgo.

## 9. H-06 — Correos rechazados y fallos sin trazabilidad funcional

**Tipo:** fallo observado de configuración y mejora necesaria de seguimiento. **Prioridad:** alta si las tareas incluyen avisos; el caso puede avanzar aunque el correo falle.

### Configuración externa

[EVIDENCIA] `application.yml` ya usa `RESEND_API_KEY`, `MAIL_FROM`, `MAIL_REPLY_TO` y `RISK_ALERT_EMAIL`. `ResendClient` separa remitente y Reply-To. No hace falta convertir un correo Gmail de un usuario en el remitente de la plataforma.

[RECOMENDACIÓN] Verificar un dominio controlado por el equipo en Resend, aplicar los registros DNS indicados por el proveedor y configurar `MAIL_FROM` con una dirección de ese dominio. No intentar verificar un dominio público como `gmail.com`. La documentación oficial exige un dominio propio verificado para este envío. [Fuente: dominios verificados de Resend](https://resend.com/docs/dashboard/domains/introduction).

Renato configura en el servicio **backend** de Railway dominio/remitente y variables vigentes; no requiere herramientas de Claude para esa tarea. El propósito del código es separar `from` —identidad de plataforma— de `reply_to` —respuesta al usuario—, conservar el error real y comprobarlo con un cliente simulado. No registrar claves ni valores privados en Git. Claude no necesita conocer sus valores.

### Requisitos funcionales

- RF-06.1: solicitud y respuesta se conservan aunque el correo falle; el usuario no pierde la operación de negocio.
- RF-06.2: informar que la solicitud quedó registrada y distinguir el estado del aviso. No mostrar “correo entregado” por un HTTP exitoso de la API de negocio.
- RF-06.3: consultar estado por notificación y recurso asociado desde un acceso autorizado. Un fallo permanente requiere intervención, no reintentos ilimitados.
- RF-06.4: respetar los destinatarios del flujo actual: solicitud recibida para abogado, solicitud contestada para médico y alerta de riesgo para destinatario configurado.

### Especificación técnica propuesta

Archivos: `notifications/ResendClient.java`, `notifications/MailNotifier.java`, `notifications/MailTemplates.java`, `ml/application/RiskAlertNotifier.java` y servicios que disparan los avisos.

Añadir un registro persistente de notificación o patrón outbox con: UUID, tipo, caseId/requestId/classificationId según evento, destinatario protegido, estado, intentos, fechas, último error sanitizado e ID del proveedor. Reutilizar la infraestructura existente si hay una equivalente; no introducir otro proveedor sin necesidad.

Crear el registro en la transacción de negocio y despachar **después del commit**, evitando avisos de operaciones revertidas. Un worker recupera pendientes tras reinicio. Usar una clave estable por evento/destinatario, bloqueo de trabajo y deduplicación. Si la API aceptó un correo pero la conexión perdió la respuesta, no reenviar sin control de idempotencia. El contrato relevante se transcribe abajo para no exigir acceso de Claude al proveedor. [Fuente: API de envío de Resend](https://resend.com/docs/api-reference/emails/send-email).

Estados mínimos diferenciados: `pending`, `processing`, `accepted_by_provider`, `failed`, `skipped`. `delivered`/`bounced` solo si existe confirmación del proveedor, por webhook validado u otra evidencia. Mostrar aceptación del proveedor y entrega como eventos diferentes.

Reintentar de manera acotada errores transitorios —por ejemplo timeout, 429 y ciertos 5xx— con espera creciente. Un 403 por dominio no verificado permanece fallido hasta corregir configuración. No imprimir tokens, HTML completo, contraseñas ni descripciones clínicas en logs de diagnóstico.

### Cambios concretos de notificaciones

1. Crear `notifications/domain/NotificationOutbox.java`, `notifications/infrastructure/NotificationOutboxRepository.java`, `notifications/NotificationService.java` y `notifications/NotificationWorker.java` con el esquema del apartado 12. Guardar UUID y `eventKey` estable: `contact_request_received:<requestId>`, `contact_request_answered:<requestId>:<status>` y `risk_alert:<classificationId>`.
2. En `ContactRequestService` y `CaseClassificationService`, encolar en la transacción de negocio tras obtener el ID del recurso. La alerta de riesgo hoy se dispara antes de guardar la clasificación: mover el encolado a después de `classificationRepository.save`/flush para tener classificationId estable. Sustituir para esos eventos la llamada directa `@Async` a `MailNotifier`/`RiskAlertNotifier`. El worker solo ve filas confirmadas; un rollback elimina también su outbox. No tener dos canales enviando el mismo evento.
3. Reutilizar `MailTemplates`. `from` procede exclusivamente de la configuración del servidor. El outbox guarda destinatario, Reply-To, asunto/plantilla y payload mínimo privado. No incluir contraseña ni token de recuperación en este outbox nuevo. Preservar bienvenida y recuperación existentes; probar que no se rompen sin migrarlas innecesariamente.
4. Refactorizar `ResendClient.send` para devolver `providerMessageId` leído del JSON `id`, en vez de descartar la respuesta mediante `toBodilessEntity()`. Añadir overload con clave de idempotencia conservando firmas antiguas usadas por otros consumidores.
5. HTTP: POST `https://api.resend.com/emails`; headers `Authorization: Bearer <RESEND_API_KEY>`, `Content-Type: application/json`, `Idempotency-Key: notification/<outboxUuid>`; body `{"from":"<MAIL_FROM>","to":["<destinatario>"],"subject":"...","html":"...","reply_to":"<opcional>"}`. Omitir Reply-To vacío. Respuesta aceptada: JSON con `id`. Mantener clave y payload en reintentos. Resend deduplica por clave durante 24 h; fuera de esa ventana no afirmar garantía de no duplicación. [Fuente: idempotencia de Resend](https://resend.com/docs/dashboard/emails/idempotency-keys).
6. Worker por lotes pequeños con bloqueo/lease para impedir dos procesadores del mismo trabajo. `@Scheduled`, intervalo configurable de 15 s, propiedad `app.notifications.worker-enabled`, tres intentos totales. Fallos transitorios vuelven a pending a los 30 s y 120 s; al tercer fallo, failed. Processing sin finalizar tras 2 min se recupera con la misma clave dentro de la ventana del proveedor; un resultado ambiguo fuera de ella queda failed para investigación, sin reenvío automático.
7. Sin clave/destinatario, estado skipped. 400/401/403 de configuración/datos: failed sin reintento automático; 429/5xx/timeout: transitorios acotados. 2xx sin ID: respuesta ambigua, nunca entregado; conservar diagnóstico y el recurso de negocio.
8. Añadir GET `/api/legal-cases/{id}/notifications`, autorizado por permisos del caso. Retornar `id`, `type`, `resourceId`, estado, intentos, fechas y error público sanitizado; admin puede consultar ID del proveedor/código HTTP. No retornar API key, HTML o destinatarios ajenos. En detalle/solicitud frontend, mostrar **Solicitud registrada · aviso pendiente/fallido/aceptado por proveedor**.
9. No implementar un webhook inseguro para usar delivered: esta entrega usa accepted_by_provider y Renato recoge evidencia de inbox. Delivered nunca se asigna sin confirmación verificable.
10. Entregar properties sin secretos para `MAIL_FROM`, `MAIL_REPLY_TO`, `RISK_ALERT_EMAIL`, `RESEND_API_KEY`, worker/límites y `APP_FRONTEND_URL`, que es la variable actual de `app.frontend.url`. No introducir `FRONTEND_URL` como una segunda variable que el código no lee.

**Propósito:** conservar la operación y saber si su aviso está pendiente, falló o fue aceptado. Claude prueba con cliente HTTP fake 200/403/429/500/timeout; Renato verifica la recepción real después.

### Aceptación

- CA-06-A: con proveedor simulado en 403, la solicitud se registra una vez y su aviso queda fallido con correlación al caso.
- CA-06-B: rollback de la operación no dispara un aviso; reinicio recupera pendientes sin duplicar el evento.
- CA-06-C: error transitorio/reintento respeta el máximo; error permanente no entra en bucle.
- CA-06-D: Renato verifica los tres avisos, destinatarios, Reply-To y recepción en integración. Claude entrega antes tests con proveedor simulado e ID conservado. Guardar ID del proveedor y evidencia de bandeja/evento; aceptación de API sola no demuestra recepción.
- CA-06-E: omisión por falta de clave/destinatario es visible como `skipped`, nunca como enviado.

## 10. Regresión del flujo completo y problemas que no deben inventarse

[EVIDENCIA] `CaseWorkflowService.close` y el detalle frontend ya implementan cierre desde `respondida`. No construir un segundo flujo de cierre solo porque el caso de referencia no haya sido cerrado.

**Recorrido a verificar con cuentas de prueba separadas:**

1. Registro del médico, consentimiento, login/logout y consulta de sus casos.
2. Creación de consulta simulada; captura de factores, clasificación persistida y explicación.
3. Generación guardada del ranking; elección y solicitud al abogado.
4. Abogado ve su solicitud y puede aceptar o rechazar; su sesión no puede ver casos ajenos fuera de los permisos establecidos.
5. Aceptación vincula al abogado y pasa a `asignada`; inicia revisión y pasa a `en_revision`.
6. Respuesta legal queda asociada al caso y pasa a `respondida`; médico puede consultar y marcar revisada.
7. Médico cierra desde el estado admitido y pasa a `cerrada`; queda evento/auditoría.
8. Historial y documentos se consultan con permisos. Si T06 necesita documentos, preparar fixtures sintéticos y verificar carga/consulta/descarga según el escenario aprobado. La ausencia de documentos en el caso de referencia no acredita una falla de ese módulo.

**CA-07-A:** ejecutar el recorrido hasta cierre y conservar IDs, estados, eventos, ranking, vínculo, respuesta y avisos. No cerrar ni reescribir el caso de referencia para fabricar evidencia de una prueba pasada.

**CA-07-B:** probar autorización negativa: otro médico no puede leer/alterar la consulta, generar su ranking ni revisar su respuesta; otro abogado no puede aceptar la solicitud ni responder el caso asignado a un tercero. Respetar las facultades del administrador definidas por el sistema.

**CA-07-C:** repetir/reenviar aceptación, respuesta o cierre no produce asignaciones incompatibles ni efectos duplicados. Respetar las transiciones legales, cancelación/rechazo y reglas de solicitudes pendientes.

**CA-07-D:** verificar login y persistencia de sesión en Vercel/Railway con navegador limpio. Un 401 de `/api/auth/me` sin sesión puede ser esperado; los 403 anteriores de login requieren reproducción y respuesta de servidor antes de atribuirlos a CORS, cookies o CSRF. No desactivar esos controles para conseguir que una prueba pase. Errores `chrome-extension://` se diagnostican fuera del backend.

**Cuentas sintéticas:** Renato prepara médicos/abogados/administrador y disponibilidad en BD. Claude crea fixtures locales equivalentes —IDs ficticios y actores simulados— para probar permisos sin credenciales productivas. Los hashes de contraseña no son recuperables en texto; las contraseñas no son parte de la corrección y no se incluyen en documentos/resultados.

## 11. Evidencia e instrumentación para el OE4

### 11.1 Manifiesto de versión y exportación

[RECOMENDACIÓN] Antes de las sesiones, guardar un manifiesto con SHA frontend/backend/ML, IDs de despliegue, versiones de migración, modelo, pipeline, corpus/candidatos, escenarios, protocolo, zona horaria y fecha. Congelar esos elementos en la muestra principal; cambios sustantivos posteriores requieren nueva versión y decisión metodológica documentada.

Implementar GET `/api/legal-cases/{id}/evidence` para médico propietario/admin con el control de acceso del caso y lectura pura. Retornar JSON con `schemaVersion`, `exportedAt`, `caseId`, estado, historial de clasificaciones/inputs/versiones, runs/resultados, solicitudes con vínculo y eventos/timestamps. Reutilizar consultas guardadas, sin llamar a ML ni cambiar estados. En frontend, botón **Descargar evidencia técnica** en el detalle autorizado genera `.json`; no desarrollar un portal de investigación separado.

Omitir contraseñas, tokens, correos, CMP, teléfono y nombres reales del médico/participante. Exportar metadatos y factores simulados necesarios, no entidades JPA serializadas indiscriminadamente ni payloads de correo. Los IDs técnicos enlazan evidencia; Renato asigna código de participante y guarda la correspondencia identificable aparte. La fotografía interna completa de inputs/corpus no se expone públicamente: exportar versión/hash y subconjunto anonimizado suficiente para análisis.

**CA-08-A:** otro miembro del equipo puede enlazar registros por IDs y reproducir el recorrido sin depender de capturas aisladas ni de recalcular ML. Registrar la fecha de extracción y proteger los artefactos originales.

### 11.2 Medición de rapidez con abogados sintéticos

Los abogados simulados sirven para evaluar interacción y pertinencia de perfiles, pero no demuestran acceso real a asesoramiento profesional. Las alternativas siguientes son contexto para Renato, no instrucciones de esperar una decisión antes de programar:

- **Alternativa A — búsqueda y solicitud:** AS-IS, seleccionar un abogado adecuado de un directorio equivalente y registrar una solicitud por el procedimiento manual; TO-BE, seleccionar del ranking y registrar la solicitud en la app. Mismos escenarios, información y corpus; misma definición de éxito. Permite afirmar reducción de búsqueda/solicitud en un escenario simulado, no reducción del tiempo hasta recibir asesoramiento legal.
- **Alternativa B — asesoramiento simulado con operador:** un operador representa al abogado y sigue un guion, reglas y disponibilidad equivalentes en ambas condiciones. Fin: recepción de orientación simulada verificable. Registrar tiempos de interacción y espera; no ajustar artificialmente la espera AS-IS para favorecer TO-BE. La conclusión queda limitada a esa simulación.
- **Alternativa C — piloto con abogado profesional:** comparar el acceso y respuesta bajo condiciones acordadas y comparables. La disponibilidad, complejidad y calidad mínima de la orientación deben quedar definidas; la participación profesional no acredita representatividad nacional.

[HECHO] Si el indicador mide hasta orientación recibida, **A no basta**: solo mide búsqueda/solicitud. Renato define y sustenta el protocolo B/C o un alcance distinto. Claude conserva los eventos y fechas para medir cualquiera de esos intervalos sin volver a desarrollar la app.

Registrar por sesión: código del participante, escenario, condición, orden, inicio/fin, tiempo bruto, pausas, motivo, ayuda, éxito, incidencias y versión. Contrabalancear el orden si el diseño aprobado lo permite y usar tareas equivalentes para controlar aprendizaje. Definir exclusiones antes de ver resultados.

Separar métricas:

- `responseTimeMs` de ML mide latencia técnica, no acceso al asesoramiento.
- Duración completa de tarea incluye navegación, lectura y esperas visibles según protocolo. El modal contiene una espera visual mínima de unos 2,8 s: medir la experiencia real, no restarla después para mejorar resultados. Si se cambia esa espera, hacerlo antes del congelamiento y registrarlo.
- Tiempo hasta orientación incluye espera por el abogado/operador cuando ese sea el final acordado.

El caso de referencia tardó aproximadamente **56 min 47 s** desde creación hasta respuesta. Es un antecedente de un recorrido de prueba, sin AS-IS equivalente ni control de pausas; no demuestra el umbral de rapidez.

Fórmula por par válido: `reducción_i = (ASIS_i − TOBE_i) / ASIS_i × 100`, con `ASIS_i > 0` y unidades iguales. El plan propone **mediana de reducciones individuales** como estimador principal y media como secundario. No sustituirlo por la reducción calculada con promedios agregados. Conservar valores negativos y registrar faltantes sin inventarlos.

**CA-08-B:** se pueden obtener ambos tiempos y eventos finales equivalentes por participante. Los timestamps UTC del sistema se convierten de forma consistente para presentación en Lima; no mezclar reloj del cliente/servidor sin registrar sus diferencias. Cronometraje supervisado puede complementar los eventos; no requiere desarrollar todo un módulo de encuestas.

### 11.3 SUS, facilidad y utilidad

- Aplicar SUS de diez ítems después de las tareas con una versión española aprobada, conservando escala/polaridad. Por participante: impares menos 1, pares igual a 5 menos respuesta, suma por 2,5. Evaluar **media SUS ≥70**; no interpretar el puntaje como porcentaje de usuarios satisfechos.
- Facilidad y utilidad son preguntas separadas, escala 1–5, favorables 4 o 5. Porcentaje = favorables/respuestas válidas ×100; cumplir ≥80 % por dimensión. Con veinte respuestas válidas son al menos dieciséis en cada una.
- [PROBLEMA] El criterio del equipo menciona utilidad **para apoyar decisiones**, mientras que la pregunta disponible se refiere a **acceso al asesoramiento**. Renato alinea el instrumento; Claude no necesita modificar código ni esperar una respuesta para resolver esta diferencia documental.
- Elegibilidad: acreditar al participante profesional mediante el procedimiento aprobado, separado de la cuenta sintética usada para tareas. El CMP vacío de una cuenta de demostración no acredita ni desacredita por sí solo al participante real. Conservar verificación y consentimiento en archivo restringido, enlazados mediante código.
- No completar encuestas por los participantes, imputar SUS incompleto ni eliminar opiniones desfavorables. Registrar reclutados, iniciados, completados, excluidos y analizados con motivos definidos.

**CA-08-C:** datos crudos y cálculos reproducibles permiten reconstruir cada indicador sin usar las métricas del backend como sustituto de percepción del usuario.

### 11.4 Matching y métricas del modelo

[EVIDENCIA] El protocolo existente propone adjudicación humana, escala 0/1/2 y qrels finales. `ml-service/data/reference/ds04_qrels.csv` no está presente en el contexto técnico revisado. Los scripts y auto-tests no sustituyen etiquetas humanas.

- Congelar consultas, perfiles, resultados y versiones para los jueces. Puntuaciones y posición quedan en H-02; los jueces evalúan pertinencia sin adoptar automáticamente el score ML como relevancia.
- Definir acuerdo/resolución de discrepancias y relevancia binaria antes del análisis. El plan cuenta 2 como relevante y propone sensibilidad para 1; cerrar esa definición previamente. `Precision@3` es el promedio por consulta de relevantes entre los tres primeros, bajo el protocolo aprobado. Con menos de tres candidatos, seguir la regla predefinida, no inventar ni excluir silenciosamente resultados.
- Ejecutar `ml-service/evaluation/run_ablation.py` únicamente con qrels reales y corpus/consultas compatibles; archivar parámetros, outputs y limitaciones. No crear etiquetas sintéticas presentándolas como juicio experto.
- Separar F1 de riesgo, pertinencia del matching, score de un caso y SUS. El F1 aproximado 0,788 de `rf-v2` procede de evaluación sintética; no valida profesionales/pacientes reales ni el ranking de este caso.
- En métricas admin, identificar procedencia, dataset, versión y fecha. Los valores seed/históricos de `model_metrics` no deben rotularse como evaluación vigente del corpus productivo. Si falta evaluación actual, mostrarla como pendiente.

**Cambio específico sin exigir métricas nuevas:** en `frontend/.../admin/metrics/metrics.component.ts`, mostrar `modelVersion`, `evaluatedAt`, `datasetSize` y `notes` del `ModelMetricDto` existente por fila, bajo **Evaluaciones registradas, no métricas en vivo**. Matching actual queda **Evaluación humana pendiente** mientras no exista procedencia explícita del estudio actual. No inferir vigencia por fecha reciente ni por el nombre de versión. En `backend/.../ml/application/ModelMetricService.java` y `ml/web/dto/ModelMetricDto.java`, puede añadirse `evaluationStatus` con valor conservador `historical_unverified` para datos antiguos; verified exige evidencia explícita, no un heurístico del texto. Claude no inserta/edita filas para que el panel parezca completo; Renato aporta después datos humanos reales.

**CA-08-D:** cada resultado reportado tiene fuente y versión; la adjudicación reproduce el ranking congelado. No se afirma validación completa de pertinencia jurídica con solo opiniones de usuarios sin experto legal.

## 12. Contrato de BD para Renato; mapeos que Claude debe implementar

Esta sección fija nombres y tipos compartidos para evitar que backend y BD evolucionen con supuestos distintos. **Claude implementa los mapeos y entrega el contrato final; Renato ejecuta la preparación de BD.** No exigir conexión a Railway para escribir entidades/repositorios ni almacenar datos solo en memoria como solución final. Los repositorios mock son para pruebas, no sustituyen la persistencia del producto.

Los campos nuevos nullable permiten leer filas históricas. Los valores requeridos para registros nuevos se validan en los servicios y con constraints compatibles. JPA JSONB: usar `@JdbcTypeCode(SqlTypes.JSON)` y objeto/JsonNode de Jackson, serializando objetos/arrays como tales; no guardar un JSON escapado como string JSON dentro de otro. UUID y fechas se generan en backend/BD conforme a convenciones existentes y se retornan después del flush.

### 12.1 Nueva tabla `recommendation_runs`

- `id UUID PRIMARY KEY`.
- `doctor_id UUID NOT NULL` → `profiles.id`; `case_id UUID NOT NULL` → `cases.id`.
- `idempotency_key VARCHAR(100) NOT NULL`; `request_hash VARCHAR(64) NOT NULL`.
- `status VARCHAR(20) NOT NULL`: processing/completed/failed.
- `origin VARCHAR(20) NULL`: ml/fallback cuando se completa; null mientras no existe resultado.
- `model_version VARCHAR(100) NULL`, `pipeline_version VARCHAR(100) NOT NULL`, `top_k INTEGER NOT NULL` con valor 10 en el flujo actual.
- `input_hash VARCHAR(64) NULL`, `corpus_hash VARCHAR(64) NULL`.
- `input_snapshot JSONB NULL`, `corpus_snapshot JSONB NULL`, `weights JSONB NULL`, `model_info JSONB NULL`; al completar son la fotografía usada, no referencias mutables a perfiles actuales. Corpus conserva también el `LawyerCardDto` mostrado junto a los campos enviados al ML; no necesita emails/telefonos del abogado para puntuar.
- `candidate_count INTEGER NULL`, `result_count INTEGER NULL`; 0 es un conteo legítimo, null es dato aún desconocido.
- `error_code VARCHAR(100) NULL`, `error_message TEXT NULL`, sanitizados.
- `created_at TIMESTAMPTZ NOT NULL`, `updated_at TIMESTAMPTZ NOT NULL`, `completed_at TIMESTAMPTZ NULL`.
- Unique `(doctor_id,idempotency_key)`; índice `(case_id,created_at DESC,id DESC)` y de recuperación por estado/updated_at. Checks de catálogo y conteos no negativos.

Las fotografías se fijan antes de la inferencia o como parte de la adquisición; un cambio de perfil durante la llamada no modifica los datos de ese run. Un run completado no admite actualización de fotos, pesos ni resultados. Su status completed y todas sus filas se confirman en una sola transacción.

### 12.2 Ampliar `match_recommendations`

Conservar columnas actuales `score NUMERIC(5,2)` porcentual, reasons, factors, algorithm_version y sus relaciones. Añadir:

- `run_id UUID NULL` → `recommendation_runs.id`; null para registros legacy.
- `rank INTEGER NULL`, `score_raw NUMERIC(7,6) NULL`, `content_score_raw NUMERIC(7,6) NULL`, `performance_score_raw NUMERIC(7,6) NULL`.
- `lawyer_snapshot JSONB NULL`, `matched_specialties TEXT[] NULL`.
- Índice por run/rank; únicos parciales `(run_id,rank)` y `(run_id,lawyer_id)` para run no null. Checks raw 0–1 y rank >0.

Para nuevas filas, `score_raw` representa el score emitido por el algoritmo, incluso el fallback normalizado: `fallbackPercent / 100`. Components raw del fallback quedan null. `score` conserva el porcentaje compatible. No dividir el score entero legacy entre 100 y presentarlo como precisión recuperada; nunca existió ese detalle histórico.

### 12.3 Ampliar `contact_requests`

- `recommendation_id UUID NULL` → `match_recommendations.id`.
- `selection_source VARCHAR(30) NULL`: recommendation/directory/legacy_untracked.
- Índice por recommendation_id; FK valida referencia. Registros antiguos quedan null/legacy sin backfill ficticio.

`ml_score` ya existe: **no añadir una segunda columna con ese nombre ni cambiar sus unidades**. Nuevo contacto del ranking copia `score_raw × 100` con escala 2. Contacto de directorio queda null. Request body no admite score como dato confiable. La ejecución se obtiene por relación, no se necesita duplicar runId en BD.

Mantener reglas actuales de duplicados. Si el esquema requiere un índice adicional para garantizar la regla vigente, incluir su definición y comprobación previa de duplicados para Renato; no eliminar filas ni redefinir silenciosamente la regla de médico/abogado/caso.

### 12.4 Ampliar `cases` y `ml_classifications`

En `cases`:

- `procedure_complexity VARCHAR(20) NULL`, check baja/media/alta cuando no null.
- `complexity_source VARCHAR(50) NULL`: reported o inferred_from_urgency_legacy cuando corresponda.

En `ml_classifications`:

- `input_snapshot JSONB NULL` con inputs reales y metadata de evaluación.
- `pipeline_version VARCHAR(100) NULL`.
- Índice por `(case_id,created_at DESC,id DESC)` si no existe uno equivalente.

Ya existen `complexity`, risk_score/risk_level/risk_factors y model_version de clasificación: conservarlos. Históricos sin fotografía continúan así. Nueva clasificación rellena los campos; volver a evaluar inserta otra fila. El latest usa orden determinista; GET histórico no deduce inputs faltantes a partir del caso editado.

### 12.5 Nueva tabla `notification_outbox`

- `id UUID PRIMARY KEY`; `event_key VARCHAR(200) NOT NULL UNIQUE`; `type VARCHAR(50) NOT NULL`.
- `case_id UUID NULL` → cases.id; `resource_type VARCHAR(50) NOT NULL`; `resource_id UUID NOT NULL`, el request/classification asociado. Validar la relación en servicio; una FK única no puede apuntar a tres tablas diferentes.
- `recipient TEXT NOT NULL`, `reply_to TEXT NULL`, `subject TEXT NOT NULL`, `payload JSONB NOT NULL` —privados—; el remitente lo aplica configuración y la fotografía del payload enviado se mantiene estable al reintentar.
- `status VARCHAR(30) NOT NULL`: pending/processing/accepted_by_provider/failed/skipped.
- `attempt_count INTEGER NOT NULL DEFAULT 0`, `next_attempt_at TIMESTAMPTZ NULL`, `locked_until TIMESTAMPTZ NULL`, `worker_token UUID NULL`.
- `provider_message_id VARCHAR(200) NULL`, `provider_http_status INTEGER NULL`, `last_error_code VARCHAR(100) NULL`, `last_error_message TEXT NULL`, `retryable BOOLEAN NOT NULL DEFAULT false`.
- `created_at TIMESTAMPTZ NOT NULL`, `updated_at TIMESTAMPTZ NOT NULL`, `accepted_at TIMESTAMPTZ NULL`.
- Índice por `(status,next_attempt_at)`, por case_id/created_at y recurso; checks de status e intentos no negativos.

Para skipped por destinatario ausente, el campo recipient puede ser cadena vacía marcada skipped; no un destinatario inventado. El worker reclama y confirma trabajo con `worker_token` para que un worker antiguo, tras expirar su lease, no sobrescriba la finalización de otro. No transportar payload privado al DTO público de notificaciones.

Congelar el payload HTTP efectivo al primer intento, incluido el remitente resuelto de configuración, para que cambios posteriores de `MAIL_FROM` no generen otro body bajo la misma clave del proveedor. Error 409 de idempotencia con payload distinto se registra como fallo permanente; 409 de solicitud concurrente admite reintento acotado. No tratar ambos como el mismo error. La reparación/reproceso operativo de fallos permanentes la organiza Renato sin reescribir los intentos originales.

### 12.6 Coordinación de esquema sin pausar código

Claude entrega una lista final de tablas/campos/índices/FKs, el mapping Java que los consume y el orden de compatibilidad. Puede preparar una propuesta SQL como texto de entrega para Renato, fuera de `backend/src/main/resources/db/migration`; **no instalar una migración de autoejecución**. No tocar V1–V14 aplicadas. Renato decide cómo registrar la nueva versión Flyway/manual de forma consistente y cómo aplicarla. Si las nuevas columnas no están listas, el arranque con ddl-auto validate puede fallar: eso se reporta como integración de esquema pendiente, no se solventa desactivando validación ni usando Hibernate update.

## 13. Implementación, pruebas y entrega del compañero

### Orden recomendado

1. Leer este documento y ubicar métodos/consumidores del API en el código. Empezar a implementar sin solicitar confirmación del encargo ni acceso a herramientas de Codex. Registrar divergencias reproducibles del checkout.
2. Preparar rama/diff, fixtures y pruebas locales. Renato gestiona BD y copias; entregar tempranamente el contrato del apartado 12 mientras se avanza en código con repositorios simulados.
3. Implementar H-01/H-04; H-02/H-03 en conjunto; H-05 con complejidad independiente y snapshots; H-06 con outbox y cliente simulado; evidencia y procedencia de métricas del apartado 11. No dejar H-05 esperando aprobación ni esperar dominio verificado para escribir el cliente/outbox.
4. Ejecutar aceptación por hallazgo, regresión del apartado 10, contratos y permisos. Corregir los defectos reproducidos que impidan T01–T06 y registrar su evidencia.
5. Entregar código y pruebas con lista de verificaciones que necesitan BD/servicios reales. Renato prepara esquema, integra y realiza prueba de humo; Claude no despliega por este encargo.
6. Corregir incidencias reproducidas en pruebas o integración. Renato organiza piloto, congelamiento y muestra principal; no confundir esos pendientes del estudio con código sin implementar.

### Pruebas mínimas y herramientas

- Backend: ampliar `Oe3FlowIntegrationTest` y `OwnershipIntegrationTest`; usar dobles controlados de ML/Resend para éxito, error, timeout, concurrencia y rollback. Ejecutar `mvnw.cmd test` en Windows o `./mvnw test` donde corresponda. Testcontainers requiere Docker disponible; si no existe, completar el código y las pruebas escritas, registrar que no se ejecutaron y avanzar con verificaciones disponibles.
- Frontend: en `frontend`, instalar con lockfile (`npm ci`), compilar (`npm run build`) y ejecutar pruebas dirigidas con el runner configurado (`npm test -- --watch=false`, comprobando opciones del workspace). Verificar render, valores decimales, errores, fallback, reintentos e idempotencia del flujo.
- ML: comprobar contrato y fórmula con fixtures de respuesta, además de sintaxis. El CI `ci-ml.yml` actual ejecuta `compileall`; eso no valida comportamiento ni desempeño. Añadir pruebas dirigidas si se modifica contrato/inferencia; no reentrenar como efecto lateral.
- Persistencia: si hay Docker/runtime, probar contra BD efímera de tests preparada con esquema equivalente, sin conectarse a producción. Si no hay runtime, escribir pruebas/mocks y entregarlas como no ejecutadas. Renato verifica aplicación de esquema con datos, conservación legacy y compatibilidad del binario anterior.
- End-to-end: sesiones separadas por rol; registrar CA-01…CA-08, resultado real, versión, fecha y evidencia sanitizada. Capturas pueden complementar, pero no reemplazan assertions ni datos crudos.

No imponer un número de tests como criterio académico. Elegir pruebas que detecten los fallos descritos. Un build verde, un health 200 o un script compilado no certifican entrega de correo, corrección del ranking ni validación OE4.

### Integración y reversión que se documentan para Renato

Claude prepara cambios aditivos compatibles con datos antiguos y explica requisitos de arranque, API y UI. Renato aplica esquema y configuración antes de iniciar el backend nuevo, y publica frontend después de tener API disponible. No cambiar unidades de columnas existentes ni introducir dependencias que borren historial al regenerar.

Entregar referencia del código anterior, cómo volver al binario previo y qué campos nuevos quedan conservados. **Volver a un commit no revierte la BD**; Renato gestiona backups/restauración. El código anterior debe poder convivir con tablas/columnas aditivas nuevas y datos legacy. Claude no escribe procedimientos de borrado de evidencia como reversión.

### Entregables exigidos al compañero

- PR/commit revisable con problema, cambios y limitaciones; listado de archivos y contratos afectados.
- Diseño de datos/rutas definitivo, diferencias justificadas y contrato de BD para Renato, consistente con mappings Java.
- Compatibilidad legacy y requisitos de integración/reversión; sin migraciones nuevas de autoejecución ni SQL aplicado a producción.
- Informe de ejecución de los criterios CA, evidencias y fallos pendientes. Separar automatizado, manual y no ejecutado.
- Configuración externa necesaria sin secretos; tests de correo con proveedor fake. Recepción real e infraestructura a cargo de Renato.
- Manifiesto de versión, fixtures/escenarios y mecanismo de extracción de evidencia.
- Instrucciones para operar médico/abogado/admin y registrar sesiones, sin credenciales públicas.

## 14. Definición de terminado y preparación del estudio

**Código terminado para Claude** cuando están implementadas las correcciones H-01…H-06, lectura histórica sin inferencia, DTOs/OpenAPI/tipos coherentes, exportación, UI de estados y pruebas dirigidas. Entregar contrato BD y señalar exactamente pruebas ejecutadas/no ejecutadas. Falta de Railway, credenciales, Docker o generador no justifica dejar solo una propuesta: producir el código y tests disponibles, sin inventar resultados de ejecución. Esto no exige tocar producción.

**Sistema preparado** solo cuando se cumpla y se adjunte evidencia de:

- [ ] H-01…H-06 cerrados según aceptación, incluida complejidad independiente del nuevo flujo y adaptación legacy identificada.
- [ ] T01–T06 y recorrido abogado/médico pasan; no hay defectos críticos de sesión, permisos, persistencia o documentos requeridos.
- [ ] Historial legacy conservado; ranking y selección nuevos son trazables; fallbacks y datos faltantes son visibles.
- [ ] Correos/configuración verificados, cuentas sintéticas operativas y corpus con candidatos suficientes para los escenarios.
- [ ] Piloto realizado, incidencias cerradas y versión congelada; responsables conocen el plan de incidentes/reversión.
- [ ] Renato tiene definidos población, elegibilidad, consentimiento, muestra e instrumentos; constructo de utilidad alineado con el criterio evaluado.
- [ ] AS-IS/TO-BE tiene inicio/fin equivalentes, medición viable y alcance defendible para abogados sintéticos.
- [ ] Adjudicación experta y qrels planificados/disponibles según el momento del protocolo, sin reportarlos como ejecutados por anticipado.
- [ ] Datos y evidencias pueden extraerse y analizarse de forma reproducible y anonimizada.

**OE4 validado** requiere después sesiones reales, cálculos y resultados observados: media SUS ≥70, facilidad ≥80 %, utilidad ≥80 % y reducción ≥40 % con el estimador aprobado. La pertinencia del matching se reporta según el protocolo adoptado. Si algún umbral no se alcanza, el objetivo se evalúa y documenta con ese resultado; no se alteran retrospectivamente métricas, muestra, tareas ni umbrales.

## 15. Encargo autosuficiente para pegar en Claude

> Implementa las correcciones de código de este documento. Este encargo autoriza frontend/backend/ML y pruebas locales: avanza sin pedir aprobación de cada etapa. No necesitas skills, conversación de Codex, conectores, sesión Railway ni credenciales productivas; el contexto, contratos, cambios por archivo y criterios están aquí. Renato se encarga de la BD, configuración externa, cuentas y despliegue. Tú implementas entidades/repositorios/DTOs/API/UI y entregas el contrato exacto de tablas/columnas/constraints; no ejecutas SQL productivo ni añades migraciones de autoejecución. Usa mocks/fixtures para avanzar donde falten servicios. Corrige salud ML, ranking persistido y vinculado a solicitud, desglose contenido/desempeño, decimales y significado del riesgo, captura de complejidad independiente y snapshots, outbox/estado de avisos, evidencia y procedencia de métricas. Incluye el detalle y reporte del caso entre las lecturas que nunca deben recalcular ML, y distingue corpus vivo vacío de fallback estático. Conserva legacy, autorización por propietario y clasificaciones anteriores. No cambies modelos/pesos/datasets para mejorar cifras. Completa código y tests y entrega diff, resultados ejecutados/no ejecutados, requisitos BD y compatibilidad/reversión. Que falten herramientas no autoriza afirmar tests verdes; declara limitaciones concretas. La coordinación de encuestas y protocolo no bloquea el código. No afirmes OE4 validado por terminar la implementación: las métricas humanas se obtienen después.

## 16. Procedencia histórica — referencias opcionales

- Encargo de Renato y revisión de `Caso-9ea50615` en esta conversación. Observaciones de producción corresponden al momento revisado; reconfirmar al implementar.
- Código fuente citado por ruta y método en H-01…H-06; tests y workflows inspeccionados, sin afirmar ejecución nueva.
- Contexto técnico/documental del repositorio usado por el autor: sus aspectos necesarios se transcribieron en este documento; Claude no depende de esos archivos de contexto ni de skills.
- [Plan y plantilla OE4 — propuesta](PLAN_Y_PLANTILLA_VALIDACION_OE4_PROPUESTA_2026-09-30.docx) y [REC-004](../../../asesoria/01_Recomendaciones/2026-09-30/REC-004.md).
- `docs/modelo-ml.md`, `ml-service/app/matching/model.py`, `ml-service/app/risk/model.py`, `ml-service/app/schemas.py` y `ml-service/evaluation/run_ablation.py`.
- Documentación oficial de Resend enlazada en H-06, consultada durante la elaboración. No se verificó/configuró un dominio ni se envió correo en esta tarea.

**Resultado de esta revisión:** se editó este documento por solicitud expresa de Renato y se creó REC-006. No se modificó código, configuración, BD ni despliegues. Las rutas/tablas nuevas son el objetivo de implementación, no una afirmación de disponibilidad actual.
