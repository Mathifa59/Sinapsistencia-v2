-- ============================================================================
-- Sinapsistencia — V4: Más datos de demostración
--
-- Amplía el seed inicial (V3) con más médicos, abogados y consultas para que
-- los portales no se vean vacíos. Mismas reglas que V3: contexto SIMULADO
-- (Ley 29733, is_simulated=true), sin DNI/nombre real de paciente.
-- Password de los nuevos médicos/abogados (por si se quiere usar): Demo123!
-- ============================================================================

-- ── Más médicos ─────────────────────────────────────────────────────────────
INSERT INTO profiles (id, email, name, role, is_active, password_hash) VALUES
    ('d1000000-0000-0000-0000-000000000001', 'valentina.rojas@sinapsistencia.pe', 'Dra. Valentina Rojas Quispe',  'doctor', TRUE, '$2a$10$D73C6Mg5hUJfTDrpeO8SZud6UgjVvD1qemlW1wSQdJiXhK62vEimi'),
    ('d1000000-0000-0000-0000-000000000002', 'sebastian.paredes@sinapsistencia.pe', 'Dr. Sebastián Paredes Luna',  'doctor', TRUE, '$2a$10$D73C6Mg5hUJfTDrpeO8SZud6UgjVvD1qemlW1wSQdJiXhK62vEimi'),
    ('d1000000-0000-0000-0000-000000000003', 'camila.torres@sinapsistencia.pe',   'Dra. Camila Torres Vidal',    'doctor', TRUE, '$2a$10$D73C6Mg5hUJfTDrpeO8SZud6UgjVvD1qemlW1wSQdJiXhK62vEimi'),
    ('d1000000-0000-0000-0000-000000000004', 'andres.salazar@sinapsistencia.pe',  'Dr. Andrés Salazar Cruz',     'doctor', TRUE, '$2a$10$D73C6Mg5hUJfTDrpeO8SZud6UgjVvD1qemlW1wSQdJiXhK62vEimi'),
    ('d1000000-0000-0000-0000-000000000005', 'fiorella.campos@sinapsistencia.pe', 'Dra. Fiorella Campos Díaz',   'doctor', TRUE, '$2a$10$D73C6Mg5hUJfTDrpeO8SZud6UgjVvD1qemlW1wSQdJiXhK62vEimi');

INSERT INTO doctor_profiles (id, user_id, cmp, specialty, sub_specialties, hospital, hospital_id,
                             years_experience, languages, phone, bio) VALUES
    ('b1000000-0000-0000-0000-000000000001', 'd1000000-0000-0000-0000-000000000001',
     '051234', 'Ginecología y Obstetricia', ARRAY['Obstetricia de Alto Riesgo'],
     'Clínica SANNA El Golf', 'a0000000-0000-0000-0000-000000000001',
     8, ARRAY['Español'], '+51 945 123 456',
     'Ginecobstetra con 8 años de experiencia en partos de alto riesgo en SANNA El Golf.'),
    ('b1000000-0000-0000-0000-000000000002', 'd1000000-0000-0000-0000-000000000002',
     '048765', 'Anestesiología', ARRAY['Anestesia Regional'],
     'Clínica Internacional', 'a0000000-0000-0000-0000-000000000002',
     15, ARRAY['Español', 'Inglés'], '+51 956 234 567',
     'Anestesiólogo con 15 años de experiencia en procedimientos quirúrgicos de mediana y alta complejidad.'),
    ('b1000000-0000-0000-0000-000000000003', 'd1000000-0000-0000-0000-000000000003',
     '052341', 'Pediatría', ARRAY['Neonatología'],
     'Hospital Nacional Edgardo Rebagliati Martins', 'a0000000-0000-0000-0000-000000000003',
     6, ARRAY['Español'], '+51 967 345 678',
     'Pediatra con 6 años de experiencia en cuidado neonatal y consulta ambulatoria pediátrica.'),
    ('b1000000-0000-0000-0000-000000000004', 'd1000000-0000-0000-0000-000000000004',
     '039876', 'Traumatología', ARRAY['Cirugía de Columna'],
     'Clínica SANNA El Golf', 'a0000000-0000-0000-0000-000000000001',
     20, ARRAY['Español'], '+51 978 456 789',
     'Traumatólogo con 20 años de experiencia en cirugía ortopédica y manejo de fracturas complejas.'),
    ('b1000000-0000-0000-0000-000000000005', 'd1000000-0000-0000-0000-000000000005',
     '061122', 'Medicina General', ARRAY['Medicina Familiar'],
     'Clínica Internacional', 'a0000000-0000-0000-0000-000000000002',
     4, ARRAY['Español'], '+51 989 567 890',
     'Médica general con 4 años de experiencia en atención primaria y medicina familiar.');

