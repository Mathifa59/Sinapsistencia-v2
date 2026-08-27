-- ============================================================================
-- Sinapsistencia -- V12: Corpus DS-03 de perfiles de abogados
--
-- Generado por ml-service/evaluation/build_corpus.py -- NO editar a mano.
-- Preserva los 12 perfiles ya sembrados (UPDATE, id/nombre intactos) y agrega
-- perfiles nuevos (INSERT) hasta completar el corpus DS-03 (docs/MATCHING-SPEC.md).
--
-- NO incluye columnas de carga de trabajo (current_caseload/max_caseload):
-- decision documentada -- ninguna variante de ablacion las consume y no existe
-- fuente de verdad en produccion para max_caseload.
--
-- Los 33 perfiles nuevos quedan is_active=TRUE (candidatos plenos del matching
-- en vivo, igual que en la evaluacion offline) pero NO autenticables: cada uno
-- tiene un password_hash bcrypt de una contraseña aleatoria de un solo uso,
-- generada y descartada en build_corpus.py. Ninguna se imprime ni se guarda.
--
-- Detalle completo de estas decisiones: docs/datasheet-corpus-ds03.md
-- ============================================================================

-- ── UPDATE: 12 perfiles anclados (11 por FK + Lucia Fernandez, que SI tenia fila
-- desde V3 -- error de investigacion corregido, ver comentario en build_corpus.py) ──
-- bio siempre se reescribe. medical_areas se fija explicitamente aunque el valor no
-- cambie (verificado campo a campo contra V3/V4/V5): asi el UPDATE queda auto-documentado
-- y no depende de que 'ya coincida' con lo que habia antes. specialties se preserva
-- salvo dos correcciones puntuales de valores invalidos (Diego Huaman y Lucia
-- Fernandez tenian 'Responsabilidad Civil Profesional', que no es uno de los 8
-- valores de LEGAL_SPECIALTIES; Lucia ademas tenia 'Medicina de Emergencia' en
-- medical_areas, que no es una de las 20 de baselines.py).
UPDATE lawyer_profiles SET
    specialties = ARRAY['Derecho Médico', 'Responsabilidad Civil Médica', 'Derecho Sanitario'],
    medical_areas = ARRAY['Cirugía General', 'Ginecología y Obstetricia'],
    bio = 'Es la abogada de cabecera para las consultas más delicadas del hospital, la que revisan primero cuando algo puede escalar a un reclamo formal. Prioriza siempre la conciliación temprana sobre el litigio, y sostiene que la mayoría de los conflictos se resuelven bien si la documentación está en orden desde el primer día. Coordina directamente con los servicios quirúrgicos y de maternidad del hospital.'
    WHERE user_id = 'd0000000-0000-0000-0000-000000000002';  -- Dra. Lucía Fernández Torres
UPDATE lawyer_profiles SET
    specialties = ARRAY['Responsabilidad Civil Médica', 'Negligencia Médica'],
    medical_areas = ARRAY['Cirugía General', 'Traumatología'],
    bio = 'Antes de litigar fue asistente legal en una aseguradora, donde aprendió a leer un peritaje médico línea por línea antes de que le llegara el caso ya armado. Esa costumbre no la perdió: revisa personalmente cada historia clínica antes de aceptar un caso. Sus clientes suelen llegar por recomendación de otros médicos a los que ya defendió.'
    WHERE user_id = 'd2000000-0000-0000-0000-000000000001';  -- Abg. Joaquín Espinoza Ruiz
UPDATE lawyer_profiles SET
    specialties = ARRAY['Derecho Sanitario', 'Consentimiento Informado'],
    medical_areas = ARRAY['Ginecología y Obstetricia', 'Pediatría'],
    bio = 'Se dedica casi exclusivamente a la atención materno-infantil: partos complicados, cuidados neonatales y las consultas de pediatría que terminan escalando cuando la comunicación con la familia falló antes que la técnica. Da charlas periódicas a residentes de ginecología y obstetricia sobre cómo documentar un consentimiento que resista una revisión legal.'
    WHERE user_id = 'd2000000-0000-0000-0000-000000000002';  -- Abg. Daniela Vargas Solís
UPDATE lawyer_profiles SET
    specialties = ARRAY['Derecho Penal Médico', 'Bioética y Derecho'],
    medical_areas = ARRAY['Anestesiología', 'Cirugía General'],
    bio = 'Dieciocho años defendiendo médicos en sede penal le han dejado una regla fija: el proceso se gana o se pierde en la primera declaración ante la fiscalía, no en el juicio. Se especializa en eventos adversos durante procedimientos con sedación, donde la línea entre riesgo asumido y negligencia suele decidirse por minutos en el registro anestésico.'
    WHERE user_id = 'd2000000-0000-0000-0000-000000000003';  -- Abg. Mateo Huamán Ríos
UPDATE lawyer_profiles SET
    specialties = ARRAY['Seguros Médicos', 'Derecho Médico'],
    medical_areas = ARRAY['Medicina General', 'Dermatología'],
    bio = 'Empezó su carrera del lado de la aseguradora, calificando pólizas de responsabilidad civil profesional para clínicas privadas, y hace cinco años cruzó al otro lado del mostrador para representar directamente a los médicos asegurados. Atiende sobre todo consultas ambulatorias y procedimientos estéticos menores.'
    WHERE user_id = 'd2000000-0000-0000-0000-000000000004';  -- Abg. Patricia Núñez Flores
UPDATE lawyer_profiles SET
    specialties = ARRAY['Negligencia Médica', 'Derecho Penal Médico', 'Derecho Médico'],
    medical_areas = ARRAY['Cardiología', 'Medicina General', 'Neumología'],
    bio = 'Su especialidad de facto es la medicina interna: casos de cardiología y neumología donde el paciente venía con varias comorbilidades y la defensa necesita reconstruir, a veces con ayuda de un perito externo, si el desenlace era o no evitable dado el cuadro de base. Litiga poco fuera de Lima, pero acepta consultoría remota para el interior.'
    WHERE user_id = 'd3000000-0000-0000-0000-000000000001';  -- Abg. Mario Castillo Bravo
