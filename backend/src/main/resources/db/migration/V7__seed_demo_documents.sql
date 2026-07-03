-- ============================================================================
-- Sinapsistencia — V7: Documentos de demostración
--
-- Crea documentos clínico-legales vinculados a las consultas del seed (V3/V4).
-- Cubre todos los tipos y estados del sistema:
--   borrador, pendiente_firma, firmado, archivado
--   historia_clinica, consentimiento_informado, informe_medico, receta,
--   orden_laboratorio, certificado_medico, documento_legal
-- Ley 29733: todo contenido es simulado, sin datos reales de pacientes.
-- ============================================================================

-- ── 1. Documentos (current_version_id se actualiza al final por FK circular) ─

INSERT INTO documents (id, title, type, status, author_id, case_id) VALUES

    -- ── Caso-001 · Complicación post-quirúrgica (respondida / alta) ──────────
    ('e0000000-0000-0000-0000-000000000001',
     'Historia Clínica — Caso-001',
     'historia_clinica', 'firmado',
     'd0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000001'),

    ('e0000000-0000-0000-0000-000000000002',
     'Consentimiento Informado — Caso-001',
     'consentimiento_informado', 'firmado',
     'd0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000001'),

    ('e0000000-0000-0000-0000-000000000003',
     'Informe Médico — Complicación Post-quirúrgica',
     'informe_medico', 'firmado',
     'd0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000001'),

    ('e0000000-0000-0000-0000-000000000004',
     'Descargo Legal — Respuesta Preliminar Caso-001',
     'documento_legal', 'firmado',
     'd0000000-0000-0000-0000-000000000002', 'c0000000-0000-0000-0000-000000000001'),

    -- ── Caso-002 · Diagnóstico tardío (cerrada) ──────────────────────────────
    ('e0000000-0000-0000-0000-000000000005',
     'Historia Clínica — Caso-002',
     'historia_clinica', 'archivado',
     'd0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000002'),

    ('e0000000-0000-0000-0000-000000000006',
     'Informe Médico — Evaluación Diagnóstica',
     'informe_medico', 'archivado',
     'd0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000002'),

    ('e0000000-0000-0000-0000-000000000007',
     'Resolución Legal — Caso-002 Cerrado',
     'documento_legal', 'archivado',
     'd0000000-0000-0000-0000-000000000002', 'c0000000-0000-0000-0000-000000000002'),

    -- ── Caso-003 · Cicatriz post-operatoria (pendiente) ──────────────────────
    ('e0000000-0000-0000-0000-000000000008',
     'Consentimiento Informado — Procedimiento Estético Caso-003',
     'consentimiento_informado', 'pendiente_firma',
     'd0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000003'),

    ('e0000000-0000-0000-0000-000000000009',
     'Historia Clínica — Caso-003',
     'historia_clinica', 'borrador',
     'd0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000003'),

    -- ── Caso-004 · Tiempo de espera en cirugía de emergencia (clasificada) ───
    ('e0000000-0000-0000-0000-000000000010',
     'Historia Clínica — Caso-004',
     'historia_clinica', 'borrador',
     'd0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000004'),

    ('e0000000-0000-0000-0000-000000000011',
     'Informe de Emergencia — Caso-004',
     'informe_medico', 'borrador',
     'd0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000004'),

    -- ── Caso-005 · Hemorragia post-parto (asignada / crítica) ────────────────
    ('e0000000-0000-0000-0000-000000000012',
     'Consentimiento Informado — Parto Caso-005',
     'consentimiento_informado', 'firmado',
     'd1000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000005'),

    ('e0000000-0000-0000-0000-000000000013',
     'Informe Obstétrico — Hemorragia Post-parto',
     'informe_medico', 'firmado',
     'd1000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000005'),

    ('e0000000-0000-0000-0000-000000000014',
     'Evaluación Legal Preliminar — Caso-005',
     'documento_legal', 'borrador',
     'd0000000-0000-0000-0000-000000000002', 'c0000000-0000-0000-0000-000000000005'),

    -- ── Caso-006 · Reacción alérgica a anestesia (en_revision / alta) ────────
    ('e0000000-0000-0000-0000-000000000015',
     'Consentimiento Informado — Anestesia Caso-006',
     'consentimiento_informado', 'firmado',
     'd1000000-0000-0000-0000-000000000002', 'c0000000-0000-0000-0000-000000000006'),

    ('e0000000-0000-0000-0000-000000000016',
     'Informe de Incidente — Reacción a Anestesia',
     'informe_medico', 'firmado',
     'd1000000-0000-0000-0000-000000000002', 'c0000000-0000-0000-0000-000000000006'),

    ('e0000000-0000-0000-0000-000000000017',
     'Análisis Legal — Caso-006',
     'documento_legal', 'borrador',
     'd2000000-0000-0000-0000-000000000003', 'c0000000-0000-0000-0000-000000000006'),

    -- ── Caso-007 · Mala práctica en parto (respondida / alta) ────────────────
    ('e0000000-0000-0000-0000-000000000018',
     'Informe Obstétrico — Parto Distócico Caso-007',
     'informe_medico', 'firmado',
     'd1000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000007'),

    ('e0000000-0000-0000-0000-000000000019',
     'Respuesta Legal — Caso-007',
     'documento_legal', 'firmado',
     'd2000000-0000-0000-0000-000000000002', 'c0000000-0000-0000-0000-000000000007'),

    -- ── Caso-008 · Error en historia clínica pediátrica (en_revision) ─────────
    ('e0000000-0000-0000-0000-000000000020',
     'Historia Clínica Corregida — Caso-008',
     'historia_clinica', 'pendiente_firma',
     'd1000000-0000-0000-0000-000000000003', 'c0000000-0000-0000-0000-000000000008'),

    ('e0000000-0000-0000-0000-000000000021',
     'Informe de Corrección Documental — Caso-008',
     'informe_medico', 'borrador',
     'd1000000-0000-0000-0000-000000000003', 'c0000000-0000-0000-0000-000000000008'),

    -- ── Caso-009 · Fractura mal consolidada (cerrada) ─────────────────────────
    ('e0000000-0000-0000-0000-000000000022',
     'Historia Clínica — Caso-009',
     'historia_clinica', 'archivado',
     'd1000000-0000-0000-0000-000000000004', 'c0000000-0000-0000-0000-000000000009'),

    ('e0000000-0000-0000-0000-000000000023',
     'Informe Radiológico — Consolidación Ósea Caso-009',
     'informe_medico', 'archivado',
     'd1000000-0000-0000-0000-000000000004', 'c0000000-0000-0000-0000-000000000009'),

    ('e0000000-0000-0000-0000-000000000024',
     'Acuerdo Legal — Cierre Caso-009',
     'documento_legal', 'archivado',
     'd2000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000009'),

    -- ── Caso-010 · Negligencia en urgencias (pendiente) ──────────────────────
    ('e0000000-0000-0000-0000-000000000025',
     'Historia Clínica — Ingreso por Emergencia Caso-010',
     'historia_clinica', 'borrador',
     'd1000000-0000-0000-0000-000000000004', 'c0000000-0000-0000-0000-000000000010'),

    -- ── Caso-011 · Consentimiento incompleto (asignada) ──────────────────────
    ('e0000000-0000-0000-0000-000000000026',
     'Consentimiento Informado Retroactivo — Caso-011',
     'consentimiento_informado', 'pendiente_firma',
     'd1000000-0000-0000-0000-000000000005', 'c0000000-0000-0000-0000-000000000011'),

    ('e0000000-0000-0000-0000-000000000027',
     'Certificado de Procedimiento Ambulatorio — Caso-011',
     'certificado_medico', 'borrador',
     'd1000000-0000-0000-0000-000000000005', 'c0000000-0000-0000-0000-000000000011'),

    -- ── Caso-012 · Falta de seguimiento (clasificada) ────────────────────────
    ('e0000000-0000-0000-0000-000000000028',
     'Receta y Plan de Seguimiento — Caso-012',
     'receta', 'borrador',
     'd1000000-0000-0000-0000-000000000005', 'c0000000-0000-0000-0000-000000000012'),

    -- ── Caso-013 · Demora en resultados de laboratorio (en_revision) ──────────
    ('e0000000-0000-0000-0000-000000000029',
     'Orden de Laboratorio Preoperatorio — Caso-013',
     'orden_laboratorio', 'firmado',
     'd0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000013'),

    ('e0000000-0000-0000-0000-000000000030',
     'Informe Médico — Demora Preoperatoria Caso-013',
     'informe_medico', 'firmado',
     'd0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000013'),

    -- ── Caso-014 · Desistimiento (cerrada) ───────────────────────────────────
    ('e0000000-0000-0000-0000-000000000031',
     'Historia Clínica — Caso-014',
     'historia_clinica', 'archivado',
     'd0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000014'),

    ('e0000000-0000-0000-0000-000000000032',
     'Carta de Desistimiento — Caso-014',
     'documento_legal', 'archivado',
     'd0000000-0000-0000-0000-000000000002', 'c0000000-0000-0000-0000-000000000014');