-- ── Más abogados ─────────────────────────────────────────────────────────────
INSERT INTO profiles (id, email, name, role, is_active, password_hash) VALUES
    ('d2000000-0000-0000-0000-000000000001', 'joaquin.espinoza@sinapsistencia.pe', 'Abg. Joaquín Espinoza Ruiz',   'lawyer', TRUE, '$2a$10$D73C6Mg5hUJfTDrpeO8SZud6UgjVvD1qemlW1wSQdJiXhK62vEimi'),
    ('d2000000-0000-0000-0000-000000000002', 'daniela.vargas@sinapsistencia.pe',   'Abg. Daniela Vargas Solís',    'lawyer', TRUE, '$2a$10$D73C6Mg5hUJfTDrpeO8SZud6UgjVvD1qemlW1wSQdJiXhK62vEimi'),
    ('d2000000-0000-0000-0000-000000000003', 'mateo.huaman@sinapsistencia.pe',     'Abg. Mateo Huamán Ríos',       'lawyer', TRUE, '$2a$10$D73C6Mg5hUJfTDrpeO8SZud6UgjVvD1qemlW1wSQdJiXhK62vEimi'),
    ('d2000000-0000-0000-0000-000000000004', 'patricia.nunez@sinapsistencia.pe',   'Abg. Patricia Núñez Flores',   'lawyer', TRUE, '$2a$10$D73C6Mg5hUJfTDrpeO8SZud6UgjVvD1qemlW1wSQdJiXhK62vEimi');

INSERT INTO lawyer_profiles (id, user_id, cab, specialties, medical_areas, years_experience,
                             rating, resolved_cases, available, phone, bio) VALUES
    ('b2000000-0000-0000-0000-000000000001', 'd2000000-0000-0000-0000-000000000001',
     '31245', ARRAY['Responsabilidad Civil Médica', 'Negligencia Médica'],
     ARRAY['Cirugía General', 'Traumatología'], 10, 4.60, 32, TRUE, '+51 991 111 222',
     'Abogado especialista en responsabilidad civil médica y casos de negligencia quirúrgica.'),
    ('b2000000-0000-0000-0000-000000000002', 'd2000000-0000-0000-0000-000000000002',
     '33678', ARRAY['Derecho Sanitario', 'Consentimiento Informado'],
     ARRAY['Ginecología y Obstetricia', 'Pediatría'], 7, 4.50, 19, TRUE, '+51 992 222 333',
     'Abogada especialista en derecho sanitario, consentimiento informado y atención materno-infantil.'),
    ('b2000000-0000-0000-0000-000000000003', 'd2000000-0000-0000-0000-000000000003',
     '29456', ARRAY['Derecho Penal Médico', 'Bioética y Derecho'],
     ARRAY['Anestesiología', 'Cirugía General'], 18, 4.90, 64, TRUE, '+51 993 333 444',
     'Abogado penalista con amplia trayectoria en bioética y derecho médico.'),
    ('b2000000-0000-0000-0000-000000000004', 'd2000000-0000-0000-0000-000000000004',
     '35890', ARRAY['Seguros Médicos', 'Derecho Médico'],
     ARRAY['Medicina General', 'Dermatología'], 5, 4.30, 11, TRUE, '+51 994 444 555',
     'Abogada especialista en seguros médicos y derecho médico general.');

