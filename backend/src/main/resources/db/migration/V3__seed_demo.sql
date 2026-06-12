-- ============================================================================
-- Sinapsistencia — V3: Seed de demostración
--
-- Hospitales de catálogo + 3 usuarios demo (uno por rol) con sus perfiles.
-- Password de TODOS los usuarios demo: Demo123!
-- (hash BCrypt generado y verificado con BCryptPasswordEncoder de Spring 6)
--
-- UUIDs fijos para que el modo "login demo por rol" (FASE 3) y los tests
-- puedan referenciarlos de forma determinista.
-- ============================================================================

-- ── Hospitales ──────────────────────────────────────────────────────────────
INSERT INTO hospitals (id, name, address, city, department, phone, is_active) VALUES
    ('a0000000-0000-0000-0000-000000000001', 'Clínica SANNA El Golf', 'Av. Aurelio Miró Quesada 1030, San Isidro', 'Lima', 'Lima', '+51 1 712 3000', TRUE),
    ('a0000000-0000-0000-0000-000000000002', 'Clínica Internacional', 'Av. Garcilaso de la Vega 1420, Cercado de Lima', 'Lima', 'Lima', '+51 1 619 6161', TRUE),
    ('a0000000-0000-0000-0000-000000000003', 'Hospital Nacional Edgardo Rebagliati Martins', 'Av. Edgardo Rebagliati 490, Jesús María', 'Lima', 'Lima', '+51 1 265 4901', TRUE);

-- ── Usuarios demo (password: Demo123!) ─────────────────────────────────────
INSERT INTO profiles (id, email, name, role, is_active, password_hash) VALUES
    ('d0000000-0000-0000-0000-000000000001', 'doctor.demo@sinapsistencia.pe', 'Dr. Carlos Mendoza Aguilar',   'doctor', TRUE, '$2a$10$D73C6Mg5hUJfTDrpeO8SZud6UgjVvD1qemlW1wSQdJiXhK62vEimi'),
    ('d0000000-0000-0000-0000-000000000002', 'lawyer.demo@sinapsistencia.pe', 'Dra. Lucía Fernández Torres',  'lawyer', TRUE, '$2a$10$D73C6Mg5hUJfTDrpeO8SZud6UgjVvD1qemlW1wSQdJiXhK62vEimi'),
    ('d0000000-0000-0000-0000-000000000003', 'admin.demo@sinapsistencia.pe',  'Jorge Ramírez Castillo',       'admin',  TRUE, '$2a$10$D73C6Mg5hUJfTDrpeO8SZud6UgjVvD1qemlW1wSQdJiXhK62vEimi');

-- ── Perfil del médico demo ──────────────────────────────────────────────────
INSERT INTO doctor_profiles (id, user_id, cmp, specialty, sub_specialties, hospital, hospital_id,
                             years_experience, languages, phone, bio) VALUES
    ('b0000000-0000-0000-0000-000000000001',
     'd0000000-0000-0000-0000-000000000001',
     '045678',
     'Cirugía General',
     ARRAY['Cirugía Laparoscópica', 'Cirugía de Emergencia'],
     'Clínica SANNA El Golf',
     'a0000000-0000-0000-0000-000000000001',
     12,
     ARRAY['Español', 'Inglés'],
     '+51 987 654 321',
     'Cirujano general con 12 años de experiencia en cirugía laparoscópica y manejo de emergencias quirúrgicas en SANNA El Golf.');

-- ── Perfil del abogado demo ─────────────────────────────────────────────────
INSERT INTO lawyer_profiles (id, user_id, cab, specialties, medical_areas, years_experience,
                             rating, resolved_cases, available, phone, bio) VALUES
    ('b0000000-0000-0000-0000-000000000002',
     'd0000000-0000-0000-0000-000000000002',
     '34521',
     ARRAY['Derecho Médico', 'Responsabilidad Civil Profesional', 'Derecho Sanitario'],
     ARRAY['Cirugía General', 'Ginecología y Obstetricia', 'Medicina de Emergencia'],
     15,
     4.80,
     47,
     TRUE,
     '+51 998 877 665',
     'Abogada especialista en derecho médico y responsabilidad civil profesional, con 15 años asesorando a profesionales de la salud.');

-- ── Perfil del administrador demo ───────────────────────────────────────────
INSERT INTO admin_profiles (id, user_id, department, permissions) VALUES
    ('b0000000-0000-0000-0000-000000000003',
     'd0000000-0000-0000-0000-000000000003',
     'Dirección Médica',
     ARRAY['usuarios', 'documentos', 'auditoria', 'reportes', 'metricas']);
