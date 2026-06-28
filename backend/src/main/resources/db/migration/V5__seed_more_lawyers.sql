-- ============================================================================
-- Sinapsistencia — V5: Más abogados (cobertura amplia de especialidades)
--
-- Estos 7 abogados ya existían como entradas ficticias en
-- ml-service/app/matching/lawyers_corpus.json (con IDs que no resolvían a
-- ningún registro real), pensados para dar cobertura de matching a
-- especialidades médicas que los abogados demo/V4 no cubrían. Se crean aquí
-- como perfiles reales para que el matching TF-IDF + coseno los pueda
-- resolver contra la base de datos. Password (por si se quiere usar): Demo123!
-- ============================================================================

INSERT INTO profiles (id, email, name, role, is_active, password_hash) VALUES
    ('d3000000-0000-0000-0000-000000000001', 'mario.castillo@sinapsistencia.pe', 'Abg. Mario Castillo Bravo',   'lawyer', TRUE, '$2a$10$D73C6Mg5hUJfTDrpeO8SZud6UgjVvD1qemlW1wSQdJiXhK62vEimi'),
    ('d3000000-0000-0000-0000-000000000002', 'paola.ramirez@sinapsistencia.pe',  'Abg. Paola Ramírez Soto',     'lawyer', TRUE, '$2a$10$D73C6Mg5hUJfTDrpeO8SZud6UgjVvD1qemlW1wSQdJiXhK62vEimi'),
    ('d3000000-0000-0000-0000-000000000003', 'renato.salazar@sinapsistencia.pe', 'Abg. Renato Salazar Méndez',  'lawyer', TRUE, '$2a$10$D73C6Mg5hUJfTDrpeO8SZud6UgjVvD1qemlW1wSQdJiXhK62vEimi'),
    ('d3000000-0000-0000-0000-000000000004', 'carmen.vega@sinapsistencia.pe',    'Abg. Carmen Vega Ibáñez',     'lawyer', TRUE, '$2a$10$D73C6Mg5hUJfTDrpeO8SZud6UgjVvD1qemlW1wSQdJiXhK62vEimi'),
    ('d3000000-0000-0000-0000-000000000005', 'diego.huaman@sinapsistencia.pe',   'Abg. Diego Huamán Vera',      'lawyer', TRUE, '$2a$10$D73C6Mg5hUJfTDrpeO8SZud6UgjVvD1qemlW1wSQdJiXhK62vEimi'),
    ('d3000000-0000-0000-0000-000000000006', 'ines.quispe@sinapsistencia.pe',    'Abg. Inés Quispe Loayza',     'lawyer', TRUE, '$2a$10$D73C6Mg5hUJfTDrpeO8SZud6UgjVvD1qemlW1wSQdJiXhK62vEimi'),
    ('d3000000-0000-0000-0000-000000000007', 'jorge.paredes@sinapsistencia.pe',  'Abg. Jorge Paredes Flores',   'lawyer', TRUE, '$2a$10$D73C6Mg5hUJfTDrpeO8SZud6UgjVvD1qemlW1wSQdJiXhK62vEimi');

INSERT INTO lawyer_profiles (id, user_id, cab, specialties, medical_areas, years_experience,
                             rating, resolved_cases, available, phone, bio) VALUES
    ('b3000000-0000-0000-0000-000000000001', 'd3000000-0000-0000-0000-000000000001',
     '21567', ARRAY['Negligencia Médica', 'Derecho Penal Médico', 'Derecho Médico'],
     ARRAY['Cardiología', 'Medicina General', 'Neumología'], 12, 4.60, 34, TRUE, '+51 995 555 666',
     'Especialista en negligencia médica y derecho penal sanitario, con experiencia en casos de cardiología y medicina interna.'),
    ('b3000000-0000-0000-0000-000000000002', 'd3000000-0000-0000-0000-000000000002',
     '24890', ARRAY['Consentimiento Informado', 'Bioética y Derecho', 'Derecho Sanitario'],
     ARRAY['Ginecología y Obstetricia', 'Pediatría', 'Endocrinología'], 9, 4.70, 21, TRUE, '+51 996 666 777',
     'Asesora en consentimiento informado y bioética, enfocada en obstetricia, pediatría y casos endocrinológicos.'),
    ('b3000000-0000-0000-0000-000000000003', 'd3000000-0000-0000-0000-000000000003',
     '27345', ARRAY['Seguros Médicos', 'Responsabilidad Civil Médica', 'Derecho Médico'],
     ARRAY['Traumatología', 'Cirugía General', 'Anestesiología'], 18, 4.90, 63, TRUE, '+51 997 777 888',
     'Experto en seguros médicos y responsabilidad civil en traumatología, cirugía y anestesiología, con amplia trayectoria en litigios complejos.'),
    ('b3000000-0000-0000-0000-000000000004', 'd3000000-0000-0000-0000-000000000004',
     '19234', ARRAY['Derecho Médico', 'Negligencia Médica', 'Bioética y Derecho'],
     ARRAY['Oncología', 'Hematología', 'Medicina General'], 10, 4.50, 28, TRUE, '+51 998 888 999',
     'Acompaña a oncólogos y hematólogos en la documentación clínica y defensa frente a reclamos por negligencia médica.'),
    ('b3000000-0000-0000-0000-000000000005', 'd3000000-0000-0000-0000-000000000005',
     '30678', ARRAY['Derecho Penal Médico', 'Responsabilidad Civil Profesional', 'Derecho Sanitario'],
     ARRAY['Neurología', 'Psiquiatría', 'Medicina General'], 14, 4.65, 39, TRUE, '+51 999 999 000',
     'Litigante en derecho penal médico con foco en neurología y psiquiatría, especializado en evaluación de riesgo legal.'),
    ('b3000000-0000-0000-0000-000000000006', 'd3000000-0000-0000-0000-000000000006',
     '15432', ARRAY['Derecho Sanitario', 'Consentimiento Informado', 'Derecho Médico'],
     ARRAY['Dermatología', 'Oftalmología', 'Urología'], 7, 4.40, 16, TRUE, '+51 911 222 333',
     'Asesora legal para procedimientos ambulatorios y consentimiento informado en dermatología, oftalmología y urología.'),
    ('b3000000-0000-0000-0000-000000000007', 'd3000000-0000-0000-0000-000000000007',
     '26781', ARRAY['Responsabilidad Civil Médica', 'Seguros Médicos', 'Negligencia Médica'],
     ARRAY['Gastroenterología', 'Nefrología', 'Infectología'], 16, 4.75, 51, TRUE, '+51 912 333 444',
     'Especialista en responsabilidad civil para especialidades de medicina interna: gastroenterología, nefrología e infectología.');