-- ── Más consultas (cases) + contexto simulado ───────────────────────────────
INSERT INTO cases (id, title, description, doctor_id, lawyer_id, status, priority,
                   priority_justification, medical_specialty, event_type, perceived_urgency, notes) VALUES
    ('c0000000-0000-0000-0000-000000000001', 'Posible reclamo por complicación post-quirúrgica',
     'Paciente presentó infección de herida operatoria 5 días después de cirugía laparoscópica. Se solicita orientación sobre exposición legal.',
     'd0000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000002',
     'respondida', 'alta', 'Posible reclamo formal del paciente, documentación completa.',
     'Cirugía General', 'Complicación quirúrgica', 'alta', 'Caso ya cuenta con respuesta legal preliminar.'),

    ('c0000000-0000-0000-0000-000000000002', 'Consulta por presunta negligencia en diagnóstico tardío',
     'Paciente cuestiona demora de 2 semanas en el diagnóstico de su cuadro abdominal. Se requiere evaluación de riesgo legal.',
     'd0000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000002',
     'cerrada', 'media', 'Caso resuelto sin acción legal del paciente.',
     'Cirugía General', 'Diagnóstico tardío', 'media', 'Cerrado tras respuesta satisfactoria al paciente.'),

    ('c0000000-0000-0000-0000-000000000003', 'Reclamo de paciente por cicatriz post-operatoria',
     'Paciente manifiesta disconformidad estética con la cicatriz resultante de la cirugía. Aún sin clasificar formalmente.',
     'd0000000-0000-0000-0000-000000000001', NULL,
     'pendiente', 'baja', NULL, 'Cirugía General', 'Resultado estético', 'baja', NULL),

    ('c0000000-0000-0000-0000-000000000004', 'Queja por tiempo de espera en cirugía de emergencia',
     'Familiar del paciente reclama por el tiempo de espera previo a una cirugía de emergencia.',
     'd0000000-0000-0000-0000-000000000001', NULL,
     'clasificada', 'media', 'Clasificado como riesgo medio, pendiente de asignación de abogado.',
     'Cirugía General', 'Demora en atención', 'media', NULL),

    ('c0000000-0000-0000-0000-000000000005', 'Solicitud de segunda opinión legal por hemorragia post-parto',
     'Paciente presentó hemorragia post-parto que requirió transfusión. Familia solicita explicación formal.',
     'd1000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000002',
     'asignada', 'critica', 'Riesgo crítico por complicación obstétrica con transfusión.',
     'Ginecología y Obstetricia', 'Hemorragia post-parto', 'critica', NULL),

    ('c0000000-0000-0000-0000-000000000006', 'Consulta por reacción alérgica a anestesia',
     'Paciente sufrió reacción alérgica moderada durante procedimiento bajo anestesia general.',
     'd1000000-0000-0000-0000-000000000002', 'd2000000-0000-0000-0000-000000000003',
     'en_revision', 'alta', 'En revisión por el médico tras respuesta legal preliminar.',
     'Anestesiología', 'Reacción adversa a medicamento', 'alta', NULL),

    ('c0000000-0000-0000-0000-000000000007', 'Caso por presunta mala práctica en parto',
     'Familia del paciente cuestiona el manejo clínico durante un parto distócico.',
     'd1000000-0000-0000-0000-000000000001', 'd2000000-0000-0000-0000-000000000002',
     'respondida', 'alta', 'Respuesta legal preliminar entregada, en espera de revisión médica.',
     'Ginecología y Obstetricia', 'Complicación obstétrica', 'alta', NULL),

    ('c0000000-0000-0000-0000-000000000008', 'Consulta por error en historia clínica pediátrica',
     'Se identificó una inconsistencia en el registro de dosis de medicamento en la historia clínica de un menor.',
     'd1000000-0000-0000-0000-000000000003', 'd2000000-0000-0000-0000-000000000002',
     'en_revision', 'media', 'En revisión, riesgo medio por error documental sin daño reportado.',
     'Pediatría', 'Error de documentación', 'media', NULL),

    ('c0000000-0000-0000-0000-000000000009', 'Reclamo por fractura mal consolidada',
     'Paciente reclama por consolidación inadecuada de fractura tratada de forma conservadora.',
     'd1000000-0000-0000-0000-000000000004', 'd2000000-0000-0000-0000-000000000001',
     'cerrada', 'media', 'Cerrado tras acuerdo de seguimiento clínico adicional.',
     'Traumatología', 'Resultado de tratamiento', 'media', NULL),

    ('c0000000-0000-0000-0000-000000000010', 'Consulta por presunta negligencia en atención de urgencias',
     'Paciente politraumatizado refiere demora en la atención inicial en el área de emergencias.',
     'd1000000-0000-0000-0000-000000000004', NULL,
     'pendiente', 'alta', NULL, 'Traumatología', 'Demora en atención', 'alta', NULL),

    ('c0000000-0000-0000-0000-000000000011', 'Solicitud de orientación legal por consentimiento incompleto',
     'Se detectó que el formulario de consentimiento informado no fue firmado antes del procedimiento ambulatorio.',
     'd1000000-0000-0000-0000-000000000005', 'd2000000-0000-0000-0000-000000000004',
     'asignada', 'baja', 'Riesgo bajo, procedimiento ambulatorio sin complicaciones clínicas.',
     'Medicina General', 'Consentimiento informado incompleto', 'baja', NULL),

    ('c0000000-0000-0000-0000-000000000012', 'Caso por presunta falta de seguimiento post-consulta',
     'Paciente refiere no haber recibido el seguimiento acordado tras una consulta ambulatoria.',
     'd1000000-0000-0000-0000-000000000005', NULL,
     'clasificada', 'media', 'Clasificado como riesgo medio, pendiente de asignación.',
     'Medicina General', 'Falta de seguimiento', 'media', NULL),

    ('c0000000-0000-0000-0000-000000000013', 'Consulta por demora en resultados de laboratorio',
     'Paciente cuestiona la demora de 10 días en la entrega de resultados de laboratorio preoperatorios.',
     'd0000000-0000-0000-0000-000000000001', 'd2000000-0000-0000-0000-000000000001',
     'en_revision', 'media', 'En revisión médica tras respuesta legal preliminar.',
     'Cirugía General', 'Demora administrativa', 'media', NULL),

    ('c0000000-0000-0000-0000-000000000014', 'Caso archivado por desistimiento del paciente',
     'El paciente desistió formalmente de continuar con su reclamo inicial.',
     'd0000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000002',
     'cerrada', 'baja', 'Cerrado por desistimiento expreso del paciente.',
     'Cirugía General', 'Desistimiento', 'baja', NULL);

