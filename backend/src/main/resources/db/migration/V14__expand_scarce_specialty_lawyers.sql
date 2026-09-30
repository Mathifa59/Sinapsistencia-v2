-- ============================================================================
-- Sinapsistencia -- V14: refuerzo de produccion para las 8 especialidades
-- "Escasa" del corpus DS-03 (Dermatologia, Endocrinologia, Oftalmologia,
-- Neumologia, Nefrologia, Reumatologia, Infectologia, Hematologia).
--
-- NO toca ningun perfil existente (V3/V4/V5/V12): solo INSERT. La escasez de
-- esas 8 areas en el corpus DS-03 es deliberada (ver
-- ml-service/evaluation/build_corpus.py TARGET_COVERAGE, comentario
-- "Escasa (adversos, a proposito)") y alimenta las consultas de especialidad
-- escasa q16-q20 de la evaluacion DS-04. Esta migracion es una decision de
-- producto POSTERIOR a esa evaluacion, no una correccion del corpus: sube
-- cada una de esas 8 areas de 2 a 4 abogados (nivel "Media" original) para
-- que los medicos tengan opciones razonables en la app en vivo.
--
-- Por que esto no invalida DS-03/DS-04: build_test_collection.py,
-- run_ablation.py y build_lawyer_panel.py leen exclusivamente el snapshot
-- congelado ml-service/data/reference/ds03_lawyers.json -- ninguno consulta
-- lawyer_profiles en vivo. Detalle completo, verificado linea por linea:
-- docs/datasheet-corpus-ds03.md §5.
--
-- is_active = TRUE, available = TRUE: candidatos plenos del matching en vivo.
-- No autenticables: password_hash bcrypt de una contraseña aleatoria de un
-- solo uso, generada y descartada (mismo mecanismo que V12, nunca impresa).
-- ============================================================================

INSERT INTO profiles (id, email, name, role, is_active, password_hash) VALUES
    ('b6000000-0000-0000-0000-000000000001', 'veronica.salinas@sinapsistencia.pe', 'Abg. Verónica Salinas Quiroga', 'lawyer', TRUE, '$2b$12$z8/6BrEoy3hGkvSvBl4KTeEt6Q8t31zqLlkMZrdEbEAHaQIBPurEG');
INSERT INTO lawyer_profiles (user_id, cab, specialties, medical_areas, years_experience, rating, resolved_cases, available, phone, bio) VALUES
    ('b6000000-0000-0000-0000-000000000001', '44012', ARRAY['Seguros Médicos', 'Consentimiento Informado'], ARRAY['Dermatología', 'Neumología'],
     9, 4.35, 19, TRUE, '+51 961 401 501', 'Divide su práctica entre procedimientos dermatológicos estéticos y reclamos respiratorios post-anestesia, dos áreas que rara vez comparten abogado porque parecen no tener nada en común. Sostiene que sí lo tienen: en ambas, el expediente se gana o se pierde en el formulario de consentimiento, no en la técnica del procedimiento. Trabaja de cerca con dos aseguradoras que le derivan casos de manera regular.');

INSERT INTO profiles (id, email, name, role, is_active, password_hash) VALUES
    ('b6000000-0000-0000-0000-000000000002', 'tomas.rivas@sinapsistencia.pe', 'Abg. Tomás Rivas Cabrera', 'lawyer', TRUE, '$2b$12$O4brwGRuEkyUrVh9wJgZ8ejn5ebRjIinT.kGYIY7uzbKUdrM9v4cy');