UPDATE lawyer_profiles SET
    specialties = ARRAY['Consentimiento Informado', 'Bioética y Derecho', 'Derecho Sanitario'],
    medical_areas = ARRAY['Ginecología y Obstetricia', 'Pediatría', 'Endocrinología'],
    bio = 'Trabaja el punto exacto donde la obstetricia se cruza con la pediatría neonatal, y con menos frecuencia acompaña casos endocrinológicos complejos en gestantes. Fue auditora médico-legal antes de litigar, experiencia que aplica revisando la trazabilidad completa del expediente antes de aceptar cualquier caso nuevo.'
    WHERE user_id = 'd3000000-0000-0000-0000-000000000002';  -- Abg. Paola Ramírez Soto
UPDATE lawyer_profiles SET
    specialties = ARRAY['Seguros Médicos', 'Responsabilidad Civil Médica', 'Derecho Médico'],
    medical_areas = ARRAY['Traumatología', 'Cirugía General', 'Anestesiología'],
    bio = 'El expediente más largo que ha llevado tuvo catorce peritajes cruzados por una prótesis de cadera mal posicionada; lo ganó y desde entonces las aseguradoras lo llaman directamente para litigios complejos de cirugía traumatológica. Cobra por resultado en la mayoría de sus casos, algo poco común entre sus colegas.'
    WHERE user_id = 'd3000000-0000-0000-0000-000000000003';  -- Abg. Renato Salazar Méndez
UPDATE lawyer_profiles SET
    specialties = ARRAY['Derecho Médico', 'Negligencia Médica', 'Bioética y Derecho'],
    medical_areas = ARRAY['Oncología', 'Hematología', 'Medicina General'],
    bio = 'Los reclamos que atiende casi nunca son por un solo error puntual, sino por una cadena de decisiones a lo largo de meses de tratamiento oncológico: demoras en biopsias, cambios de protocolo sin explicación, interconsultas que se pierden en el camino. Trabaja con un comité médico propio que revisa la cronología clínica caso por caso.'
    WHERE user_id = 'd3000000-0000-0000-0000-000000000004';  -- Abg. Carmen Vega Ibáñez
UPDATE lawyer_profiles SET
    specialties = ARRAY['Derecho Penal Médico', 'Responsabilidad Civil Médica', 'Derecho Sanitario'],
    medical_areas = ARRAY['Neurología', 'Psiquiatría', 'Medicina General'],
    bio = 'La mitad de su cartera son internamientos psiquiátricos cuestionados por la familia del paciente, y la otra mitad son secuelas neurológicas tras procedimientos que se complicaron. Publicó un artículo sobre capacidad de consentir en pacientes con deterioro cognitivo que sigue siendo citado en foros de derecho sanitario.'
    WHERE user_id = 'd3000000-0000-0000-0000-000000000005';  -- Abg. Diego Huamán Vera
UPDATE lawyer_profiles SET
    specialties = ARRAY['Derecho Sanitario', 'Consentimiento Informado', 'Bioética y Derecho'],
    medical_areas = ARRAY['Dermatología', 'Oftalmología', 'Urología'],
    bio = 'Se mueve entre tres consultorios distintos —dermatológico, oftalmológico y urológico— porque comparten un mismo patrón de riesgo: procedimientos ambulatorios cortos donde nadie se detiene a documentar bien el consentimiento hasta que ya es tarde. Prepara los formularios de autorización de dos clínicas privadas de Lima.'
    WHERE user_id = 'd3000000-0000-0000-0000-000000000006';  -- Abg. Inés Quispe Loayza
UPDATE lawyer_profiles SET
    specialties = ARRAY['Responsabilidad Civil Médica', 'Seguros Médicos', 'Negligencia Médica'],
    medical_areas = ARRAY['Gastroenterología', 'Nefrología', 'Infectología'],
    bio = 'Construyó su práctica en el terreno menos disputado de la medicina interna: complicaciones de diálisis, cuadros infecciosos intrahospitalarios y errores de manejo digestivo que rara vez llegan a la prensa pero generan litigios largos y técnicos. Prefiere los peritajes propios antes que confiar en el informe médico legal oficial.'
    WHERE user_id = 'd3000000-0000-0000-0000-000000000007';  -- Abg. Jorge Paredes Flores

-- ── INSERT: 33 perfiles nuevos del corpus DS-03 ──
-- is_active=TRUE: deben ser candidatos PLENOS del matching en vivo, igual que
-- en la evaluacion offline (excluirlos con is_active=FALSE reintroduce la misma
-- divergencia evaluacion/produccion que la Correccion 2 evito a nivel de campos,
-- aqui a nivel de 33 perfiles completos). La cuenta queda no-autenticable porque
-- cada password_hash es un bcrypt de una contraseña aleatoria de un solo uso,
-- generada y descartada en build_corpus.py -- nunca impresa ni guardada en ningun
-- lado. Ver docs/datasheet-corpus-ds03.md.
INSERT INTO profiles (id, email, name, role, is_active, password_hash) VALUES
    ('b4000000-0000-0000-0000-000000000001', 'rocio.ochoa@sinapsistencia.pe', 'Abg. Rocío Ochoa Manrique', 'lawyer', TRUE, '$2b$12$bNiAh3zj5Xw625yB4h73Q.r1WuWKVAxecqXu4gUJFLSoHL3JIgxq2');
INSERT INTO lawyer_profiles (user_id, cab, specialties, medical_areas, years_experience, rating, resolved_cases, available, phone, bio) VALUES
    ('b4000000-0000-0000-0000-000000000001', '40112', ARRAY['Responsabilidad Civil Médica', 'Derecho Médico'], ARRAY['Oncología'],
     6, 4.2, 9, TRUE, '+51 921 101 201', 'Egresó de la Universidad Católica Santo Toribio de Mogrovejo, en Chiclayo, y desde entonces atiende sobre todo casos derivados de cirugías oncológicas con reingresos no previstos por complicaciones postoperatorias. Trabaja sola, sin estudio, y responde directamente los mensajes de sus clientes fuera de horario cuando el caso lo amerita.');