INSERT INTO case_context (case_id, context_code, age_reference, medical_area, event_date, summary, relevant_factors, is_simulated) VALUES
    ('c0000000-0000-0000-0000-000000000001', 'Caso-001', 54, 'Cirugía General', '2026-04-02',
     'Paciente referencial intervenido por colecistectomía laparoscópica, con infección de sitio quirúrgico detectada en control post-operatorio.',
     ARRAY['Documentación clínica completa', 'Consentimiento informado firmado'], TRUE),

    ('c0000000-0000-0000-0000-000000000002', 'Caso-002', 47, 'Cirugía General', '2026-02-18',
     'Paciente referencial con cuadro de dolor abdominal cuyo diagnóstico definitivo se confirmó tras 2 semanas de evaluaciones.',
     ARRAY['Historial de evaluaciones previas', 'Sin quejas previas registradas'], TRUE),

    ('c0000000-0000-0000-0000-000000000003', 'Caso-003', 36, 'Cirugía General', '2026-05-10',
     'Paciente referencial manifiesta disconformidad con el resultado estético de cicatriz post-quirúrgica.',
     ARRAY['Procedimiento electivo', 'Sin complicaciones clínicas'], TRUE),

    ('c0000000-0000-0000-0000-000000000004', 'Caso-004', 29, 'Cirugía General', '2026-05-22',
     'Paciente referencial ingresado por cirugía de emergencia, familiar reporta tiempo de espera prolongado.',
     ARRAY['Ingreso por emergencia', 'Alta demanda del servicio'], TRUE),

    ('c0000000-0000-0000-0000-000000000005', 'Caso-005', 31, 'Ginecología y Obstetricia', '2026-06-01',
     'Paciente referencial con hemorragia post-parto que requirió transfusión sanguínea y manejo en UCI por 24 horas.',
     ARRAY['Requirió transfusión', 'Manejo en UCI', 'Parto institucional'], TRUE),

    ('c0000000-0000-0000-0000-000000000006', 'Caso-006', 42, 'Anestesiología', '2026-05-15',
     'Paciente referencial presentó reacción alérgica moderada a agente anestésico durante procedimiento programado.',
     ARRAY['Sin antecedentes alérgicos conocidos', 'Procedimiento programado'], TRUE),

    ('c0000000-0000-0000-0000-000000000007', 'Caso-007', 27, 'Ginecología y Obstetricia', '2026-03-20',
     'Paciente referencial con parto distócico que derivó en cuestionamiento del manejo clínico por parte de la familia.',
     ARRAY['Parto distócico', 'Primigesta'], TRUE),

    ('c0000000-0000-0000-0000-000000000008', 'Caso-008', 4, 'Pediatría', '2026-04-28',
     'Paciente referencial pediátrico con inconsistencia detectada en el registro de dosis de medicamento.',
     ARRAY['Sin daño clínico reportado', 'Error documental identificado a tiempo'], TRUE),

    ('c0000000-0000-0000-0000-000000000009', 'Caso-009', 58, 'Traumatología', '2025-12-10',
     'Paciente referencial con fractura tratada de forma conservadora que presentó consolidación inadecuada.',
     ARRAY['Tratamiento conservador', 'Seguimiento radiológico irregular'], TRUE),

    ('c0000000-0000-0000-0000-000000000010', 'Caso-010', 39, 'Traumatología', '2026-06-12',
     'Paciente referencial politraumatizado por accidente de tránsito, familiar reporta demora en atención inicial.',
     ARRAY['Ingreso por accidente de tránsito', 'Alta demanda en emergencias'], TRUE),

    ('c0000000-0000-0000-0000-000000000011', 'Caso-011', 33, 'Medicina General', '2026-06-05',
     'Paciente referencial sometido a procedimiento ambulatorio menor sin firma previa del consentimiento informado.',
     ARRAY['Procedimiento ambulatorio', 'Sin complicaciones clínicas'], TRUE),

    ('c0000000-0000-0000-0000-000000000012', 'Caso-012', 45, 'Medicina General', '2026-05-30',
     'Paciente referencial refiere no haber recibido la cita de seguimiento acordada tras consulta inicial.',
     ARRAY['Consulta ambulatoria', 'Seguimiento pendiente'], TRUE),

    ('c0000000-0000-0000-0000-000000000013', 'Caso-013', 61, 'Cirugía General', '2026-04-15',
     'Paciente referencial con demora de 10 días en la entrega de resultados de laboratorio preoperatorios.',
     ARRAY['Demora administrativa', 'Procedimiento electivo postergado'], TRUE),

    ('c0000000-0000-0000-0000-000000000014', 'Caso-014', 50, 'Cirugía General', '2026-01-08',
     'Paciente referencial que inició reclamo inicial y posteriormente desistió de continuar el proceso.',
     ARRAY['Desistimiento expreso', 'Sin secuelas reportadas'], TRUE);