INSERT INTO lawyer_profiles (user_id, cab, specialties, medical_areas, years_experience, rating, resolved_cases, available, phone, bio) VALUES
    ('b6000000-0000-0000-0000-000000000002', '44107', ARRAY['Negligencia Médica', 'Responsabilidad Civil Médica'], ARRAY['Neumología', 'Endocrinología'],
     13, 4.50, 27, TRUE, '+51 962 402 502', 'Sus casos casi siempre cruzan dos especialidades que en la práctica clínica van de la mano: pacientes diabéticos con complicaciones pulmonares que nadie anticipó a tiempo. Antes de litigar trabajó como auditor de historias clínicas en una EPS, lo que le dejó un ojo entrenado para detectar cuándo una demora en el seguimiento ambulatorio explica de verdad el desenlace. Prefiere las pericias cruzadas antes que un solo informe.');

INSERT INTO profiles (id, email, name, role, is_active, password_hash) VALUES
    ('b6000000-0000-0000-0000-000000000003', 'daniela.ortiz@sinapsistencia.pe', 'Abg. Daniela Ortiz Maraví', 'lawyer', TRUE, '$2b$12$E68vb0W.nPPsOPmYySE1HePLcCeFAL3jAxcD5QGn65QqN0gkOtHd.');
INSERT INTO lawyer_profiles (user_id, cab, specialties, medical_areas, years_experience, rating, resolved_cases, available, phone, bio) VALUES
    ('b6000000-0000-0000-0000-000000000003', '44203', ARRAY['Bioética y Derecho', 'Derecho Sanitario'], ARRAY['Endocrinología', 'Hematología'],
     7, 4.20, 12, TRUE, '+51 963 403 503', 'Acompaña sobre todo casos donde un manejo hormonal prolongado terminó afectando el cuadro hematológico del paciente sin que quedara claro en el expediente quién debía haberlo anticipado. Enseña un seminario breve sobre bioética clínica en tratamientos crónicos, y suele repetir a sus alumnos que la mayoría de los reclamos que ve nacen de un cambio de tratamiento nunca explicado, no de un error de diagnóstico.');

INSERT INTO profiles (id, email, name, role, is_active, password_hash) VALUES
    ('b6000000-0000-0000-0000-000000000004', 'esteban.loyola@sinapsistencia.pe', 'Abg. Esteban Loyola Guzmán', 'lawyer', TRUE, '$2b$12$TXkOKXLaySTUDMnWu3I23e2TAARewy05Ne/C9BscENpLEgk.6tlSu');
INSERT INTO lawyer_profiles (user_id, cab, specialties, medical_areas, years_experience, rating, resolved_cases, available, phone, bio) VALUES
    ('b6000000-0000-0000-0000-000000000004', '44308', ARRAY['Consentimiento Informado', 'Seguros Médicos'], ARRAY['Hematología', 'Oftalmología'],
     11, 4.45, 23, TRUE, '+51 964 404 504', 'Un caso temprano sobre pérdida de visión tras un trastorno de coagulación mal manejado definió el resto de su carrera: ahora acepta casi exclusivamente expedientes donde un problema hematológico de base complica un procedimiento oftalmológico programado. Las aseguradoras lo llaman para dictaminar si el consentimiento firmado cubría realmente ese riesgo específico o solo el procedimiento en términos generales.');

INSERT INTO profiles (id, email, name, role, is_active, password_hash) VALUES
    ('b6000000-0000-0000-0000-000000000005', 'cecilia.bravo@sinapsistencia.pe', 'Abg. Cecilia Bravo Huerta', 'lawyer', TRUE, '$2b$12$RHjDPT1oCa9HAMv7rk7CdunmgmxZe3s8kNhYVgO0Y/0kjM8j3qwGC');
INSERT INTO lawyer_profiles (user_id, cab, specialties, medical_areas, years_experience, rating, resolved_cases, available, phone, bio) VALUES
    ('b6000000-0000-0000-0000-000000000005', '44415', ARRAY['Derecho Médico', 'Bioética y Derecho'], ARRAY['Oftalmología', 'Nefrología'],
     6, 4.15, 9, TRUE, '+51 965 405 505', 'Construyó su cartera entre dos consultorios que comparten poco entre sí salvo un mismo patrón: pacientes con enfermedad renal crónica que además requieren seguimiento oftalmológico por la misma condición de base, y a quienes nadie coordinó bien entre especialistas. Da charlas sobre consentimiento informado en enfermedades crónicas multisistémicas en un colegio médico regional.');