INSERT INTO profiles (id, email, name, role, is_active, password_hash) VALUES
    ('b4000000-0000-0000-0000-000000000002', 'fernando.zuniga@sinapsistencia.pe', 'Abg. Fernando Zúñiga Palacios', 'lawyer', TRUE, '$2b$12$oVni/CrSR9X23opsFhFeP.Rh/3usOMVNLYwUfQqe7XkRg1dK6C.ju');
INSERT INTO lawyer_profiles (user_id, cab, specialties, medical_areas, years_experience, rating, resolved_cases, available, phone, bio) VALUES
    ('b4000000-0000-0000-0000-000000000002', '40233', ARRAY['Negligencia Médica', 'Responsabilidad Civil Médica'], ARRAY['Traumatología', 'Cirugía General'],
     22, 4.85, 88, TRUE, '+51 922 102 202', 'Con más de dos décadas de ejercicio, es de los pocos abogados de la plaza que aceptan clientes solo por referencia directa de otro médico. Especializado en fracturas mal consolidadas y reintervenciones quirúrgicas, considera que la mayoría de sus victorias vienen de un buen perito, no de un buen alegato.');

INSERT INTO profiles (id, email, name, role, is_active, password_hash) VALUES
    ('b4000000-0000-0000-0000-000000000003', 'milagros.cardenas@sinapsistencia.pe', 'Abg. Milagros Cárdenas Vidal', 'lawyer', TRUE, '$2b$12$JKlx1jNx2NoSfnybC8zPI.pfhJ4OZU.ORiDhBetwI5C.gtkvlLw2u');
INSERT INTO lawyer_profiles (user_id, cab, specialties, medical_areas, years_experience, rating, resolved_cases, available, phone, bio) VALUES
    ('b4000000-0000-0000-0000-000000000003', '40318', ARRAY['Derecho Sanitario', 'Consentimiento Informado'], ARRAY['Ginecología y Obstetricia'],
     4, 4.1, 6, TRUE, '+51 923 103 203', 'Es la más joven del grupo, pero ya lleva seis casos ganados en obstetricia, todos relacionados con consentimiento para procedimientos de inducción y cesárea de urgencia. Antes de titularse trabajó dos años como asistente de sala en un servicio de maternidad, lo que le da un manejo poco común del lenguaje clínico.');

INSERT INTO profiles (id, email, name, role, is_active, password_hash) VALUES
    ('b4000000-0000-0000-0000-000000000004', 'ricardo.palomino@sinapsistencia.pe', 'Abg. Ricardo Palomino Aguirre', 'lawyer', TRUE, '$2b$12$Y1l8YX4/uyLdKloMWkas9.ldsdjse0lD3cSdx8Sccrokf4b3ilZwW');
INSERT INTO lawyer_profiles (user_id, cab, specialties, medical_areas, years_experience, rating, resolved_cases, available, phone, bio) VALUES
    ('b4000000-0000-0000-0000-000000000004', '40407', ARRAY['Derecho Médico', 'Derecho Sanitario'], ARRAY['Medicina General', 'Pediatría'],
     13, 4.55, 30, TRUE, '+51 924 104 204', 'Atiende clínicas de atención primaria y consultorios pediátricos familiares, un segmento que casi nadie más cubre porque los montos en juego son bajos y los casos, tediosos. Dicta un taller anual gratuito para médicos jóvenes sobre cómo redactar una historia clínica que no los exponga.');

INSERT INTO profiles (id, email, name, role, is_active, password_hash) VALUES
    ('b4000000-0000-0000-0000-000000000005', 'adriana.trujillo@sinapsistencia.pe', 'Abg. Adriana Trujillo Bermúdez', 'lawyer', TRUE, '$2b$12$cRi793QAuGLX88mUX4RYbe1EPcJYh4C/T/dnC5/gKBDw9NU1vIcNC');
INSERT INTO lawyer_profiles (user_id, cab, specialties, medical_areas, years_experience, rating, resolved_cases, available, phone, bio) VALUES
    ('b4000000-0000-0000-0000-000000000005', '40521', ARRAY['Responsabilidad Civil Médica', 'Negligencia Médica'], ARRAY['Ginecología y Obstetricia', 'Cirugía General'],
     8, 4.35, 17, TRUE, '+51 925 105 205', 'Los últimos tres años los dedicó casi por completo a un solo tipo de caso: complicaciones de cesárea con reintervención posterior. Conoce a los peritos obstétricos de la plaza uno por uno y prefiere elegir el suyo antes de que lo asigne el juzgado.');

INSERT INTO profiles (id, email, name, role, is_active, password_hash) VALUES
    ('b4000000-0000-0000-0000-000000000006', 'sebastian.cornejo@sinapsistencia.pe', 'Abg. Sebastián Cornejo Zevallos', 'lawyer', TRUE, '$2b$12$5035EvvsSrZwGodGEHE7D.pA28w3HsVcF7hBieQcehuoXPWdYPrJK');
INSERT INTO lawyer_profiles (user_id, cab, specialties, medical_areas, years_experience, rating, resolved_cases, available, phone, bio) VALUES
    ('b4000000-0000-0000-0000-000000000006', '40609', ARRAY['Seguros Médicos', 'Derecho Médico'], ARRAY['Traumatología'],
     11, 4.45, 25, TRUE, '+51 926 106 206', 'Litiga contra las mismas cuatro o cinco aseguradoras desde hace once años, lo que le da una ventaja poco habitual: conoce de memoria las cláusulas de exclusión que suelen aplicar en accidentes de tránsito con fracturas expuestas. Su tasa de conciliación extrajudicial supera el setenta por ciento.');