-- ── 2. Versiones de documento (1 versión por documento) ─────────────────────

INSERT INTO document_versions (id, document_id, version, content, notes, created_by) VALUES

    -- e001: Historia Clínica Caso-001
    ('f0000000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000001', 1,
     'HISTORIA CLÍNICA — CASO REFERENCIAL 001 (SIMULADO)

Paciente: Caso-001 | Edad referencial: 54 años | Área: Cirugía General
Fecha del evento: 02/04/2026

ANTECEDENTES RELEVANTES
- Colecistectomía laparoscópica programada, sin antecedentes de complicaciones previas.
- Sin alergias conocidas a medicamentos.

EVOLUCIÓN CLÍNICA
Paciente intervenido de forma electiva. A los 5 días del alta se presentó a control con signos de infección del sitio quirúrgico (eritema, secreción serosa). Se inició tratamiento antibiótico y seguimiento ambulatorio. Resolución completa a los 14 días.

OBSERVACIONES
Documentación completa archivada. Consentimiento informado firmado previo al procedimiento.

*Documento generado con fines de demostración. No contiene datos reales de pacientes (Ley 29733).*',
     'Versión inicial revisada y firmada.', 'd0000000-0000-0000-0000-000000000001'),

    -- e002: Consentimiento Informado Caso-001
    ('f0000000-0000-0000-0000-000000000002', 'e0000000-0000-0000-0000-000000000002', 1,
     'CONSENTIMIENTO INFORMADO — CASO REFERENCIAL 001 (SIMULADO)

Procedimiento: Colecistectomía Laparoscópica
Médico responsable: Dr. Carlos Mendoza Aguilar | CMP 045678
Institución: Clínica SANNA El Golf

El paciente referencial ha sido informado de:
- Naturaleza del procedimiento y alternativas disponibles.
- Riesgos asociados: infección, sangrado, lesión de estructuras adyacentes.
- Beneficios esperados y pronóstico.
- Derecho a retirar el consentimiento en cualquier momento.

Estado: FIRMADO por el paciente y el médico responsable.

*Documento generado con fines de demostración. No contiene datos reales de pacientes (Ley 29733).*',
     NULL, 'd0000000-0000-0000-0000-000000000001'),

    -- e003: Informe Médico Caso-001
    ('f0000000-0000-0000-0000-000000000003', 'e0000000-0000-0000-0000-000000000003', 1,
     'INFORME MÉDICO — COMPLICACIÓN POST-QUIRÚRGICA (SIMULADO)

Caso referencial: 001 | Fecha: 07/04/2026
Médico: Dr. Carlos Mendoza Aguilar | CMP 045678

DESCRIPCIÓN DE LA COMPLICACIÓN
Se detectó infección superficial del sitio quirúrgico a los 5 días post-colecistectomía. Clasificación CDC: infección incisional superficial.

MANEJO REALIZADO
- Cultivo de la secreción: flora polimicrobiana.
- Antibioticoterapia: amoxicilina-clavulánico 875/125 mg c/12h por 7 días.
- Curaciones diarias ambulatorias.
- Resolución completa confirmada a los 14 días.

CONCLUSIÓN
La complicación fue manejada de forma oportuna y con resultado favorable. No hubo secuelas permanentes.

*Documento generado con fines de demostración. No contiene datos reales de pacientes (Ley 29733).*',
     'Informe firmado digitalmente.', 'd0000000-0000-0000-0000-000000000001'),

    -- e004: Descargo Legal Caso-001 (Lucía)
    ('f0000000-0000-0000-0000-000000000004', 'e0000000-0000-0000-0000-000000000004', 1,
     'DESCARGO LEGAL — RESPUESTA PRELIMINAR (SIMULADO)

Caso referencial: 001 | Abogada: Dra. Lucía Fernández Torres | CAB 34521
Fecha de emisión: 15/04/2026

ANÁLISIS PRELIMINAR
Revisada la documentación clínica del Caso-001, se concluye que:
1. El procedimiento contó con consentimiento informado debidamente firmado.
2. La complicación infecciosa se encuentra dentro del rango esperado para la intervención realizada (tasa publicada: 1-3%).
3. El manejo post-operatorio fue oportuno y siguió los protocolos vigentes.

RECOMENDACIÓN LEGAL
Se recomienda responder al reclamo del paciente con la documentación clínica completa y evidenciar el seguimiento realizado. Riesgo de acción legal formal: BAJO.

*Documento generado con fines de demostración (Ley 29733).*',
     'Respuesta preliminar aprobada.', 'd0000000-0000-0000-0000-000000000002'),

    -- e005: Historia Clínica Caso-002
    ('f0000000-0000-0000-0000-000000000005', 'e0000000-0000-0000-0000-000000000005', 1,
     'HISTORIA CLÍNICA — CASO REFERENCIAL 002 (SIMULADO)

Paciente: Caso-002 | Edad referencial: 47 años | Área: Cirugía General
Fecha del evento: 18/02/2026 — Estado: ARCHIVADO

MOTIVO DE CONSULTA
Dolor abdominal de 3 semanas de evolución, de carácter intermitente, localizado en fosa ilíaca derecha.

PROCESO DIAGNÓSTICO
Semana 1: Descartada apendicitis aguda por clínica y laboratorio.
Semana 2: Ecografía abdominal sin hallazgos concluyentes.
Semana 3: TAC abdominal confirmó quiste ovárico derecho torsionado (diagnóstico definitivo).

RESOLUCIÓN
Intervención quirúrgica exitosa. Paciente dada de alta sin complicaciones.

*Documento archivado — caso cerrado sin acción legal. Ley 29733.*',
     'Archivado tras cierre del caso.', 'd0000000-0000-0000-0000-000000000001'),

    -- e006: Informe Médico Caso-002
    ('f0000000-0000-0000-0000-000000000006', 'e0000000-0000-0000-0000-000000000006', 1,
     'INFORME MÉDICO — EVALUACIÓN DIAGNÓSTICA (SIMULADO)

Caso referencial: 002 | Fecha: 04/03/2026 | Estado: ARCHIVADO

JUSTIFICACIÓN DE LA DEMORA DIAGNÓSTICA
La presentación clínica inicial no era concluyente para un diagnóstico específico. El proceso diagnóstico siguió el protocolo estándar de dolor abdominal agudo, con estudios escalonados y apropiados.

CRONOLOGÍA DOCUMENTADA
- Día 1: Evaluación inicial, laboratorio y descarte de emergencia quirúrgica.
- Día 7: Control ambulatorio, persistencia de síntomas. Se solicita ecografía.
- Día 14: Resultado ecográfico no concluyente. Se solicita TAC.
- Día 15: TAC muestra quiste ovárico torsionado. Ingreso para cirugía.

CONCLUSIÓN
La demora de 2 semanas es atribuible a la presentación atípica del cuadro clínico, no a negligencia médica.

*Archivado. Ley 29733.*',
     NULL, 'd0000000-0000-0000-0000-000000000001'),

    -- e007: Resolución Legal Caso-002
    ('f0000000-0000-0000-0000-000000000007', 'e0000000-0000-0000-0000-000000000007', 1,
     'RESOLUCIÓN LEGAL — CASO-002 CERRADO (SIMULADO)

Caso referencial: 002 | Abogada: Dra. Lucía Fernández Torres | CAB 34521
Fecha: 20/03/2026 | Estado: ARCHIVADO

RESOLUCIÓN FINAL
Tras la revisión exhaustiva del caso y la comunicación con el paciente referencial, se determina:

- El proceso diagnóstico fue correcto y debidamente documentado.
- La paciente aceptó la explicación clínica proporcionada.
- No se iniciará acción legal ni arbitral.

El caso se cierra sin responsabilidad médica atribuible.

*Caso cerrado. Archivado. Ley 29733.*',
     'Archivado tras desistimiento.', 'd0000000-0000-0000-0000-000000000002'),

    -- e008: Consentimiento Informado Caso-003
    ('f0000000-0000-0000-0000-000000000008', 'e0000000-0000-0000-0000-000000000008', 1,
     'CONSENTIMIENTO INFORMADO — PROCEDIMIENTO ESTÉTICO (SIMULADO)

Caso referencial: 003 | Procedimiento: Cirugía correctiva de cicatriz
Médico: Dr. Carlos Mendoza Aguilar | CMP 045678

INFORMACIÓN AL PACIENTE
Se ha explicado al paciente referencial:
- Que los resultados estéticos de las cicatrices son variables y dependen de factores individuales (tipo de piel, cicatrización individual, etc.).
- Que el resultado estético no es predecible con exactitud.
- Opciones de manejo: observación, tratamiento tópico, revisión quirúrgica.

ESTADO: PENDIENTE DE FIRMA del paciente.

*Documento en proceso. Ley 29733.*',
     'En espera de firma del paciente referencial.', 'd0000000-0000-0000-0000-000000000001'),

    -- e009: Historia Clínica Caso-003 (borrador)
    ('f0000000-0000-0000-0000-000000000009', 'e0000000-0000-0000-0000-000000000009', 1,
     'HISTORIA CLÍNICA — CASO REFERENCIAL 003 (BORRADOR) (SIMULADO)

Paciente: Caso-003 | Edad referencial: 36 años | Área: Cirugía General

[BORRADOR — EN PROCESO DE COMPLETAR]

Fecha del evento: 10/05/2026
Procedimiento: Cirugía electiva abdominal
Complicación referida: Disconformidad estética con cicatriz resultante.

Antecedentes: ...
Descripción del procedimiento: ...
Evolución post-operatoria: Sin complicaciones clínicas registradas.

*Pendiente de completar y revisar. Ley 29733.*',
     'Borrador inicial — pendiente de completar.', 'd0000000-0000-0000-0000-000000000001'),

    -- e010: Historia Clínica Caso-004 (borrador)
    ('f0000000-0000-0000-0000-000000000010', 'e0000000-0000-0000-0000-000000000010', 1,
     'HISTORIA CLÍNICA — CASO REFERENCIAL 004 (BORRADOR) (SIMULADO)

Paciente: Caso-004 | Edad referencial: 29 años | Área: Cirugía General
Motivo: Ingreso por emergencia abdominal — familiar reporta tiempo de espera.

[BORRADOR — CLASIFICADO, PENDIENTE DE ABOGADO]

Descripción del ingreso: ...
Tiempos documentados: ...
Personal de turno: ...

*En proceso. Ley 29733.*',
     NULL, 'd0000000-0000-0000-0000-000000000001'),

    -- e011: Informe de Emergencia Caso-004 (borrador)
    ('f0000000-0000-0000-0000-000000000011', 'e0000000-0000-0000-0000-000000000011', 1,
     'INFORME DE EMERGENCIA — CASO REFERENCIAL 004 (BORRADOR) (SIMULADO)

[BORRADOR]

Caso: 004 | Fecha: 22/05/2026 | Médico: Dr. Carlos Mendoza Aguilar

Descripción de la atención de emergencia:
- Hora de llegada del paciente: ...
- Hora de primera evaluación médica: ...
- Diagnóstico inicial: ...
- Tiempo hasta intervención: ...

Justificación de tiempos: ...

*Pendiente de completar. Ley 29733.*',
     'Borrador inicial.', 'd0000000-0000-0000-0000-000000000001'),

    -- e012: Consentimiento Informado Caso-005 (Valentina)
    ('f0000000-0000-0000-0000-000000000012', 'e0000000-0000-0000-0000-000000000012', 1,
     'CONSENTIMIENTO INFORMADO — ATENCIÓN DEL PARTO (SIMULADO)

Caso referencial: 005 | Procedimiento: Parto institucional
Médica: Dra. Valentina Rojas Quispe | CMP 051234
Institución: Clínica SANNA El Golf

INFORMACIÓN PROPORCIONADA
- Proceso natural del trabajo de parto y posibles intervenciones necesarias.
- Riesgos inherentes al parto: hemorragia, desgarros, complicaciones neonatales.
- Posibilidad de cesárea si las condiciones lo requieren.
- Posibilidad de transfusión sanguínea en caso de hemorragia mayor.

Estado: FIRMADO.

*Documento generado con fines de demostración. Ley 29733.*',
     NULL, 'd1000000-0000-0000-0000-000000000001'),

    -- e013: Informe Obstétrico Caso-005 (Valentina)
    ('f0000000-0000-0000-0000-000000000013', 'e0000000-0000-0000-0000-000000000013', 1,
     'INFORME OBSTÉTRICO — HEMORRAGIA POST-PARTO (SIMULADO)

Caso referencial: 005 | Fecha: 01/06/2026
Médica: Dra. Valentina Rojas Quispe | CMP 051234

DESCRIPCIÓN DEL EVENTO
Paciente referencial primigesta con parto vaginal. A los 20 minutos post-alumbramiento se evidenció hemorragia uterina mayor (estimado: 1200 ml). Se activó protocolo de hemorragia obstétrica.

MANEJO REALIZADO
1. Oxitocina y misoprostol IV.
2. Masaje uterino continuo.
3. Transfusión de 2 unidades de sangre total.
4. Traslado a UCI por 24 horas para monitoreo.
5. Alta médica al día 4 post-parto en buen estado general.

CONCLUSIÓN
La hemorragia fue manejada con el protocolo institucional vigente. La paciente recuperó satisfactoriamente.

*Documento generado con fines de demostración. Ley 29733.*',
     'Firmado tras revisión.', 'd1000000-0000-0000-0000-000000000001'),

    -- e014: Evaluación Legal Caso-005 (Lucía, borrador)
    ('f0000000-0000-0000-0000-000000000014', 'e0000000-0000-0000-0000-000000000014', 1,
     'EVALUACIÓN LEGAL PRELIMINAR — CASO-005 (BORRADOR) (SIMULADO)

Caso referencial: 005 | Abogada: Dra. Lucía Fernández Torres | CAB 34521

[BORRADOR — EN REVISIÓN]

ANÁLISIS INICIAL
La hemorragia post-parto es una complicación obstétrica conocida con incidencia del 5-10% de los partos. La clave legal es demostrar que:
1. Existía consentimiento informado que mencionaba este riesgo.
2. El protocolo de manejo fue activado oportunamente.
3. El resultado final fue favorable.

DOCUMENTACIÓN PENDIENTE DE REVISAR
- [ ] Historia clínica del parto
- [ ] Registros de UCI
- [ ] Protocolo de hemorragia institucional

*Borrador. Ley 29733.*',
     'Borrador inicial — pendiente de completar análisis.', 'd0000000-0000-0000-0000-000000000002'),

    -- e015: Consentimiento Informado Caso-006 (Sebastián)
    ('f0000000-0000-0000-0000-000000000015', 'e0000000-0000-0000-0000-000000000015', 1,
     'CONSENTIMIENTO INFORMADO — ANESTESIA GENERAL (SIMULADO)

Caso referencial: 006 | Procedimiento: Anestesia general para cirugía electiva
Médico: Dr. Sebastián Paredes Luna | CMP 048765
Institución: Clínica Internacional

INFORMACIÓN AL PACIENTE
- Técnica anestésica propuesta y alternativas.
- Riesgos: náuseas, vómitos, dolor de garganta, reacciones alérgicas (poco frecuentes).
- Protocolo de manejo de reacciones adversas disponible en el quirófano.
- Sin antecedentes alérgicos conocidos reportados por el paciente.

Estado: FIRMADO.

*Documento generado con fines de demostración. Ley 29733.*',
     NULL, 'd1000000-0000-0000-0000-000000000002'),

    -- e016: Informe de Incidente Caso-006 (Sebastián)
    ('f0000000-0000-0000-0000-000000000016', 'e0000000-0000-0000-0000-000000000016', 1,
     'INFORME DE INCIDENTE — REACCIÓN ALÉRGICA A ANESTESIA (SIMULADO)

Caso referencial: 006 | Fecha: 15/05/2026
Médico: Dr. Sebastián Paredes Luna | CMP 048765

DESCRIPCIÓN DEL INCIDENTE
A los 5 minutos de inducción anestésica con propofol, el paciente referencial presentó eritema generalizado y broncoespasmo leve. Se suspendió el agente y se activó protocolo de reacción alérgica.

MANEJO INMEDIATO
1. Suspensión de agente desencadenante.
2. Adrenalina 0.3 mg IM.
3. Hidrocortisona 200 mg IV.
4. Monitoreo continuo durante 4 horas.
5. Recuperación completa. Procedimiento reprogramado.

CLASIFICACIÓN: Reacción de hipersensibilidad grado II (moderada).

RECOMENDACIÓN
Pruebas alérgicas pre-anestesia para intervenciones futuras. Registro en historia clínica.

*Documento generado con fines de demostración. Ley 29733.*',
     'Firmado y enviado a comité de calidad.', 'd1000000-0000-0000-0000-000000000002'),

    -- e017: Análisis Legal Caso-006 (Mateo, borrador)
    ('f0000000-0000-0000-0000-000000000017', 'e0000000-0000-0000-0000-000000000017', 1,
     'ANÁLISIS LEGAL — CASO-006 (BORRADOR) (SIMULADO)

Caso referencial: 006 | Abogado: Abg. Mateo Huamán Ríos | CAB 29456

[BORRADOR — EN PROCESO DE REVISIÓN MÉDICA]

POSICIÓN LEGAL PRELIMINAR
El médico obtuvo consentimiento informado que mencionaba el riesgo de reacciones alérgicas. La reacción fue manejada de forma inmediata y correcta. El paciente no sufrió secuelas.

PUNTOS CLAVE A CONSOLIDAR
- Verificar que el consentimiento mencione explícitamente reacciones alérgicas.
- Obtener copia del protocolo institucional de reacciones adversas.
- Confirmar que no existían alergias previas documentadas.

*Borrador. Pendiente de revisión médica. Ley 29733.*',
     'En revisión conjunta médico-abogado.', 'd2000000-0000-0000-0000-000000000003'),

    -- e018: Informe Obstétrico Caso-007 (Valentina)
    ('f0000000-0000-0000-0000-000000000018', 'e0000000-0000-0000-0000-000000000018', 1,
     'INFORME OBSTÉTRICO — PARTO DISTÓCICO (SIMULADO)

Caso referencial: 007 | Fecha: 20/03/2026
Médica: Dra. Valentina Rojas Quispe | CMP 051234

DESCRIPCIÓN DEL EVENTO
Paciente referencial primigesta con trabajo de parto espontáneo que evolucionó a detención del descenso en período expulsivo. Se realizó extracción instrumental (vacuum) y nació recién nacido vigoroso, Apgar 8/9.

DECISIONES CLÍNICAS DOCUMENTADAS
- La indicación de vacuum fue correcta según partograma y criterios ACOG.
- Tiempo de extracción: 8 minutos.
- No se registraron complicaciones maternas ni neonatales inmediatas.

CONCLUSIÓN
El manejo del parto distócico siguió los protocolos vigentes. El cuestionamiento familiar es comprensible pero no evidencia mala práctica.

*Firmado. Ley 29733.*',
     'Firmado.', 'd1000000-0000-0000-0000-000000000001'),

    -- e019: Respuesta Legal Caso-007 (Daniela)
    ('f0000000-0000-0000-0000-000000000019', 'e0000000-0000-0000-0000-000000000019', 1,
     'RESPUESTA LEGAL — CASO-007 (SIMULADO)

Caso referencial: 007 | Abogada: Abg. Daniela Vargas Solís | CAB 33678
Fecha: 10/04/2026

RESPUESTA A LA FAMILIA
Estimada familia,

Tras la revisión exhaustiva del caso y la documentación clínica, podemos informarles que:

1. El parto fue atendido siguiendo los protocolos obstétricos actuales.
2. La decisión de uso de vacuum fue clínicamente justificada y documentada.
3. Tanto la madre como el recién nacido tuvieron una evolución favorable.

Se adjunta el informe médico firmado para su revisión. Quedamos a disposición para cualquier consulta adicional.

Con respeto,
Abg. Daniela Vargas Solís

*Firmado. Ley 29733.*',
     'Respuesta enviada a la familia.', 'd2000000-0000-0000-0000-000000000002'),

    -- e020: Historia Clínica Corregida Caso-008 (Camila)
    ('f0000000-0000-0000-0000-000000000020', 'e0000000-0000-0000-0000-000000000020', 1,
     'HISTORIA CLÍNICA CORREGIDA — CASO REFERENCIAL 008 (SIMULADO)

Paciente: Caso-008 | Edad referencial: 4 años | Área: Pediatría
Estado: PENDIENTE DE FIRMA | Fecha de corrección: 05/05/2026

CORRECCIÓN DOCUMENTAL
Se identificó error en el registro de dosis de amoxicilina: el registro original indicaba 500 mg cada 8 horas, siendo la dosis correcta para el peso del paciente 250 mg cada 8 horas.

ACLARACIÓN
El error fue documental; la dosis administrada al paciente fue la correcta según la prescripción verbal verificada con enfermería. Se corrige el registro escrito para reflejar la dosis real.

VALIDACIÓN MÉDICA PENDIENTE
Requiere firma de la médica responsable y revisión del servicio de calidad.

*Pendiente de firma. Ley 29733.*',
     'Corrección pendiente de firma.', 'd1000000-0000-0000-0000-000000000003'),

    -- e021: Informe de Corrección Caso-008 (Camila, borrador)
    ('f0000000-0000-0000-0000-000000000021', 'e0000000-0000-0000-0000-000000000021', 1,
     'INFORME DE CORRECCIÓN DOCUMENTAL — CASO-008 (BORRADOR) (SIMULADO)

[BORRADOR]

Médica: Dra. Camila Torres Vidal | CMP 052341
Servicio: Pediatría | Fecha: 06/05/2026

DESCRIPCIÓN DEL ERROR
Durante auditoría interna se detectó discrepancia entre la dosis prescrita en la evolución médica (500 mg) y la dosis clínicamente correcta para el peso del paciente pediátrico (250 mg).

IMPACTO CLÍNICO
El error fue exclusivamente documental. Verificado con enfermería: la dosis administrada fue la clínicamente correcta.

MEDIDAS CORRECTIVAS
1. Corrección del registro con nota aclaratoria firmada.
2. Capacitación al equipo sobre registro adecuado de dosis pediátricas.
3. Implementación de doble verificación para prescripciones pediátricas.

*Borrador en revisión. Ley 29733.*',
     'En revisión con comité de calidad.', 'd1000000-0000-0000-0000-000000000003'),

    -- e022: Historia Clínica Caso-009 (Andrés)
    ('f0000000-0000-0000-0000-000000000022', 'e0000000-0000-0000-0000-000000000022', 1,
     'HISTORIA CLÍNICA — CASO REFERENCIAL 009 (ARCHIVADO) (SIMULADO)

Paciente: Caso-009 | Edad referencial: 58 años | Área: Traumatología
Fecha del evento: 10/12/2025 | Estado: ARCHIVADO

DIAGNÓSTICO
Fractura de radio distal derecho tratada con inmovilización enyesada durante 6 semanas.

EVOLUCIÓN
Control radiológico al mes: consolidación en ligera angulación. Control a los 3 meses: consolidación completa con leve limitación funcional de extensión de muñeca.

CONCLUSIÓN
La consolidación con ligera angulación es un resultado posible del tratamiento conservador, informado al paciente durante el consentimiento inicial.

*Archivado. Caso cerrado. Ley 29733.*',
     'Archivado tras cierre del caso.', 'd1000000-0000-0000-0000-000000000004'),

    -- e023: Informe Radiológico Caso-009 (Andrés)
    ('f0000000-0000-0000-0000-000000000023', 'e0000000-0000-0000-0000-000000000023', 1,
     'INFORME RADIOLÓGICO — CONSOLIDACIÓN ÓSEA (ARCHIVADO) (SIMULADO)

Caso referencial: 009 | Médico: Dr. Andrés Salazar Cruz | CMP 039876
Fecha: 15/03/2026 | Estado: ARCHIVADO

HALLAZGOS RADIOLÓGICOS
RX de muñeca derecha AP y lateral (control 3 meses):
- Fractura de radio distal consolidada.
- Angulación volar residual de 8°.
- Sin signos de pseudoartrosis.
- Amplitud articular radiocarpiana conservada.

CONCLUSIÓN RADIOLÓGICA
Consolidación en posición aceptable para tratamiento conservador. El grado de angulación está dentro del rango reportado en la literatura para este tipo de fractura tratada sin cirugía.

*Archivado. Ley 29733.*',
     NULL, 'd1000000-0000-0000-0000-000000000004'),

    -- e024: Acuerdo Legal Caso-009 (Joaquín)
    ('f0000000-0000-0000-0000-000000000024', 'e0000000-0000-0000-0000-000000000024', 1,
     'ACUERDO LEGAL — CIERRE CASO-009 (ARCHIVADO) (SIMULADO)

Caso referencial: 009 | Abogado: Abg. Joaquín Espinoza Ruiz | CAB 31245
Fecha: 01/04/2026 | Estado: ARCHIVADO

TÉRMINOS DEL ACUERDO
Tras la revisión del caso y reunión con el paciente referencial, se acordó:

1. El médico se compromete a brindar seguimiento clínico fisioterapéutico adicional por 3 meses.
2. El paciente acepta que la consolidación obtenida es un resultado clínicamente aceptable.
3. Ninguna de las partes iniciará acciones legales relacionadas con este caso.

CIERRE DEFINITIVO
El caso se da por cerrado. Documentación archivada.

*Archivado. Ley 29733.*',
     'Acuerdo firmado por ambas partes. Archivado.', 'd2000000-0000-0000-0000-000000000001'),

    -- e025: Historia Clínica Caso-010 (Andrés, borrador)
    ('f0000000-0000-0000-0000-000000000025', 'e0000000-0000-0000-0000-000000000025', 1,
     'HISTORIA CLÍNICA — INGRESO POR EMERGENCIA (BORRADOR) (SIMULADO)

[BORRADOR]

Caso referencial: 010 | Edad referencial: 39 años | Área: Traumatología
Fecha del evento: 12/06/2026

MOTIVO DE INGRESO
Politraumatismo por accidente de tránsito. Familiar reporta tiempo de espera de 45 minutos previo a atención médica.

PENDIENTE DE DOCUMENTAR
- Registro de tiempos de atención (hora de llegada, triage, primera evaluación).
- Diagnóstico definitivo de lesiones.
- Plan de manejo.

*Borrador en proceso. Ley 29733.*',
     'Borrador inicial — urgente completar registro de tiempos.', 'd1000000-0000-0000-0000-000000000004'),

    -- e026: Consentimiento Informado Retroactivo Caso-011 (Fiorella)
    ('f0000000-0000-0000-0000-000000000026', 'e0000000-0000-0000-0000-000000000026', 1,
     'CONSENTIMIENTO INFORMADO RETROACTIVO — PROCEDIMIENTO AMBULATORIO (SIMULADO)

Caso referencial: 011 | Procedimiento: Procedimiento ambulatorio menor
Médica: Dra. Fiorella Campos Díaz | CMP 061122
Institución: Clínica Internacional

DECLARACIÓN
La médica tratante declara que el procedimiento fue explicado verbalmente al paciente referencial antes de su realización, incluyendo los riesgos, beneficios y alternativas. Por error administrativo, el formulario escrito no fue suscrito antes del procedimiento.

Se emite este documento retroactivo como regularización del expediente, sin que esto implique que el paciente no fue informado previamente.

Estado: PENDIENTE DE FIRMA del paciente referencial.

*En proceso de regularización. Ley 29733.*',
     'Pendiente de firma para regularización.', 'd1000000-0000-0000-0000-000000000005'),

    -- e027: Certificado de Procedimiento Caso-011 (Fiorella, borrador)
    ('f0000000-0000-0000-0000-000000000027', 'e0000000-0000-0000-0000-000000000027', 1,
     'CERTIFICADO DE PROCEDIMIENTO AMBULATORIO (BORRADOR) (SIMULADO)

[BORRADOR]

Caso referencial: 011 | Médica: Dra. Fiorella Campos Díaz | CMP 061122

CERTIFICO QUE:
El paciente referencial fue sometido a [PROCEDIMIENTO PENDIENTE DE DETALLAR] el día [FECHA] en las instalaciones de Clínica Internacional.

El procedimiento transcurrió sin complicaciones y el paciente fue dado de alta en buen estado general.

*Borrador. Pendiente de completar. Ley 29733.*',
     'Borrador — pendiente de completar detalles del procedimiento.', 'd1000000-0000-0000-0000-000000000005'),

    -- e028: Receta / Plan de Seguimiento Caso-012 (Fiorella, borrador)
    ('f0000000-0000-0000-0000-000000000028', 'e0000000-0000-0000-0000-000000000028', 1,
     'RECETA Y PLAN DE SEGUIMIENTO — CASO-012 (BORRADOR) (SIMULADO)

[BORRADOR]

Caso referencial: 012 | Médica: Dra. Fiorella Campos Díaz | CMP 061122
Fecha: 30/05/2026

PRESCRIPCIÓN (SIMULADA)
- Medicación: [pendiente]
- Indicaciones generales: dieta balanceada, control de presión arterial.

PLAN DE SEGUIMIENTO
- Control en 30 días.
- Solicitar exámenes de laboratorio de control.
- Contactar al paciente si no se presenta a cita.

*Borrador. Pendiente de firma. Ley 29733.*',
     'Borrador de receta y plan.', 'd1000000-0000-0000-0000-000000000005'),

    -- e029: Orden de Laboratorio Caso-013 (Carlos)
    ('f0000000-0000-0000-0000-000000000029', 'e0000000-0000-0000-0000-000000000029', 1,
     'ORDEN DE LABORATORIO PREOPERATORIO (SIMULADO)

Caso referencial: 013 | Médico: Dr. Carlos Mendoza Aguilar | CMP 045678
Fecha de solicitud: 15/04/2026 | Estado: FIRMADO

EXÁMENES SOLICITADOS
- Hemograma completo
- Grupo sanguíneo y factor Rh
- Glucosa, urea, creatinina
- TP, TPA, INR
- EKG preoperatorio
- Rx de tórax PA

NOTA
Resultados requeridos con urgencia para programar cirugía electiva. La entrega demoró 10 días desde la solicitud, lo que postergó el procedimiento.

*Firmado. Ley 29733.*',
     'Firmado. Entrega demorada por laboratorio externo.', 'd0000000-0000-0000-0000-000000000001'),

    -- e030: Informe Médico Caso-013 (Carlos)
    ('f0000000-0000-0000-0000-000000000030', 'e0000000-0000-0000-0000-000000000030', 1,
     'INFORME MÉDICO — DEMORA PREOPERATORIA (SIMULADO)

Caso referencial: 013 | Médico: Dr. Carlos Mendoza Aguilar | CMP 045678
Fecha: 28/04/2026 | Estado: FIRMADO

DESCRIPCIÓN DE LA SITUACIÓN
La cirugía electiva del paciente referencial fue postergada 10 días a causa de la demora en la entrega de resultados de laboratorio preoperatorio por parte del laboratorio externo contratado.

IMPACTO CLÍNICO
Sin complicaciones clínicas derivadas de la espera. El procedimiento fue realizado exitosamente una vez obtenidos los resultados.

RESPONSABILIDAD
La demora es atribuible al laboratorio externo, no al equipo médico tratante. Se adjunta comunicación con el laboratorio solicitando explicación por la demora.

*Firmado. En revisión legal. Ley 29733.*',
     'Firmado. Enviado a revisión con abogado.', 'd0000000-0000-0000-0000-000000000001'),

    -- e031: Historia Clínica Caso-014 (Carlos)
    ('f0000000-0000-0000-0000-000000000031', 'e0000000-0000-0000-0000-000000000031', 1,
     'HISTORIA CLÍNICA — CASO REFERENCIAL 014 (ARCHIVADO) (SIMULADO)

Paciente: Caso-014 | Edad referencial: 50 años | Área: Cirugía General
Fecha: 08/01/2026 | Estado: ARCHIVADO

RESUMEN DEL CASO
Paciente referencial inició un proceso de reclamo por disconformidad con un procedimiento electivo. Tras reunión de mediación y revisión de documentación, el paciente decidió desistir formalmente de continuar el proceso.

Sin secuelas clínicas reportadas. Documentación completa archivada.

*Archivado. Desistimiento. Ley 29733.*',
     'Archivado por desistimiento del paciente.', 'd0000000-0000-0000-0000-000000000001'),

    -- e032: Carta de Desistimiento Caso-014 (Lucía)
    ('f0000000-0000-0000-0000-000000000032', 'e0000000-0000-0000-0000-000000000032', 1,
     'CARTA DE DESISTIMIENTO — CASO-014 (ARCHIVADO) (SIMULADO)

Caso referencial: 014 | Abogada: Dra. Lucía Fernández Torres | CAB 34521
Fecha: 20/02/2026 | Estado: ARCHIVADO

DECLARACIÓN DE DESISTIMIENTO
El paciente referencial del Caso-014 ha comunicado formalmente su decisión de desistir de todo reclamo, queja o acción legal vinculada al procedimiento médico recibido.

Motivación declarada: satisfacción con la explicación médica recibida en reunión de mediación y con el seguimiento clínico brindado.

El presente documento deja constancia del desistimiento expreso e irrevocable de cualquier acción futura derivada de los mismos hechos.

*Archivado. Caso cerrado definitivamente. Ley 29733.*',
     'Desistimiento formal archivado.', 'd0000000-0000-0000-0000-000000000002');