INSERT INTO profiles (id, email, name, role, is_active, password_hash) VALUES
    ('b6000000-0000-0000-0000-000000000006', 'ramiro.chuquipoma@sinapsistencia.pe', 'Abg. Ramiro Chuquipoma Díaz', 'lawyer', TRUE, '$2b$12$oUi1xgGGzxz3H0pwcDRjqup.bnKKEZ0dAfqR3cRe/7sDhDAQVjG7S');
INSERT INTO lawyer_profiles (user_id, cab, specialties, medical_areas, years_experience, rating, resolved_cases, available, phone, bio) VALUES
    ('b6000000-0000-0000-0000-000000000006', '44502', ARRAY['Negligencia Médica', 'Derecho Penal Médico'], ARRAY['Nefrología', 'Infectología'],
     15, 4.60, 34, TRUE, '+51 966 406 506', 'Litiga casi exclusivamente infecciones asociadas a catéteres de diálisis que terminaron en sepsis, un tipo de caso técnico que exige entender tanto protocolo de bioseguridad como derecho penal sanitario. Fue perito de parte en dos procesos que sentaron precedente local sobre responsabilidad por infecciones intrahospitalarias en unidades de hemodiálisis. Acepta pocos casos nuevos por año, pero los sigue personalmente hasta el cierre.');

INSERT INTO profiles (id, email, name, role, is_active, password_hash) VALUES
    ('b6000000-0000-0000-0000-000000000007', 'fabiola.anaya@sinapsistencia.pe', 'Abg. Fabiola Anaya Cueva', 'lawyer', TRUE, '$2b$12$3JCp/mf.NeXo4B7jYcKF4OoizRPr2Jr3ZyV9p5r8dd4BeuthCIExC');
INSERT INTO lawyer_profiles (user_id, cab, specialties, medical_areas, years_experience, rating, resolved_cases, available, phone, bio) VALUES
    ('b6000000-0000-0000-0000-000000000007', '44609', ARRAY['Derecho Sanitario', 'Responsabilidad Civil Médica'], ARRAY['Infectología', 'Reumatología'],
     8, 4.30, 16, TRUE, '+51 967 407 507', 'Se especializó casi por accidente: un caso de artritis reactiva mal diagnosticada como infección viral común la llevó a entender que reumatología e infectología comparten más zonas grises de las que los propios médicos reconocen. Colabora con un reumatólogo externo en cada expediente para trazar la línea exacta entre evolución natural de la enfermedad y demora diagnóstica evitable.');

INSERT INTO profiles (id, email, name, role, is_active, password_hash) VALUES
    ('b6000000-0000-0000-0000-000000000008', 'gustavo.peralta@sinapsistencia.pe', 'Abg. Gustavo Peralta Ibarra', 'lawyer', TRUE, '$2b$12$XywiQ94SEZwrz3GutypUquZT81KwoOQ7tA3OPqUhDfH8vrMYd0iAm');
INSERT INTO lawyer_profiles (user_id, cab, specialties, medical_areas, years_experience, rating, resolved_cases, available, phone, bio) VALUES
    ('b6000000-0000-0000-0000-000000000008', '44711', ARRAY['Seguros Médicos', 'Bioética y Derecho'], ARRAY['Reumatología', 'Dermatología'],
     10, 4.40, 21, TRUE, '+51 968 408 508', 'Atiende sobre todo reacciones cutáneas severas a tratamientos biológicos para enfermedades reumatológicas, un cruce que pocas aseguradoras saben tasar correctamente porque el manual de pólizas rara vez contempla ambas condiciones a la vez. Antes de especializarse en esta intersección trabajaba en seguros generales, experiencia que ahora usa para negociar coberturas antes de que el caso llegue a litigio.');