INSERT INTO profiles (id, email, name, role, is_active, password_hash) VALUES
    ('b4000000-0000-0000-0000-000000000007', 'gabriela.manrique@sinapsistencia.pe', 'Abg. Gabriela Manrique Rosales', 'lawyer', TRUE, '$2b$12$9fBNgJQVq2WWYH68j.K66eNBEz87i278CuLGZgbcSLe3K86MwFwES');
INSERT INTO lawyer_profiles (user_id, cab, specialties, medical_areas, years_experience, rating, resolved_cases, available, phone, bio) VALUES
    ('b4000000-0000-0000-0000-000000000007', '40714', ARRAY['Bioética y Derecho', 'Derecho Sanitario'], ARRAY['Pediatría'],
     15, 4.6, 41, TRUE, '+51 927 107 207', 'Enseña bioética clínica los fines de semana en un diplomado de posgrado y litiga casos de pediatría entre semana; sostiene que ambas cosas se alimentan mutuamente. Su bio profesional en el colegio de abogados la describe simplemente como ''defensora de decisiones difíciles'', una frase que ella misma escribió.');

INSERT INTO profiles (id, email, name, role, is_active, password_hash) VALUES
    ('b4000000-0000-0000-0000-000000000008', 'alvaro.farfan@sinapsistencia.pe', 'Abg. Álvaro Farfán Aliaga', 'lawyer', TRUE, '$2b$12$yAx3ykIWh3HkOny.C40B2uXuis8gBxRo7OTREGSDyHVH6.ysQQKd2');
INSERT INTO lawyer_profiles (user_id, cab, specialties, medical_areas, years_experience, rating, resolved_cases, available, phone, bio) VALUES
    ('b4000000-0000-0000-0000-000000000008', '40822', ARRAY['Derecho Penal Médico', 'Negligencia Médica'], ARRAY['Cirugía General', 'Traumatología'],
     20, 4.7, 57, TRUE, '+51 928 108 208', 'Formado en San Marcos, con una maestría en Ciencias Penales por la misma universidad. Veinte años después sigue prefiriendo la sala penal a cualquier otra: dice que ahí se define de verdad si un médico trabajó dentro de la lex artis o no. Rechaza más casos de los que acepta.');

INSERT INTO profiles (id, email, name, role, is_active, password_hash) VALUES
    ('b4000000-0000-0000-0000-000000000009', 'beatriz.salcedo@sinapsistencia.pe', 'Abg. Beatriz Salcedo Otiniano', 'lawyer', TRUE, '$2b$12$1ribpR68Xjid0Fr2WUFciOxIRhp88MgGKUNW/eYjeBtXd2hbFHH5S');
INSERT INTO lawyer_profiles (user_id, cab, specialties, medical_areas, years_experience, rating, resolved_cases, available, phone, bio) VALUES
    ('b4000000-0000-0000-0000-000000000009', '40915', ARRAY['Responsabilidad Civil Médica', 'Derecho Médico'], ARRAY['Cardiología'],
     9, 4.4, 18, TRUE, '+51 929 109 209', 'Se hizo un nombre defendiendo a un cardiólogo acusado de retrasar una angioplastía en un caso que llegó a segunda instancia y terminó absuelto; desde entonces la mitad de sus consultas llegan por ese caso puntual. Trabaja con un cardiólogo forense externo en cada expediente nuevo.');

INSERT INTO profiles (id, email, name, role, is_active, password_hash) VALUES
    ('b4000000-0000-0000-0000-000000000010', 'ivan.contreras@sinapsistencia.pe', 'Abg. Iván Contreras Sotelo', 'lawyer', TRUE, '$2b$12$UdWcw17lkSIfwAR3FyaB8.FBDVznwvV1wbACghfXAyaBdwpzzzknO');
INSERT INTO lawyer_profiles (user_id, cab, specialties, medical_areas, years_experience, rating, resolved_cases, available, phone, bio) VALUES
    ('b4000000-0000-0000-0000-000000000010', '41003', ARRAY['Derecho Penal Médico', 'Bioética y Derecho'], ARRAY['Neurología', 'Psiquiatría'],
     17, 4.75, 49, TRUE, '+51 930 110 210', 'Se especializó en neurología y psiquiatría después de un caso temprano en su carrera sobre alta médica prematura de un paciente con riesgo suicida, que lo marcó lo suficiente como para no volver a tocar otro tipo de expediente. Es perito judicial acreditado en capacidad civil.');

INSERT INTO profiles (id, email, name, role, is_active, password_hash) VALUES
    ('b4000000-0000-0000-0000-000000000011', 'lourdes.benavides@sinapsistencia.pe', 'Abg. Lourdes Benavides Ugarte', 'lawyer', TRUE, '$2b$12$QZaCknCrC4cCtAxVl.rqNecdLGp1Zx2Roh2tm2tUYb5nS6a/BFF2a');
INSERT INTO lawyer_profiles (user_id, cab, specialties, medical_areas, years_experience, rating, resolved_cases, available, phone, bio) VALUES
    ('b4000000-0000-0000-0000-000000000011', '41128', ARRAY['Derecho Sanitario', 'Derecho Médico'], ARRAY['Oncología'],
     6, 4.15, 8, TRUE, '+51 931 111 211', 'Antes de litigar trabajó cinco años como coordinadora de calidad en un hospital oncológico, revisando exactamente el tipo de expedientes que ahora defiende desde el otro lado. Prefiere resolver por conciliación siempre que el paciente aún esté en tratamiento activo.');

INSERT INTO profiles (id, email, name, role, is_active, password_hash) VALUES
    ('b4000000-0000-0000-0000-000000000012', 'ximena.coronado@sinapsistencia.pe', 'Abg. Ximena Coronado Villanueva', 'lawyer', TRUE, '$2b$12$.JXkcORfNNAdkcm1HTRcy.W0STxgO/hmFY0QUwCwxTvzc2ilWnY5K');