-- ── 3. Apuntar current_version_id en cada documento ─────────────────────────

UPDATE documents SET current_version_id = 'f0000000-0000-0000-0000-000000000001' WHERE id = 'e0000000-0000-0000-0000-000000000001';
UPDATE documents SET current_version_id = 'f0000000-0000-0000-0000-000000000002' WHERE id = 'e0000000-0000-0000-0000-000000000002';
UPDATE documents SET current_version_id = 'f0000000-0000-0000-0000-000000000003' WHERE id = 'e0000000-0000-0000-0000-000000000003';
UPDATE documents SET current_version_id = 'f0000000-0000-0000-0000-000000000004' WHERE id = 'e0000000-0000-0000-0000-000000000004';
UPDATE documents SET current_version_id = 'f0000000-0000-0000-0000-000000000005' WHERE id = 'e0000000-0000-0000-0000-000000000005';
UPDATE documents SET current_version_id = 'f0000000-0000-0000-0000-000000000006' WHERE id = 'e0000000-0000-0000-0000-000000000006';
UPDATE documents SET current_version_id = 'f0000000-0000-0000-0000-000000000007' WHERE id = 'e0000000-0000-0000-0000-000000000007';
UPDATE documents SET current_version_id = 'f0000000-0000-0000-0000-000000000008' WHERE id = 'e0000000-0000-0000-0000-000000000008';
UPDATE documents SET current_version_id = 'f0000000-0000-0000-0000-000000000009' WHERE id = 'e0000000-0000-0000-0000-000000000009';
UPDATE documents SET current_version_id = 'f0000000-0000-0000-0000-000000000010' WHERE id = 'e0000000-0000-0000-0000-000000000010';
UPDATE documents SET current_version_id = 'f0000000-0000-0000-0000-000000000011' WHERE id = 'e0000000-0000-0000-0000-000000000011';
UPDATE documents SET current_version_id = 'f0000000-0000-0000-0000-000000000012' WHERE id = 'e0000000-0000-0000-0000-000000000012';
UPDATE documents SET current_version_id = 'f0000000-0000-0000-0000-000000000013' WHERE id = 'e0000000-0000-0000-0000-000000000013';
UPDATE documents SET current_version_id = 'f0000000-0000-0000-0000-000000000014' WHERE id = 'e0000000-0000-0000-0000-000000000014';
UPDATE documents SET current_version_id = 'f0000000-0000-0000-0000-000000000015' WHERE id = 'e0000000-0000-0000-0000-000000000015';
UPDATE documents SET current_version_id = 'f0000000-0000-0000-0000-000000000016' WHERE id = 'e0000000-0000-0000-0000-000000000016';
UPDATE documents SET current_version_id = 'f0000000-0000-0000-0000-000000000017' WHERE id = 'e0000000-0000-0000-0000-000000000017';
UPDATE documents SET current_version_id = 'f0000000-0000-0000-0000-000000000018' WHERE id = 'e0000000-0000-0000-0000-000000000018';
UPDATE documents SET current_version_id = 'f0000000-0000-0000-0000-000000000019' WHERE id = 'e0000000-0000-0000-0000-000000000019';
UPDATE documents SET current_version_id = 'f0000000-0000-0000-0000-000000000020' WHERE id = 'e0000000-0000-0000-0000-000000000020';
UPDATE documents SET current_version_id = 'f0000000-0000-0000-0000-000000000021' WHERE id = 'e0000000-0000-0000-0000-000000000021';
UPDATE documents SET current_version_id = 'f0000000-0000-0000-0000-000000000022' WHERE id = 'e0000000-0000-0000-0000-000000000022';
UPDATE documents SET current_version_id = 'f0000000-0000-0000-0000-000000000023' WHERE id = 'e0000000-0000-0000-0000-000000000023';
UPDATE documents SET current_version_id = 'f0000000-0000-0000-0000-000000000024' WHERE id = 'e0000000-0000-0000-0000-000000000024';
UPDATE documents SET current_version_id = 'f0000000-0000-0000-0000-000000000025' WHERE id = 'e0000000-0000-0000-0000-000000000025';
UPDATE documents SET current_version_id = 'f0000000-0000-0000-0000-000000000026' WHERE id = 'e0000000-0000-0000-0000-000000000026';
UPDATE documents SET current_version_id = 'f0000000-0000-0000-0000-000000000027' WHERE id = 'e0000000-0000-0000-0000-000000000027';
UPDATE documents SET current_version_id = 'f0000000-0000-0000-0000-000000000028' WHERE id = 'e0000000-0000-0000-0000-000000000028';
UPDATE documents SET current_version_id = 'f0000000-0000-0000-0000-000000000029' WHERE id = 'e0000000-0000-0000-0000-000000000029';
UPDATE documents SET current_version_id = 'f0000000-0000-0000-0000-000000000030' WHERE id = 'e0000000-0000-0000-0000-000000000030';
UPDATE documents SET current_version_id = 'f0000000-0000-0000-0000-000000000031' WHERE id = 'e0000000-0000-0000-0000-000000000031';
UPDATE documents SET current_version_id = 'f0000000-0000-0000-0000-000000000032' WHERE id = 'e0000000-0000-0000-0000-000000000032';
