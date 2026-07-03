-- ============================================================================
-- Sinapsistencia — V8: Solicitudes de contacto de demostración
--
-- Crea contact_requests coherentes con los casos del seed (V3/V4):
--   - aceptado  → casos con lawyer_id asignado (ya vinculados)
--   - pendiente → casos sin abogado asignado aún
--   - rechazado → muestra el ciclo completo de la plataforma
-- Cubre la bandeja de la cuenta demo de abogado (Lucía) y el resto de abogados.
-- ============================================================================

INSERT INTO contact_requests
    (id, from_doctor_id, to_lawyer_id, case_id, case_title, message, status, ml_score, response_message)
VALUES

    -- ═══════════════════════════════════════════════════════════════════════
    -- ACEPTADAS — alineadas con el lawyer_id de cada caso
    -- ═══════════════════════════════════════════════════════════════════════

    -- c001 · Carlos → Lucía (respondida / alta)
    ('g0000000-0000-0000-0000-000000000001',
     'd0000000-0000-0000-0000-000000000001',
     'd0000000-0000-0000-0000-000000000002',
     'c0000000-0000-0000-0000-000000000001',
     'Posible reclamo por complicación post-quirúrgica',
     'Estimada Dra. Fernández, me gustaría contar con su asesoría para el Caso-001. El paciente presentó infección post-quirúrgica y existe riesgo de reclamo formal. Le adjuntaré la documentación completa.',
     'aceptado', 92.00,
     'Estimado Dr. Mendoza, acepto gustosamente asesorarle en este caso. He revisado la documentación inicial y el riesgo legal es manejable dado que el consentimiento informado está completo. Coordinamos reunión esta semana.'),

    -- c002 · Carlos → Lucía (cerrada)
    ('g0000000-0000-0000-0000-000000000002',
     'd0000000-0000-0000-0000-000000000001',
     'd0000000-0000-0000-0000-000000000002',
     'c0000000-0000-0000-0000-000000000002',
     'Consulta por presunta negligencia en diagnóstico tardío',
     'Dra. Fernández, el paciente del Caso-002 cuestiona la demora de 2 semanas en su diagnóstico. Necesito orientación legal para documentar adecuadamente el proceso diagnóstico seguido.',
     'aceptado', 88.00,
     'Dr. Mendoza, revisaré el expediente. La presentación atípica del cuadro clínico es un argumento sólido. Le prepararé un descargo con la cronología documentada.'),

    -- c005 · Valentina → Lucía (asignada / crítica)
    ('g0000000-0000-0000-0000-000000000003',
     'd1000000-0000-0000-0000-000000000001',
     'd0000000-0000-0000-0000-000000000002',
     'c0000000-0000-0000-0000-000000000005',
     'Solicitud de segunda opinión legal por hemorragia post-parto',
     'Dra. Fernández, la familia del Caso-005 solicita explicación formal sobre la hemorragia post-parto que requirió transfusión. Es un caso crítico y necesito asesoría urgente.',
     'aceptado', 95.00,
     'Dra. Rojas, entiendo la urgencia. La hemorragia post-parto es una complicación obstétrica reconocida. Con el consentimiento informado que menciona este riesgo y el protocolo activado, tenemos una posición sólida. Iniciamos revisión inmediata.'),

    -- c006 · Sebastián → Mateo (en_revision / alta)
    ('g0000000-0000-0000-0000-000000000004',
     'd1000000-0000-0000-0000-000000000002',
     'd2000000-0000-0000-0000-000000000003',
     'c0000000-0000-0000-0000-000000000006',
     'Consulta por reacción alérgica a anestesia',
     'Abg. Huamán, el paciente del Caso-006 tuvo una reacción alérgica moderada durante anestesia. El manejo fue correcto pero la familia está nerviosa. Necesito su apoyo legal.',
     'aceptado', 90.00,
     'Dr. Paredes, he revisado el informe de incidente. El protocolo fue activado correctamente y el paciente se recuperó sin secuelas. Prepararé el análisis legal esta semana.'),

    -- c007 · Valentina → Daniela (respondida / alta)
    ('g0000000-0000-0000-0000-000000000005',
     'd1000000-0000-0000-0000-000000000001',
     'd2000000-0000-0000-0000-000000000002',
     'c0000000-0000-0000-0000-000000000007',
     'Caso por presunta mala práctica en parto',
     'Abg. Vargas, la familia del Caso-007 cuestiona el manejo de un parto distócico con vacuum. Todo fue documentado y el resultado fue favorable, pero necesito respaldo legal.',
     'aceptado', 85.00,
     'Dra. Rojas, revisé el caso. La indicación de vacuum estaba justificada según el partograma. Ya he redactado la respuesta para la familia. Se la envío para su revisión.'),

    -- c008 · Camila → Daniela (en_revision / media)
    ('g0000000-0000-0000-0000-000000000006',
     'd1000000-0000-0000-0000-000000000003',
     'd2000000-0000-0000-0000-000000000002',
     'c0000000-0000-0000-0000-000000000008',
     'Consulta por error en historia clínica pediátrica',
     'Abg. Vargas, se detectó un error documental en la historia clínica pediátrica del Caso-008. No hubo daño clínico, pero quiero asesoría para manejar la corrección correctamente.',
     'aceptado', 78.00,
     'Dra. Torres, entiendo la situación. Un error documental sin daño clínico es manejable. Proceda con la corrección formal con nota aclaratoria. Le prepararé el informe de corrección.'),

    -- c009 · Andrés → Joaquín (cerrada)
    ('g0000000-0000-0000-0000-000000000007',
     'd1000000-0000-0000-0000-000000000004',
     'd2000000-0000-0000-0000-000000000001',
     'c0000000-0000-0000-0000-000000000009',
     'Reclamo por fractura mal consolidada',
     'Abg. Espinoza, el paciente del Caso-009 reclama por consolidación inadecuada de fractura. El tratamiento conservador fue el adecuado para su tipo de fractura, pero necesito documentar bien la posición.',
     'aceptado', 82.00,
     'Dr. Salazar, revisé el caso y los controles radiológicos. La angulación residual está dentro del rango aceptable para tratamiento conservador. Propongo una reunión de mediación con el paciente para cerrar el caso sin litigio.'),

    -- c011 · Fiorella → Patricia (asignada / baja)
    ('g0000000-0000-0000-0000-000000000008',
     'd1000000-0000-0000-0000-000000000005',
     'd2000000-0000-0000-0000-000000000004',
     'c0000000-0000-0000-0000-000000000011',
     'Solicitud de orientación legal por consentimiento incompleto',
     'Abg. Núñez, en el Caso-011 el procedimiento ambulatorio se realizó sin la firma previa del consentimiento. El paciente está conforme, pero quiero regularizar el expediente correctamente.',
     'aceptado', 70.00,
     'Dra. Campos, entiendo. Le recomiendo obtener el consentimiento retroactivo firmado y adjuntar una nota aclaratoria en el expediente. El riesgo es bajo dado que el paciente no reporta quejas.'),

    -- c013 · Carlos → Joaquín (en_revision / media)
    ('g0000000-0000-0000-0000-000000000009',
     'd0000000-0000-0000-0000-000000000001',
     'd2000000-0000-0000-0000-000000000001',
     'c0000000-0000-0000-0000-000000000013',
     'Consulta por demora en resultados de laboratorio',
     'Abg. Espinoza, el paciente del Caso-013 reclama por la demora de 10 días en resultados de laboratorio que postergó su cirugía. La demora fue del laboratorio externo, no nuestra.',
     'aceptado', 75.00,
     'Dr. Mendoza, revisé la documentación. La demora es claramente atribuible al laboratorio externo. Le recomiendo obtener comunicación escrita del laboratorio reconociendo la demora, lo cual reforzará nuestra posición.'),

    -- c014 · Carlos → Lucía (cerrada)
    ('g0000000-0000-0000-0000-000000000010',
     'd0000000-0000-0000-0000-000000000001',
     'd0000000-0000-0000-0000-000000000002',
     'c0000000-0000-0000-0000-000000000014',
     'Caso archivado por desistimiento del paciente',
     'Dra. Fernández, el paciente del Caso-014 inició un reclamo que luego decidió retirar. Necesito que formalice el desistimiento para cerrar el caso definitivamente.',
     'aceptado', 80.00,
     'Dr. Mendoza, perfecto. Prepararé la carta de desistimiento formal para que el paciente la firme. Una vez firmada, el caso queda cerrado definitivamente sin posibilidad de reapertura.'),

    -- ═══════════════════════════════════════════════════════════════════════
    -- PENDIENTES — casos sin abogado asignado aún
    -- ═══════════════════════════════════════════════════════════════════════

    -- c003 · Carlos → Paola Ramírez (pendiente cicatriz)
    ('g0000000-0000-0000-0000-000000000011',
     'd0000000-0000-0000-0000-000000000001',
     'd3000000-0000-0000-0000-000000000002',
     'c0000000-0000-0000-0000-000000000003',
     'Reclamo de paciente por cicatriz post-operatoria',
     'Abg. Ramírez, el paciente del Caso-003 expresa disconformidad estética con la cicatriz. El procedimiento fue electivo y el resultado clínico fue exitoso. ¿Podría asesorarme?',
     'pendiente', 65.00, NULL),

    -- c004 · Carlos → Diego Huamán (pendiente tiempo de espera)
    ('g0000000-0000-0000-0000-000000000012',
     'd0000000-0000-0000-0000-000000000001',
     'd3000000-0000-0000-0000-000000000005',
     'c0000000-0000-0000-0000-000000000004',
     'Queja por tiempo de espera en cirugía de emergencia',
     'Abg. Huamán, un familiar reclama por el tiempo de espera previo a una cirugía de emergencia (Caso-004). Tenemos los registros de triage. Solicito su asesoría para documentar la respuesta.',
     'pendiente', 72.00, NULL),

    -- c010 · Andrés → Renato Salazar (pendiente negligencia urgencias)
    ('g0000000-0000-0000-0000-000000000013',
     'd1000000-0000-0000-0000-000000000004',
     'd3000000-0000-0000-0000-000000000003',
     'c0000000-0000-0000-0000-000000000010',
     'Consulta por presunta negligencia en atención de urgencias',
     'Abg. Salazar, el familiar del Caso-010 reporta demora en la atención inicial de urgencias. El paciente ingresó por politraumatismo. Necesito asesoría para documentar los tiempos de atención.',
     'pendiente', 68.00, NULL),

    -- c012 · Fiorella → Mario Castillo (pendiente falta seguimiento)
    ('g0000000-0000-0000-0000-000000000014',
     'd1000000-0000-0000-0000-000000000005',
     'd3000000-0000-0000-0000-000000000001',
     'c0000000-0000-0000-0000-000000000012',
     'Caso por presunta falta de seguimiento post-consulta',
     'Abg. Castillo, el paciente del Caso-012 refiere no haber recibido la cita de seguimiento acordada. Es un caso de baja complejidad pero quiero documentar bien la respuesta.',
     'pendiente', 60.00, NULL),

    -- ═══════════════════════════════════════════════════════════════════════
    -- RECHAZADAS — muestra el ciclo completo
    -- ═══════════════════════════════════════════════════════════════════════

    -- Carlos → Jorge Paredes para c004 (rechazó, luego Carlos buscó a otro)
    ('g0000000-0000-0000-0000-000000000015',
     'd0000000-0000-0000-0000-000000000001',
     'd3000000-0000-0000-0000-000000000007',
     'c0000000-0000-0000-0000-000000000004',
     'Queja por tiempo de espera en cirugía de emergencia',
     'Abg. Paredes, quisiera su asesoría para el Caso-004 relacionado con una queja por tiempo de espera en emergencias.',
     'rechazado', 55.00,
     'Dr. Mendoza, lamentablemente en este momento no puedo asumir más casos de urgencias ya que mi carga actual es muy alta. Le recomiendo contactar a un colega que se especialice en este tipo de reclamos.'),

    -- Valentina → Carmen Vega para c005 (rechazó antes de Lucía aceptar)
    ('g0000000-0000-0000-0000-000000000016',
     'd1000000-0000-0000-0000-000000000001',
     'd3000000-0000-0000-0000-000000000004',
     'c0000000-0000-0000-0000-000000000005',
     'Solicitud de segunda opinión legal por hemorragia post-parto',
     'Abg. Vega, necesito asesoría urgente por una complicación obstétrica del Caso-005. ¿Podría revisarlo?',
     'rechazado', 48.00,
     'Dra. Rojas, mi especialización actual es principalmente oncología y hematología. Para casos obstétricos le recomiendo buscar un abogado con experiencia específica en ginecología y obstetricia.');