INSERT INTO lawyer_profiles (user_id, cab, specialties, medical_areas, years_experience, rating, resolved_cases, available, phone, bio) VALUES
    ('b4000000-0000-0000-0000-000000000012', '41207', ARRAY['Negligencia Médica', 'Seguros Médicos'], ARRAY['Anestesiología'],
     12, 4.5, 27, TRUE, '+51 932 112 212', 'El registro de signos vitales minuto a minuto es, según ella, la pieza que gana o pierde cualquier caso de sedación complicada; se lo repite a cada anestesiólogo que la contrata para revisar su protocolo antes de que ocurra algo. Litiga poco, asesora mucho.');

INSERT INTO profiles (id, email, name, role, is_active, password_hash) VALUES
    ('b4000000-0000-0000-0000-000000000013', 'rodrigo.teran@sinapsistencia.pe', 'Abg. Rodrigo Terán Marín', 'lawyer', TRUE, '$2b$12$PwaEFL..cG6FbxdaipXnhOb6VzqHeA7dep0yZVnVapboUEOj0pJ1i');
INSERT INTO lawyer_profiles (user_id, cab, specialties, medical_areas, years_experience, rating, resolved_cases, available, phone, bio) VALUES
    ('b4000000-0000-0000-0000-000000000013', '41315', ARRAY['Derecho Médico', 'Responsabilidad Civil Médica'], ARRAY['Urología'],
     19, 4.65, 52, TRUE, '+51 933 113 213', 'Su especialidad no aparece en ningún diploma: aprendió a litigar casos de urología acompañando a su padre, también abogado, desde los veinticuatro años. Hoy lleva el mismo estudio familiar y sigue recibiendo referidos de los clientes que atendió su padre.');

INSERT INTO profiles (id, email, name, role, is_active, password_hash) VALUES
    ('b4000000-0000-0000-0000-000000000014', 'camila.delgado@sinapsistencia.pe', 'Abg. Camila Delgado Escobar', 'lawyer', TRUE, '$2b$12$Wdh/EJpNepb5WMTJh9oPAOOO.OgZwNjJuBNjLef6tzCu4GvxkFhXm');
INSERT INTO lawyer_profiles (user_id, cab, specialties, medical_areas, years_experience, rating, resolved_cases, available, phone, bio) VALUES
    ('b4000000-0000-0000-0000-000000000014', '41402', ARRAY['Derecho Sanitario', 'Consentimiento Informado'], ARRAY['Gastroenterología'],
     8, 4.3, 14, TRUE, '+51 934 114 214', 'Se enfoca en procedimientos endoscópicos que terminan en perforación o complicación no advertida al paciente. Sostiene que casi todos esos casos se explican por un formulario de autorización firmado sin haberlo leído, no por un error técnico del procedimiento en sí.');

INSERT INTO profiles (id, email, name, role, is_active, password_hash) VALUES
    ('b4000000-0000-0000-0000-000000000015', 'hugo.ponce@sinapsistencia.pe', 'Abg. Hugo Ponce Arriola', 'lawyer', TRUE, '$2b$12$hZ6Mt15y4A/efn3FU/tGMu9oGAJ76lSoOUe1yvCJ.bJQ57VWci1Qq');
INSERT INTO lawyer_profiles (user_id, cab, specialties, medical_areas, years_experience, rating, resolved_cases, available, phone, bio) VALUES
    ('b4000000-0000-0000-0000-000000000015', '41519', ARRAY['Derecho Penal Médico', 'Derecho Médico'], ARRAY['Psiquiatría', 'Neurología'],
     5, 4.05, 5, TRUE, '+51 935 115 215', 'Es reciente en la práctica de neurología y psiquiatría forense, pero viene de cuatro años en la defensa penal pública, donde litigó decenas de audiencias antes de cumplir treinta. Todavía construye su cartera de referidos médicos y acepta, por ahora, casos que otros colegas más establecidos prefieren no tomar por el monto reducido de los honorarios.');

INSERT INTO profiles (id, email, name, role, is_active, password_hash) VALUES
    ('b4000000-0000-0000-0000-000000000016', 'valeria.aguirre@sinapsistencia.pe', 'Abg. Valeria Aguirre Bocanegra', 'lawyer', TRUE, '$2b$12$5NWhn59PzwvfrDSByFre0.D.fAeBl.5Sw4wvgn9gp7CXehxToYGPW');
INSERT INTO lawyer_profiles (user_id, cab, specialties, medical_areas, years_experience, rating, resolved_cases, available, phone, bio) VALUES
    ('b4000000-0000-0000-0000-000000000016', '41633', ARRAY['Responsabilidad Civil Médica', 'Bioética y Derecho'], ARRAY['Oncología', 'Hematología'],
     14, 4.55, 33, TRUE, '+51 936 116 216', 'Perdió a un familiar cercano por lo que en su momento consideró una demora diagnóstica, y ese episodio personal terminó definiendo el rumbo de su carrera hacia la oncología y la hematología. Lo cuenta abiertamente en la primera reunión con cada cliente nuevo.');

INSERT INTO profiles (id, email, name, role, is_active, password_hash) VALUES
    ('b4000000-0000-0000-0000-000000000017', 'teresa.alvarado@sinapsistencia.pe', 'Abg. Teresa Alvarado Yupanqui', 'lawyer', TRUE, '$2b$12$I3vs6khYg3LlKuoHFl6FQePSO7/wUe2znQJg/2hthqZvcblbGfKR.');
INSERT INTO lawyer_profiles (user_id, cab, specialties, medical_areas, years_experience, rating, resolved_cases, available, phone, bio) VALUES
    ('b4000000-0000-0000-0000-000000000017', '41748', ARRAY['Derecho Sanitario', 'Consentimiento Informado'], ARRAY['Endocrinología'],
     10, 4.2, 12, TRUE, '+51 937 117 217', 'Es una de las pocas abogadas de Lima con casos activos en endocrinología, un área donde casi no hay litigio especializado porque los reclamos suelen diluirse entre varias comorbilidades. Trabaja principalmente con pacientes diabéticos con complicaciones por manejo tardío, y suele apoyarse en un endocrinólogo de confianza para traducir el expediente clínico al lenguaje del proceso.');

INSERT INTO profiles (id, email, name, role, is_active, password_hash) VALUES
    ('b4000000-0000-0000-0000-000000000018', 'oscar.bedoya@sinapsistencia.pe', 'Abg. Óscar Bedoya Landa', 'lawyer', TRUE, '$2b$12$FuAl1Ac9Juuu3VnTTor9KednMbUAkwLhxuIMT9hB3wiOL8fxxsSOi');
INSERT INTO lawyer_profiles (user_id, cab, specialties, medical_areas, years_experience, rating, resolved_cases, available, phone, bio) VALUES
    ('b4000000-0000-0000-0000-000000000018', '41856', ARRAY['Negligencia Médica', 'Derecho Médico'], ARRAY['Neumología'],
     16, 4.45, 38, TRUE, '+51 938 118 218', 'La mayoría de sus casos llegan por reingresos hospitalarios de pacientes con insuficiencia respiratoria crónica que la familia considera prevenibles. Prepara cada expediente con línea de tiempo detallada de saturación de oxígeno, algo que pocos colegas se molestan en reconstruir.');

INSERT INTO profiles (id, email, name, role, is_active, password_hash) VALUES
    ('b4000000-0000-0000-0000-000000000019', 'natalia.guevara@sinapsistencia.pe', 'Abg. Natalia Guevara Chumpitaz', 'lawyer', TRUE, '$2b$12$XtZJJpPGp5wch0v14MIzKe6XRTPdahX10JUrJKu4qad8Z5kxgaJqS');
INSERT INTO lawyer_profiles (user_id, cab, specialties, medical_areas, years_experience, rating, resolved_cases, available, phone, bio) VALUES
    ('b4000000-0000-0000-0000-000000000019', '41904', ARRAY['Responsabilidad Civil Médica', 'Derecho Sanitario'], ARRAY['Nefrología'],
     7, 4.1, 9, TRUE, '+51 939 119 219', 'Un solo caso de infección asociada a catéter de diálisis, que terminó en una indemnización considerable, la posicionó como referente para el resto de los centros de hemodiálisis de la ciudad. Sigue siendo prácticamente el único tipo de caso que acepta.');

INSERT INTO profiles (id, email, name, role, is_active, password_hash) VALUES
    ('b4000000-0000-0000-0000-000000000020', 'marco.delaguila@sinapsistencia.pe', 'Abg. Marco Del Águila Rosales', 'lawyer', TRUE, '$2b$12$7hYMBKjmgyrozc4har8at.EN1KbPqssvA.lJ97NQOCbmoIlxWViP.');
INSERT INTO lawyer_profiles (user_id, cab, specialties, medical_areas, years_experience, rating, resolved_cases, available, phone, bio) VALUES
    ('b4000000-0000-0000-0000-000000000020', '42011', ARRAY['Derecho Penal Médico', 'Negligencia Médica'], ARRAY['Infectología'],
     13, 4.35, 24, TRUE, '+51 940 120 220', 'Litiga sobre todo infecciones intrahospitalarias mal contenidas, un terreno técnico que casi nadie más quiere pisar porque exige entender protocolos de bioseguridad además de derecho penal. Fue perito de parte en el brote hospitalario más citado de los últimos años en Lima.');

INSERT INTO profiles (id, email, name, role, is_active, password_hash) VALUES
    ('b4000000-0000-0000-0000-000000000021', 'silvia.rios@sinapsistencia.pe', 'Abg. Silvia Ríos Contreras', 'lawyer', TRUE, '$2b$12$75tRYRX13qF7e0UJYaLf8e3vWjUWMSD8V2uuxUupW0PINxn0/SYH6');
INSERT INTO lawyer_profiles (user_id, cab, specialties, medical_areas, years_experience, rating, resolved_cases, available, phone, bio) VALUES
    ('b4000000-0000-0000-0000-000000000021', '42127', ARRAY['Consentimiento Informado', 'Derecho Sanitario'], ARRAY['Oftalmología'],
     4, 4.0, 4, TRUE, '+51 941 121 221', 'Recién colegiada, construyó su primer caso relevante defendiendo a un oftalmólogo tras una cirugía refractiva con resultado no esperado; el paciente había firmado el consentimiento, pero sin que se le explicaran los riesgos verbalmente, y ese matiz decidió el proceso.');

INSERT INTO profiles (id, email, name, role, is_active, password_hash) VALUES
    ('b4000000-0000-0000-0000-000000000022', 'andres.villanueva@sinapsistencia.pe', 'Abg. Andrés Villanueva Bravo', 'lawyer', TRUE, '$2b$12$zNIdlWnewo54ouQxIUdcOe.QopKW8olaKmusSbc2Jcpp5aQTtwGHy');
INSERT INTO lawyer_profiles (user_id, cab, specialties, medical_areas, years_experience, rating, resolved_cases, available, phone, bio) VALUES
    ('b4000000-0000-0000-0000-000000000022', '42208', ARRAY['Derecho Médico', 'Responsabilidad Civil Médica'], ARRAY['Reumatología'],
     11, 4.25, 15, TRUE, '+51 942 122 222', 'Es de los muy pocos abogados en Lima con casos activos en reumatología, casi siempre relacionados con retraso en el diagnóstico de enfermedades autoinmunes de progresión lenta. Reconoce abiertamente que es un nicho de bajo volumen, pero de clientes muy fieles.');

INSERT INTO profiles (id, email, name, role, is_active, password_hash) VALUES
    ('b4000000-0000-0000-0000-000000000023', 'claudia.chavez@sinapsistencia.pe', 'Abg. Claudia Chávez Marín', 'lawyer', TRUE, '$2b$12$7XtwAEjuBUayWlqFH3ax0.X58zDmfjJPvu0444okdlgifm.U3yV4C');
INSERT INTO lawyer_profiles (user_id, cab, specialties, medical_areas, years_experience, rating, resolved_cases, available, phone, bio) VALUES
    ('b4000000-0000-0000-0000-000000000023', '42315', ARRAY['Seguros Médicos', 'Bioética y Derecho'], ARRAY['Cardiología'],
     9, 4.3, 16, TRUE, '+51 943 123 223', 'Cubre un segmento poco habitual dentro de la cardiología: eventos adversos durante pruebas de esfuerzo o cateterismos ambulatorios, donde la línea entre riesgo asumido y negligencia depende del protocolo previo de evaluación del paciente. Las aseguradoras la buscan específicamente para dictaminar si la complicación era previsible, y su informe suele ser determinante para que el caso se resuelva sin llegar a juicio.');

INSERT INTO profiles (id, email, name, role, is_active, password_hash) VALUES
    ('b4000000-0000-0000-0000-000000000024', 'ruben.gutierrez@sinapsistencia.pe', 'Abg. Rubén Gutiérrez Palomino', 'lawyer', TRUE, '$2b$12$mF.ekUhxrBefP7/kcyQczuVpHBeJg/CIeDhL8rLk2Y7PkdX6XlTD2');
INSERT INTO lawyer_profiles (user_id, cab, specialties, medical_areas, years_experience, rating, resolved_cases, available, phone, bio) VALUES
    ('b4000000-0000-0000-0000-000000000024', '42409', ARRAY['Responsabilidad Civil Médica', 'Consentimiento Informado'], ARRAY['Urología'],
     10, 4.4, 20, TRUE, '+51 944 124 224', 'Diez años dedicados casi en exclusiva a procedimientos ambulatorios de baja complejidad, donde el mayor riesgo legal no está en la técnica sino en la documentación previa: consentimientos incompletos, historias clínicas con vacíos, autorizaciones verbales nunca registradas por escrito. Revisa cada expediente como si fuera a terminar en un juzgado.');

INSERT INTO profiles (id, email, name, role, is_active, password_hash) VALUES
    ('b4000000-0000-0000-0000-000000000025', 'karina.sotelo@sinapsistencia.pe', 'Abg. Karina Sotelo Delgado', 'lawyer', TRUE, '$2b$12$wgf1jilQq9xAwodOaOcpCelxtJdkJBq0qy./kJNZK85y.vsWmPa/e');
INSERT INTO lawyer_profiles (user_id, cab, specialties, medical_areas, years_experience, rating, resolved_cases, available, phone, bio) VALUES
    ('b4000000-0000-0000-0000-000000000025', '42501', ARRAY['Seguros Médicos', 'Consentimiento Informado'], ARRAY['Psiquiatría'],
     10, 4.35, 18, TRUE, '+51 945 125 225', 'Diez años dedicados casi en exclusiva a procedimientos ambulatorios de baja complejidad, donde el mayor riesgo legal no está en la técnica sino en la documentación previa: consentimientos incompletos, historias clínicas con vacíos, autorizaciones verbales nunca registradas por escrito. Trabaja de la mano con las aseguradoras que cubren a la clínica.');

INSERT INTO profiles (id, email, name, role, is_active, password_hash) VALUES
    ('b4000000-0000-0000-0000-000000000026', 'estefania.rojas@sinapsistencia.pe', 'Abg. Estefanía Rojas Ibáñez', 'lawyer', TRUE, '$2b$12$tTaLiplZaK6aOqgd/tRkP.LPA/GipSFQZ//K50oAjzbCmB9fyfzlq');
INSERT INTO lawyer_profiles (user_id, cab, specialties, medical_areas, years_experience, rating, resolved_cases, available, phone, bio) VALUES
    ('b4000000-0000-0000-0000-000000000026', '42618', ARRAY['Derecho Penal Médico', 'Negligencia Médica'], ARRAY['Urología'],
     13, 4.55, 29, TRUE, '+51 946 126 226', 'Construyó su reputación en un solo tipo de expediente: intervenciones que se prolongaron mucho más de lo previsto y terminaron con una reintervención de urgencia. El común denominador, según ella, casi nunca es el cirujano sino la falta de un plan B documentado antes de entrar a sala.');

INSERT INTO profiles (id, email, name, role, is_active, password_hash) VALUES
    ('b4000000-0000-0000-0000-000000000027', 'gonzalo.manrique@sinapsistencia.pe', 'Abg. Gonzalo Manrique Trujillo', 'lawyer', TRUE, '$2b$12$4aLbzI8egf1Qssw0/hMid.HQZxHb6bgwZR1Cr2UoeQUTwxWPPbizC');
INSERT INTO lawyer_profiles (user_id, cab, specialties, medical_areas, years_experience, rating, resolved_cases, available, phone, bio) VALUES
    ('b4000000-0000-0000-0000-000000000027', '42704', ARRAY['Responsabilidad Civil Médica', 'Negligencia Médica'], ARRAY['Gastroenterología'],
     13, 4.5, 26, TRUE, '+51 947 127 227', 'Construyó su reputación en un solo tipo de expediente: intervenciones que se prolongaron mucho más de lo previsto y terminaron con una reintervención de urgencia. El común denominador, según él, casi nunca es el cirujano sino la falta de un plan B documentado antes de entrar a sala. Litiga solo en Lima Metropolitana.');

INSERT INTO profiles (id, email, name, role, is_active, password_hash) VALUES
    ('b4000000-0000-0000-0000-000000000028', 'pilar.zevallos@sinapsistencia.pe', 'Abg. Pilar Zevallos Farfán', 'lawyer', TRUE, '$2b$12$/Gh7965/87Pc0ThR30TYPunfYWYXcs8YlZ7i1/GitO3gPsF2f.jyK');
INSERT INTO lawyer_profiles (user_id, cab, specialties, medical_areas, years_experience, rating, resolved_cases, available, phone, bio) VALUES
    ('b4000000-0000-0000-0000-000000000028', '42812', ARRAY['Derecho Sanitario', 'Bioética y Derecho'], ARRAY['Gastroenterología'],
     8, 4.2, 11, TRUE, '+51 948 128 228', 'Atiende sobre todo casos donde el paciente reclama no haber entendido de verdad el tratamiento crónico que le indicaron, más allá de haber firmado el papel correspondiente. Sostiene que buena parte de estos reclamos se resuelven si el médico documenta la conversación, no solo la firma.');

INSERT INTO profiles (id, email, name, role, is_active, password_hash) VALUES
    ('b4000000-0000-0000-0000-000000000029', 'julio.aliaga@sinapsistencia.pe', 'Abg. Julio Aliaga Cornejo', 'lawyer', TRUE, '$2b$12$bU9wQhJCzydbI7j9OL43u.8skvVcM3Pkwvyfry1OOIJmCgPIFlPDy');
INSERT INTO lawyer_profiles (user_id, cab, specialties, medical_areas, years_experience, rating, resolved_cases, available, phone, bio) VALUES
    ('b4000000-0000-0000-0000-000000000029', '42933', ARRAY['Derecho Sanitario', 'Consentimiento Informado'], ARRAY['Reumatología'],
     8, 4.15, 10, TRUE, '+51 949 129 229', 'Atiende sobre todo casos donde el paciente reclama no haber entendido de verdad el tratamiento crónico que le indicaron, más allá de haber firmado el papel correspondiente. Sostiene que buena parte de estos reclamos se resuelven si el médico documenta la conversación, no solo la firma. Da seguimiento personal a cada caso hasta el cierre.');

INSERT INTO profiles (id, email, name, role, is_active, password_hash) VALUES
    ('b4000000-0000-0000-0000-000000000030', 'diana.landa@sinapsistencia.pe', 'Abg. Diana Landa Ochoa', 'lawyer', TRUE, '$2b$12$Mx1DkysxzrsYs.rfsHVgG.Fxbpv3mTQhFS9w0zI5zAcm.kRp9NwM6');
INSERT INTO lawyer_profiles (user_id, cab, specialties, medical_areas, years_experience, rating, resolved_cases, available, phone, bio) VALUES
    ('b4000000-0000-0000-0000-000000000030', '43015', ARRAY['Responsabilidad Civil Médica', 'Derecho Médico'], ARRAY['Pediatría', 'Medicina General'],
     21, 4.8, 71, TRUE, '+51 950 130 230', 'Es una de las de mayor antigüedad del directorio, con más de dos décadas de ejercicio. Empezó su carrera en un estudio grande y hace catorce años se independizó para litigar exclusivamente casos pediátricos. Su despacho queda a dos cuadras del hospital de niños, y no es casual.');

INSERT INTO profiles (id, email, name, role, is_active, password_hash) VALUES
    ('b4000000-0000-0000-0000-000000000031', 'elena.marin@sinapsistencia.pe', 'Abg. Elena Marín Bedoya', 'lawyer', TRUE, '$2b$12$hjmVsI201bFI8.2lJtZ6t.dhwrNIhf0CXQCbuLe58VI2eX/i6uy82');
INSERT INTO lawyer_profiles (user_id, cab, specialties, medical_areas, years_experience, rating, resolved_cases, available, phone, bio) VALUES
    ('b4000000-0000-0000-0000-000000000031', '43126', ARRAY['Negligencia Médica', 'Bioética y Derecho'], ARRAY['Cardiología', 'Neurología'],
     15, 4.6, 40, TRUE, '+51 951 131 231', 'Se formó como enfermera antes de estudiar Derecho, y todavía usa esa base clínica para leer un electrocardiograma o una resonancia sin depender por completo del perito. Divide su cartera entre casos cardiológicos y neurológicos casi en partes iguales, y es una de las pocas del gremio que redacta ella misma el resumen técnico de cada expediente antes de entregarlo al perito externo.');

INSERT INTO profiles (id, email, name, role, is_active, password_hash) VALUES
    ('b4000000-0000-0000-0000-000000000032', 'christian.aguirre@sinapsistencia.pe', 'Abg. Christian Aguirre Terán', 'lawyer', TRUE, '$2b$12$HF18FQsfC0lka0heP0LpUeIuiHFT7eJzJwrG72temFyFtdVw032N6');
INSERT INTO lawyer_profiles (user_id, cab, specialties, medical_areas, years_experience, rating, resolved_cases, available, phone, bio) VALUES
    ('b4000000-0000-0000-0000-000000000032', '43219', ARRAY['Derecho Médico', 'Seguros Médicos'], ARRAY['Anestesiología'],
     6, 4.15, 7, TRUE, '+51 952 132 232', 'Todavía comparte oficina con otros dos abogados jóvenes, pero ya construyó una relación estable con dos clínicas privadas que lo llaman de forma recurrente para revisar protocolos quirúrgicos antes de que ocurra un incidente, no después. Es la parte del trabajo que más le interesa: prevenir el litigio en vez de ganarlo.');

INSERT INTO profiles (id, email, name, role, is_active, password_hash) VALUES
    ('b4000000-0000-0000-0000-000000000033', 'fiorella.ponce@sinapsistencia.pe', 'Abg. Fiorella Ponce Guevara', 'lawyer', TRUE, '$2b$12$cP3LDu6OgJbJIgW4oZVzb.3JH/CBKWSlQyZJYsMaYNm.jPwgeEQ9K');
INSERT INTO lawyer_profiles (user_id, cab, specialties, medical_areas, years_experience, rating, resolved_cases, available, phone, bio) VALUES
    ('b4000000-0000-0000-0000-000000000033', '43304', ARRAY['Derecho Sanitario', 'Derecho Penal Médico'], ARRAY['Ginecología y Obstetricia', 'Traumatología'],
     12, 4.5, 31, TRUE, '+51 953 133 233', 'Litigó dos años en provincia antes de trasladarse a Lima, y sigue recibiendo casos derivados de colegas del norte del país. Sus casos más frecuentes combinan complicaciones obstétricas con lesiones traumáticas del parto instrumentado, un cruce poco común que pocos estudios de la capital manejan con la misma soltura.');
