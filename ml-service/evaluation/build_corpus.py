"""Construye el corpus DS-03 de perfiles de abogados (docs/MATCHING-SPEC.md §3).

Preserva los 12 perfiles ya sembrados (11 con FK desde cases/contact_requests +
1 -- la cuenta demo de abogado -- que resulto tener fila desde V3 pese a que la
investigacion original de Fase 1 concluyo lo contrario; corregido tras chocar
con una unique constraint al aplicar V12 contra Postgres real) y agrega
perfiles nuevos hasta completar el corpus objetivo, con distribucion
deliberadamente desbalanceada por area medica y 3 pares "dificiles" (biografia
parecida, especialidad distinta) para el estudio de ablacion.

Decisiones ya aprobadas (ver hilo de Fase 1 en la memoria de la sesion):
  - specialties[] usa EXCLUSIVAMENTE los 8 valores de LEGAL_SPECIALTIES
    (frontend/src/app/shared/constants.ts). No se toca el frontend.
  - medical_areas[] usa EXCLUSIVAMENTE las 20 de SPECIALTY_BASELINE
    (ml-service/app/risk/baselines.py) - importadas, no reescritas a mano.
  - current_caseload / max_caseload: OMITIDOS. No los usa ninguna variante de
    ablacion (el filtro duro es solo `available`) y no existe fuente de verdad
    en produccion para max_caseload. Documentado en el datasheet, no en columnas.
  - Los 4 perfiles con solicitud pendiente/rechazada a un area no coincidente
    (Paola Ramirez, Carmen Vega, Diego Huaman, Jorge Paredes) conservan
    specialties/medical_areas intactos; solo se reescribe su bio. Un rechazo
    por area fuera de cobertura es realista, no un bug de los datos.
  - d3000000-...005 (Diego Huaman) tenia 'Responsabilidad Civil Profesional' en
    specialties[], que NO es uno de los 8 valores de LEGAL_SPECIALTIES -> se
    corrige a 'Responsabilidad Civil Médica' (unico ajuste de specialties en
    un perfil anclado, y es una correccion de inconsistencia, no una
    reclasificacion editorial).
  - Coherencia bio<->medical_areas: obligatoria para TODOS los perfiles, con
    UNA excepcion documentada -- los pares "dificiles" (`pair` in A/B/C) estan
    EXENTOS de este requisito, siempre que la bio no AFIRME un area distinta
    de la del campo estructurado. Genericidad no es lo mismo que contradiccion:
    una bio generica que no nombra ningun area no rompe el par (es el diseño:
    solo los campos estructurados deben discriminar); una bio que describe
    contenido clinico de OTRA area si lo rompe. Ver Correccion 2, Fase 1.
  - Seguridad de las 33 cuentas nuevas (solo-corpus, no demo interactiva):
    is_active = TRUE (deben ser candidatos plenos del matching EN VIVO, no
    solo del JSON offline -- excluirlas con is_active=FALSE crea la misma
    divergencia evaluacion/produccion que la Correccion 2 evito a nivel de
    campos, pero a nivel de 33 perfiles completos). La autenticacion se
    bloquea con una contraseña aleatoria distinta por perfil
    (`secrets.token_urlsafe(32)`), hasheada con bcrypt y DESCARTADA de
    inmediato -- nunca se imprime, nunca se guarda. Cada hash se autoverifica
    (formato bcrypt valido + bcrypt.checkpw() antes de escribirlo) para que
    AuthService la rechace limpiamente por credenciales invalidas, no por una
    excepcion de parseo. Ver docs/datasheet-corpus-ds03.md.

Salida:
  - ml-service/data/reference/ds03_lawyers.json
  - Reporte en consola: distribucion real vs objetivo, estadisticas de
    vocabulario (chequeo de degeneracion de la matriz TF-IDF) y % de bios que
    nombran su area medica explicitamente.

Uso:
    cd ml-service
    python evaluation/build_corpus.py
"""

from __future__ import annotations

import json
import re
import secrets
import sys
import unicodedata
from collections import Counter
from pathlib import Path

import bcrypt

if sys.stdout.encoding and sys.stdout.encoding.lower() != "utf-8":
    sys.stdout.reconfigure(encoding="utf-8")

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.risk.baselines import SPECIALTY_BASELINE  # noqa: E402
from app.matching.model import SPANISH_STOPWORDS, _normalize  # noqa: E402

MEDICAL_AREAS = list(SPECIALTY_BASELINE.keys())

# Debe coincidir EXACTO con frontend/src/app/shared/constants.ts -> LEGAL_SPECIALTIES.
# Fuente unica de verdad: el frontend. Si esa constante cambia, este literal
# debe actualizarse a mano (no hay forma de importarlo desde Python).
LEGAL_SPECIALTIES = [
    "Derecho Médico", "Responsabilidad Civil Médica", "Derecho Penal Médico",
    "Bioética y Derecho", "Seguros Médicos", "Derecho Sanitario",
    "Negligencia Médica", "Consentimiento Informado",
]

OUT_PATH = Path(__file__).resolve().parents[1] / "data" / "reference" / "ds03_lawyers.json"

# Objetivo de cobertura por nivel (aprobado en Fase 1). Los numeros son el
# conteo de ABOGADOS que mencionan cada area en medical_areas[], no de casos.
TARGET_COVERAGE = {
    # Alta
    "Cirugía General": (5, 7), "Ginecología y Obstetricia": (5, 7),
    "Traumatología": (5, 7), "Medicina General": (5, 7), "Pediatría": (5, 7),
    # Media
    "Cardiología": (3, 4), "Neurología": (3, 4), "Oncología": (3, 4),
    "Anestesiología": (3, 4), "Urología": (3, 4), "Gastroenterología": (3, 4),
    "Psiquiatría": (3, 4),
    # Escasa (adversos, a propósito)
    "Dermatología": (1, 2), "Endocrinología": (1, 2), "Oftalmología": (1, 2),
    "Neumología": (1, 2), "Nefrología": (1, 2), "Reumatología": (1, 2),
    "Infectología": (1, 2), "Hematología": (1, 2),
}

assert set(TARGET_COVERAGE) == set(MEDICAL_AREAS), (
    "TARGET_COVERAGE debe cubrir exactamente las 20 areas de baselines.py"
)


def L(*names: str) -> list[str]:
    for n in names:
        assert n in LEGAL_SPECIALTIES, f"'{n}' no está en LEGAL_SPECIALTIES"
    return list(names)


def M(*areas: str) -> list[str]:
    for a in areas:
        assert a in MEDICAL_AREAS, f"'{a}' no está en las 20 áreas de baselines.py"
    return list(areas)


# ═══════════════════════════════════════════════════════════════════════════
# 1) LOS 12 PERFILES EXISTENTES — id y nombre preservados siempre.
#    `op` indica la operación SQL que le corresponde en V12: update | insert.
#    `names_area` = True si la bio nueva nombra su área médica explícitamente.
# ═══════════════════════════════════════════════════════════════════════════

EXISTING_LAWYERS = [
    {
        # Cuenta demo de abogado (login). CORRECCION: en la verificacion contra
        # Postgres real (Correccion Fase 1, aplicar V12) resulto que SI tiene
        # fila en lawyer_profiles desde V3 (id b0000000-...002, cab '34521') --
        # mi hallazgo original de que "no tiene fila" era incorrecto, encontrado
        # solo al intentar el INSERT y chocar con la unique constraint de
        # user_id. Pasa a UPDATE como los otros 11 anclados.
        # Ademas su fila original en V3 traia dos valores invalidos que nunca
        # se revisaron: specialties tenia 'Responsabilidad Civil Profesional'
        # (mismo error que Diego Huaman -> misma correccion) y medical_areas
        # tenia 'Medicina de Emergencia', que NO es una de las 20 de
        # baselines.py -- se elimina (sin reemplazo: no hay un area de las 20
        # que sea equivalente 1:1, y sus 2 areas restantes ya estan
        # verificadas contra sus 3 casos reales en V4: Cirugia General x2,
        # Ginecologia y Obstetricia x1).
        "op": "update",
        "lawyer_id": "d0000000-0000-0000-0000-000000000002",
        "full_name": "Dra. Lucía Fernández Torres",
        "bar_number": "34521",  # valor real de V3, cab -- no se toca en el UPDATE
        "specialties": L("Derecho Médico", "Responsabilidad Civil Médica", "Derecho Sanitario"),
        "medical_areas": M("Cirugía General", "Ginecología y Obstetricia"),
        "years_experience": 15,
        "rating": 4.80,
        "resolved_cases": 47,
        "names_area": False,
        "bio": (
            "Es la abogada de cabecera para las consultas más delicadas del hospital, "
            "la que revisan primero cuando algo puede escalar a un reclamo formal. "
            "Prioriza siempre la conciliación temprana sobre el litigio, y sostiene que "
            "la mayoría de los conflictos se resuelven bien si la documentación está en "
            "orden desde el primer día. Coordina directamente con los servicios "
            "quirúrgicos y de maternidad del hospital."
        ),
    },
    {
        "op": "update",
        "lawyer_id": "d2000000-0000-0000-0000-000000000001",
        "full_name": "Abg. Joaquín Espinoza Ruiz",
        "bar_number": "31245",
        "specialties": L("Responsabilidad Civil Médica", "Negligencia Médica"),
        "medical_areas": M("Cirugía General", "Traumatología"),
        "years_experience": 10,
        "rating": 4.60,
        "resolved_cases": 32,
        "names_area": False,
        "bio": (
            "Antes de litigar fue asistente legal en una aseguradora, donde aprendió a "
            "leer un peritaje médico línea por línea antes de que le llegara el caso ya "
            "armado. Esa costumbre no la perdió: revisa personalmente cada historia "
            "clínica antes de aceptar un caso. Sus clientes suelen llegar por "
            "recomendación de otros médicos a los que ya defendió."
        ),
    },
    {
        "op": "update",
        "lawyer_id": "d2000000-0000-0000-0000-000000000002",
        "full_name": "Abg. Daniela Vargas Solís",
        "bar_number": "33678",
        "specialties": L("Derecho Sanitario", "Consentimiento Informado"),
        "medical_areas": M("Ginecología y Obstetricia", "Pediatría"),
        "years_experience": 7,
        "rating": 4.50,
        "resolved_cases": 19,
        "names_area": True,
        "bio": (
            "Se dedica casi exclusivamente a la atención materno-infantil: partos "
            "complicados, cuidados neonatales y las consultas de pediatría que terminan "
            "escalando cuando la comunicación con la familia falló antes que la técnica. "
            "Da charlas periódicas a residentes de ginecología y obstetricia sobre cómo "
            "documentar un consentimiento que resista una revisión legal."
        ),
    },
    {
        "op": "update",
        "lawyer_id": "d2000000-0000-0000-0000-000000000003",
        "full_name": "Abg. Mateo Huamán Ríos",
        "bar_number": "29456",
        "specialties": L("Derecho Penal Médico", "Bioética y Derecho"),
        "medical_areas": M("Anestesiología", "Cirugía General"),
        "years_experience": 18,
        "rating": 4.90,
        "resolved_cases": 64,
        "names_area": False,
        "bio": (
            "Dieciocho años defendiendo médicos en sede penal le han dejado una regla "
            "fija: el proceso se gana o se pierde en la primera declaración ante la "
            "fiscalía, no en el juicio. Se especializa en eventos adversos durante "
            "procedimientos con sedación, donde la línea entre riesgo asumido y "
            "negligencia suele decidirse por minutos en el registro anestésico."
        ),
    },
    {
        "op": "update",
        "lawyer_id": "d2000000-0000-0000-0000-000000000004",
        "full_name": "Abg. Patricia Núñez Flores",
        "bar_number": "35890",
        "specialties": L("Seguros Médicos", "Derecho Médico"),
        "medical_areas": M("Medicina General", "Dermatología"),
        "years_experience": 5,
        "rating": 4.30,
        "resolved_cases": 11,
        "names_area": False,
        "bio": (
            "Empezó su carrera del lado de la aseguradora, calificando pólizas de "
            "responsabilidad civil profesional para clínicas privadas, y hace cinco "
            "años cruzó al otro lado del mostrador para representar directamente a los "
            "médicos asegurados. Atiende sobre todo consultas ambulatorias y "
            "procedimientos estéticos menores."
        ),
    },
    {
        "op": "update",
        "lawyer_id": "d3000000-0000-0000-0000-000000000001",
        "full_name": "Abg. Mario Castillo Bravo",
        "bar_number": "21567",
        "specialties": L("Negligencia Médica", "Derecho Penal Médico", "Derecho Médico"),
        "medical_areas": M("Cardiología", "Medicina General", "Neumología"),
        "years_experience": 12,
        "rating": 4.60,
        "resolved_cases": 34,
        "names_area": True,
        "bio": (
            "Su especialidad de facto es la medicina interna: casos de cardiología y "
            "neumología donde el paciente venía con varias comorbilidades y la defensa "
            "necesita reconstruir, a veces con ayuda de un perito externo, si el "
            "desenlace era o no evitable dado el cuadro de base. Litiga poco fuera de "
            "Lima, pero acepta consultoría remota para el interior."
        ),
    },
    {
        "op": "update",
        "lawyer_id": "d3000000-0000-0000-0000-000000000002",
        "full_name": "Abg. Paola Ramírez Soto",
        "bar_number": "24890",
        "specialties": L("Consentimiento Informado", "Bioética y Derecho", "Derecho Sanitario"),
        "medical_areas": M("Ginecología y Obstetricia", "Pediatría", "Endocrinología"),
        "years_experience": 9,
        "rating": 4.70,
        "resolved_cases": 21,
        "names_area": True,
        "bio": (
            "Trabaja el punto exacto donde la obstetricia se cruza con la pediatría "
            "neonatal, y con menos frecuencia acompaña casos endocrinológicos "
            "complejos en gestantes. Fue auditora médico-legal antes de litigar, "
            "experiencia que aplica revisando la trazabilidad completa del expediente "
            "antes de aceptar cualquier caso nuevo."
        ),
    },
    {
        "op": "update",
        "lawyer_id": "d3000000-0000-0000-0000-000000000003",
        "full_name": "Abg. Renato Salazar Méndez",
        "bar_number": "27345",
        "specialties": L("Seguros Médicos", "Responsabilidad Civil Médica", "Derecho Médico"),
        "medical_areas": M("Traumatología", "Cirugía General", "Anestesiología"),
        "years_experience": 18,
        "rating": 4.90,
        "resolved_cases": 63,
        "names_area": False,
        "bio": (
            "El expediente más largo que ha llevado tuvo catorce peritajes cruzados por "
            "una prótesis de cadera mal posicionada; lo ganó y desde entonces las "
            "aseguradoras lo llaman directamente para litigios complejos de cirugía "
            "traumatológica. Cobra por resultado en la mayoría de sus casos, algo poco "
            "común entre sus colegas."
        ),
    },
    {
        "op": "update",
        "lawyer_id": "d3000000-0000-0000-0000-000000000004",
        "full_name": "Abg. Carmen Vega Ibáñez",
        "bar_number": "19234",
        "specialties": L("Derecho Médico", "Negligencia Médica", "Bioética y Derecho"),
        "medical_areas": M("Oncología", "Hematología", "Medicina General"),
        "years_experience": 10,
        "rating": 4.50,
        "resolved_cases": 28,
        "names_area": False,
        "bio": (
            "Los reclamos que atiende casi nunca son por un solo error puntual, sino por "
            "una cadena de decisiones a lo largo de meses de tratamiento oncológico: "
            "demoras en biopsias, cambios de protocolo sin explicación, "
            "interconsultas que se pierden en el camino. Trabaja con un comité médico "
            "propio que revisa la cronología clínica caso por caso."
        ),
    },
    {
        "op": "update",
        "lawyer_id": "d3000000-0000-0000-0000-000000000005",
        "full_name": "Abg. Diego Huamán Vera",
        "bar_number": "30678",
        "specialties": L("Derecho Penal Médico", "Responsabilidad Civil Médica", "Derecho Sanitario"),
        "medical_areas": M("Neurología", "Psiquiatría", "Medicina General"),
        "years_experience": 14,
        "rating": 4.65,
        "resolved_cases": 39,
        "names_area": False,
        "bio": (
            "La mitad de su cartera son internamientos psiquiátricos cuestionados por la "
            "familia del paciente, y la otra mitad son secuelas neurológicas tras "
            "procedimientos que se complicaron. Publicó un artículo sobre capacidad de "
            "consentir en pacientes con deterioro cognitivo que sigue siendo citado en "
            "foros de derecho sanitario."
        ),
    },
    {
        "op": "update",
        "lawyer_id": "d3000000-0000-0000-0000-000000000006",
        "full_name": "Abg. Inés Quispe Loayza",
        "bar_number": "15432",
        "specialties": L("Derecho Sanitario", "Consentimiento Informado", "Bioética y Derecho"),
        "medical_areas": M("Dermatología", "Oftalmología", "Urología"),
        "years_experience": 7,
        "rating": 4.40,
        "resolved_cases": 16,
        "names_area": False,
        "bio": (
            "Se mueve entre tres consultorios distintos —dermatológico, oftalmológico y "
            "urológico— porque comparten un mismo patrón de riesgo: procedimientos "
            "ambulatorios cortos donde nadie se detiene a documentar bien el "
            "consentimiento hasta que ya es tarde. Prepara los formularios de "
            "autorización de dos clínicas privadas de Lima."
        ),
    },
    {
        "op": "update",
        "lawyer_id": "d3000000-0000-0000-0000-000000000007",
        "full_name": "Abg. Jorge Paredes Flores",
        "bar_number": "26781",
        # Corrección: 'Responsabilidad Civil Profesional' no está en LEGAL_SPECIALTIES.
        "specialties": L("Responsabilidad Civil Médica", "Seguros Médicos", "Negligencia Médica"),
        "medical_areas": M("Gastroenterología", "Nefrología", "Infectología"),
        "years_experience": 16,
        "rating": 4.75,
        "resolved_cases": 51,
        "names_area": False,
        "bio": (
            "Construyó su práctica en el terreno menos disputado de la medicina "
            "interna: complicaciones de diálisis, cuadros infecciosos intrahospitalarios "
            "y errores de manejo digestivo que rara vez llegan a la prensa pero generan "
            "litigios largos y técnicos. Prefiere los peritajes propios antes que "
            "confiar en el informe médico legal oficial."
        ),
    },
]

# ═══════════════════════════════════════════════════════════════════════════
# 2) PERFILES NUEVOS — 38, con 3 pares "difíciles" incluidos (marcados `pair`).
# ═══════════════════════════════════════════════════════════════════════════

NEW_LAWYERS = [
    # ── Refuerzo nivel ALTO ──────────────────────────────────────────────
    {
        "full_name": "Abg. Rocío Ochoa Manrique", "bar_number": "40112",
        "specialties": L("Responsabilidad Civil Médica", "Derecho Médico"),
        "medical_areas": M("Oncología"),
        "years_experience": 6, "rating": 4.20, "resolved_cases": 9,
        "names_area": False,
        "bio": (
            "Egresó de la Universidad Católica Santo Toribio de Mogrovejo, en "
            "Chiclayo, y desde entonces atiende sobre todo casos derivados de "
            "cirugías oncológicas con reingresos no previstos por complicaciones "
            "postoperatorias. Trabaja sola, sin estudio, y responde directamente "
            "los mensajes de sus clientes fuera de horario cuando el caso lo "
            "amerita."
        ),
    },
    {
        "full_name": "Abg. Fernando Zúñiga Palacios", "bar_number": "40233",
        "specialties": L("Negligencia Médica", "Responsabilidad Civil Médica"),
        "medical_areas": M("Traumatología", "Cirugía General"),
        "years_experience": 22, "rating": 4.85, "resolved_cases": 88,
        "names_area": False,
        "bio": (
            "Con más de dos décadas de ejercicio, es de los pocos abogados de la "
            "plaza que aceptan clientes solo por referencia directa de otro médico. "
            "Especializado en fracturas mal consolidadas y reintervenciones "
            "quirúrgicas, considera que la mayoría de sus victorias vienen de un "
            "buen perito, no de un buen alegato."
        ),
    },
    {
        "full_name": "Abg. Milagros Cárdenas Vidal", "bar_number": "40318",
        "specialties": L("Derecho Sanitario", "Consentimiento Informado"),
        "medical_areas": M("Ginecología y Obstetricia"),
        "years_experience": 4, "rating": 4.10, "resolved_cases": 6,
        "names_area": True,
        "bio": (
            "Es la más joven del grupo, pero ya lleva seis casos ganados en "
            "obstetricia, todos relacionados con consentimiento para procedimientos "
            "de inducción y cesárea de urgencia. Antes de titularse trabajó dos años "
            "como asistente de sala en un servicio de maternidad, lo que le da un "
            "manejo poco común del lenguaje clínico."
        ),
    },
    {
        "full_name": "Abg. Ricardo Palomino Aguirre", "bar_number": "40407",
        "specialties": L("Derecho Médico", "Derecho Sanitario"),
        "medical_areas": M("Medicina General", "Pediatría"),
        "years_experience": 13, "rating": 4.55, "resolved_cases": 30,
        "names_area": False,
        "bio": (
            "Atiende clínicas de atención primaria y consultorios pediátricos "
            "familiares, un segmento que casi nadie más cubre porque los montos en "
            "juego son bajos y los casos, tediosos. Dicta un taller anual gratuito "
            "para médicos jóvenes sobre cómo redactar una historia clínica que no "
            "los exponga."
        ),
    },
    {
        "full_name": "Abg. Adriana Trujillo Bermúdez", "bar_number": "40521",
        "specialties": L("Responsabilidad Civil Médica", "Negligencia Médica"),
        "medical_areas": M("Ginecología y Obstetricia", "Cirugía General"),
        "years_experience": 8, "rating": 4.35, "resolved_cases": 17,
        "names_area": False,
        "bio": (
            "Los últimos tres años los dedicó casi por completo a un solo tipo de "
            "caso: complicaciones de cesárea con reintervención posterior. Conoce a "
            "los peritos obstétricos de la plaza uno por uno y prefiere elegir el "
            "suyo antes de que lo asigne el juzgado."
        ),
    },
    {
        "full_name": "Abg. Sebastián Cornejo Zevallos", "bar_number": "40609",
        "specialties": L("Seguros Médicos", "Derecho Médico"),
        "medical_areas": M("Traumatología"),
        "years_experience": 11, "rating": 4.45, "resolved_cases": 25,
        "names_area": False,
        "bio": (
            "Litiga contra las mismas cuatro o cinco aseguradoras desde hace once "
            "años, lo que le da una ventaja poco habitual: conoce de memoria las "
            "cláusulas de exclusión que suelen aplicar en accidentes de tránsito con "
            "fracturas expuestas. Su tasa de conciliación extrajudicial supera el "
            "setenta por ciento."
        ),
    },
    {
        "full_name": "Abg. Gabriela Manrique Rosales", "bar_number": "40714",
        "specialties": L("Bioética y Derecho", "Derecho Sanitario"),
        "medical_areas": M("Pediatría"),
        "years_experience": 15, "rating": 4.60, "resolved_cases": 41,
        "names_area": True,
        "bio": (
            "Enseña bioética clínica los fines de semana en un diplomado de "
            "posgrado y litiga casos de pediatría entre semana; sostiene que ambas "
            "cosas se alimentan mutuamente. Su bio profesional en el colegio de "
            "abogados la describe simplemente como 'defensora de decisiones "
            "difíciles', una frase que ella misma escribió."
        ),
    },
    {
        "full_name": "Abg. Álvaro Farfán Aliaga", "bar_number": "40822",
        "specialties": L("Derecho Penal Médico", "Negligencia Médica"),
        "medical_areas": M("Cirugía General", "Traumatología"),
        "years_experience": 20, "rating": 4.70, "resolved_cases": 57,
        "names_area": False,
        "bio": (
            "Formado en San Marcos, con una maestría en Ciencias Penales por la "
            "misma universidad. Veinte años después sigue prefiriendo la sala penal "
            "a cualquier otra: dice que ahí se define de verdad si un médico "
            "trabajó dentro de la lex artis o no. Rechaza más casos de los que "
            "acepta."
        ),
    },
    # ── Refuerzo nivel MEDIO ─────────────────────────────────────────────
    {
        "full_name": "Abg. Beatriz Salcedo Otiniano", "bar_number": "40915",
        "specialties": L("Responsabilidad Civil Médica", "Derecho Médico"),
        "medical_areas": M("Cardiología"),
        "years_experience": 9, "rating": 4.40, "resolved_cases": 18,
        "names_area": False,
        "bio": (
            "Se hizo un nombre defendiendo a un cardiólogo acusado de retrasar una "
            "angioplastía en un caso que llegó a segunda instancia y terminó "
            "absuelto; desde entonces la mitad de sus consultas llegan por ese "
            "caso puntual. Trabaja con un cardiólogo forense externo en cada "
            "expediente nuevo."
        ),
    },
    {
        "full_name": "Abg. Iván Contreras Sotelo", "bar_number": "41003",
        "specialties": L("Derecho Penal Médico", "Bioética y Derecho"),
        "medical_areas": M("Neurología", "Psiquiatría"),
        "years_experience": 17, "rating": 4.75, "resolved_cases": 49,
        "names_area": True,
        "bio": (
            "Se especializó en neurología y psiquiatría después de un caso "
            "temprano en su carrera sobre alta médica prematura de un paciente con "
            "riesgo suicida, que lo marcó lo suficiente como para no volver a tocar "
            "otro tipo de expediente. Es perito judicial acreditado en capacidad "
            "civil."
        ),
    },
    {
        "full_name": "Abg. Lourdes Benavides Ugarte", "bar_number": "41128",
        "specialties": L("Derecho Sanitario", "Derecho Médico"),
        "medical_areas": M("Oncología"),
        "years_experience": 6, "rating": 4.15, "resolved_cases": 8,
        "names_area": False,
        "bio": (
            "Antes de litigar trabajó cinco años como coordinadora de calidad en "
            "un hospital oncológico, revisando exactamente el tipo de expedientes "
            "que ahora defiende desde el otro lado. Prefiere resolver por "
            "conciliación siempre que el paciente aún esté en tratamiento activo."
        ),
    },
    {
        "full_name": "Abg. Ximena Coronado Villanueva", "bar_number": "41207",
        "specialties": L("Negligencia Médica", "Seguros Médicos"),
        "medical_areas": M("Anestesiología"),
        "years_experience": 12, "rating": 4.50, "resolved_cases": 27,
        "names_area": False,
        "bio": (
            "El registro de signos vitales minuto a minuto es, según ella, la "
            "pieza que gana o pierde cualquier caso de sedación complicada; se lo "
            "repite a cada anestesiólogo que la contrata para revisar su protocolo "
            "antes de que ocurra algo. Litiga poco, asesora mucho."
        ),
    },
    {
        "full_name": "Abg. Rodrigo Terán Marín", "bar_number": "41315",
        "specialties": L("Derecho Médico", "Responsabilidad Civil Médica"),
        "medical_areas": M("Urología"),
        "years_experience": 19, "rating": 4.65, "resolved_cases": 52,
        "names_area": True,
        "bio": (
            "Su especialidad no aparece en ningún diploma: aprendió a litigar "
            "casos de urología acompañando a su padre, también abogado, desde los "
            "veinticuatro años. Hoy lleva el mismo estudio familiar y sigue "
            "recibiendo referidos de los clientes que atendió su padre."
        ),
    },
    {
        "full_name": "Abg. Camila Delgado Escobar", "bar_number": "41402",
        "specialties": L("Derecho Sanitario", "Consentimiento Informado"),
        "medical_areas": M("Gastroenterología"),
        "years_experience": 8, "rating": 4.30, "resolved_cases": 14,
        "names_area": False,
        "bio": (
            "Se enfoca en procedimientos endoscópicos que terminan en perforación "
            "o complicación no advertida al paciente. Sostiene que casi todos esos "
            "casos se explican por un formulario de autorización firmado sin "
            "haberlo leído, no por un error técnico del procedimiento en sí."
        ),
    },
    {
        "full_name": "Abg. Hugo Ponce Arriola", "bar_number": "41519",
        "specialties": L("Derecho Penal Médico", "Derecho Médico"),
        "medical_areas": M("Psiquiatría", "Neurología"),
        "years_experience": 5, "rating": 4.05, "resolved_cases": 5,
        "names_area": True,
        "bio": (
            "Es reciente en la práctica de neurología y psiquiatría forense, pero "
            "viene de cuatro años en la defensa penal pública, donde litigó decenas "
            "de audiencias antes de cumplir treinta. Todavía construye su cartera "
            "de referidos médicos y acepta, por ahora, casos que otros colegas más "
            "establecidos prefieren no tomar por el monto reducido de los honorarios."
        ),
    },
    {
        "full_name": "Abg. Valeria Aguirre Bocanegra", "bar_number": "41633",
        "specialties": L("Responsabilidad Civil Médica", "Bioética y Derecho"),
        "medical_areas": M("Oncología", "Hematología"),
        "years_experience": 14, "rating": 4.55, "resolved_cases": 33,
        "names_area": True,
        "bio": (
            "Perdió a un familiar cercano por lo que en su momento consideró una "
            "demora diagnóstica, y ese episodio personal terminó definiendo el "
            "rumbo de su carrera hacia la oncología y la hematología. Lo cuenta "
            "abiertamente en la primera reunión con cada cliente nuevo."
        ),
    },
    # ── Refuerzo nivel ESCASO (adversos) ─────────────────────────────────
    {
        "full_name": "Abg. Teresa Alvarado Yupanqui", "bar_number": "41748",
        "specialties": L("Derecho Sanitario", "Consentimiento Informado"),
        "medical_areas": M("Endocrinología"),
        "years_experience": 10, "rating": 4.20, "resolved_cases": 12,
        "names_area": True,
        "bio": (
            "Es una de las pocas abogadas de Lima con casos activos en "
            "endocrinología, un área donde casi no hay litigio especializado "
            "porque los reclamos suelen diluirse entre varias comorbilidades. "
            "Trabaja principalmente con pacientes diabéticos con complicaciones "
            "por manejo tardío, y suele apoyarse en un endocrinólogo de confianza "
            "para traducir el expediente clínico al lenguaje del proceso."
        ),
    },
    {
        "full_name": "Abg. Óscar Bedoya Landa", "bar_number": "41856",
        "specialties": L("Negligencia Médica", "Derecho Médico"),
        "medical_areas": M("Neumología"),
        "years_experience": 16, "rating": 4.45, "resolved_cases": 38,
        "names_area": False,
        "bio": (
            "La mayoría de sus casos llegan por reingresos hospitalarios de "
            "pacientes con insuficiencia respiratoria crónica que la familia "
            "considera prevenibles. Prepara cada expediente con línea de tiempo "
            "detallada de saturación de oxígeno, algo que pocos colegas se molestan "
            "en reconstruir."
        ),
    },
    {
        "full_name": "Abg. Natalia Guevara Chumpitaz", "bar_number": "41904",
        "specialties": L("Responsabilidad Civil Médica", "Derecho Sanitario"),
        "medical_areas": M("Nefrología"),
        "years_experience": 7, "rating": 4.10, "resolved_cases": 9,
        "names_area": False,
        "bio": (
            "Un solo caso de infección asociada a catéter de diálisis, que "
            "terminó en una indemnización considerable, la posicionó como "
            "referente para el resto de los centros de hemodiálisis de la ciudad. "
            "Sigue siendo prácticamente el único tipo de caso que acepta."
        ),
    },
    {
        "full_name": "Abg. Marco Del Águila Rosales", "bar_number": "42011",
        "specialties": L("Derecho Penal Médico", "Negligencia Médica"),
        "medical_areas": M("Infectología"),
        "years_experience": 13, "rating": 4.35, "resolved_cases": 24,
        "names_area": True,
        "bio": (
            "Litiga sobre todo infecciones intrahospitalarias mal contenidas, un "
            "terreno técnico que casi nadie más quiere pisar porque exige entender "
            "protocolos de bioseguridad además de derecho penal. Fue perito de "
            "parte en el brote hospitalario más citado de los últimos años en "
            "Lima."
        ),
    },
    {
        "full_name": "Abg. Silvia Ríos Contreras", "bar_number": "42127",
        "specialties": L("Consentimiento Informado", "Derecho Sanitario"),
        "medical_areas": M("Oftalmología"),
        "years_experience": 4, "rating": 4.00, "resolved_cases": 4,
        "names_area": False,
        "bio": (
            "Recién colegiada, construyó su primer caso relevante defendiendo a "
            "un oftalmólogo tras una cirugía refractiva con resultado no esperado; "
            "el paciente había firmado el consentimiento, pero sin que se le "
            "explicaran los riesgos verbalmente, y ese matiz decidió el proceso."
        ),
    },
    {
        "full_name": "Abg. Andrés Villanueva Bravo", "bar_number": "42208",
        "specialties": L("Derecho Médico", "Responsabilidad Civil Médica"),
        "medical_areas": M("Reumatología"),
        "years_experience": 11, "rating": 4.25, "resolved_cases": 15,
        "names_area": True,
        "bio": (
            "Es de los muy pocos abogados en Lima con casos activos en "
            "reumatología, casi siempre relacionados con retraso en el diagnóstico "
            "de enfermedades autoinmunes de progresión lenta. Reconoce abiertamente "
            "que es un nicho de bajo volumen, pero de clientes muy fieles."
        ),
    },
    {
        "full_name": "Abg. Claudia Chávez Marín", "bar_number": "42315",
        "specialties": L("Seguros Médicos", "Bioética y Derecho"),
        "medical_areas": M("Cardiología"),
        "years_experience": 9, "rating": 4.30, "resolved_cases": 16,
        "names_area": True,
        "bio": (
            "Cubre un segmento poco habitual dentro de la cardiología: eventos "
            "adversos durante pruebas de esfuerzo o cateterismos ambulatorios, "
            "donde la línea entre riesgo asumido y negligencia depende del "
            "protocolo previo de evaluación del paciente. Las aseguradoras la "
            "buscan específicamente para dictaminar si la complicación era "
            "previsible, y su informe suele ser determinante para que el caso se "
            "resuelva sin llegar a juicio."
        ),
    },
    # ── 3 pares "difíciles": bio casi idéntica, area/especialidad distinta ──
    {
        "pair": "A", "full_name": "Abg. Rubén Gutiérrez Palomino", "bar_number": "42409",
        "specialties": L("Responsabilidad Civil Médica", "Consentimiento Informado"),
        "medical_areas": M("Urología"),
        "years_experience": 10, "rating": 4.40, "resolved_cases": 20,
        "names_area": False,
        "bio": (
            "Diez años dedicados casi en exclusiva a procedimientos ambulatorios "
            "de baja complejidad, donde el mayor riesgo legal no está en la "
            "técnica sino en la documentación previa: consentimientos "
            "incompletos, historias clínicas con vacíos, autorizaciones verbales "
            "nunca registradas por escrito. Revisa cada expediente como si fuera "
            "a terminar en un juzgado."
        ),
    },
    {
        "pair": "A", "full_name": "Abg. Karina Sotelo Delgado", "bar_number": "42501",
        "specialties": L("Seguros Médicos", "Consentimiento Informado"),
        "medical_areas": M("Psiquiatría"),
        "years_experience": 10, "rating": 4.35, "resolved_cases": 18,
        "names_area": False,
        "bio": (
            "Diez años dedicados casi en exclusiva a procedimientos ambulatorios "
            "de baja complejidad, donde el mayor riesgo legal no está en la "
            "técnica sino en la documentación previa: consentimientos "
            "incompletos, historias clínicas con vacíos, autorizaciones verbales "
            "nunca registradas por escrito. Trabaja de la mano con las "
            "aseguradoras que cubren a la clínica."
        ),
    },
    {
        "pair": "B", "full_name": "Abg. Estefanía Rojas Ibáñez", "bar_number": "42618",
        "specialties": L("Derecho Penal Médico", "Negligencia Médica"),
        "medical_areas": M("Urología"),
        "years_experience": 13, "rating": 4.55, "resolved_cases": 29,
        "names_area": False,
        "bio": (
            "Construyó su reputación en un solo tipo de expediente: intervenciones "
            "que se prolongaron mucho más de lo previsto y terminaron con una "
            "reintervención de urgencia. El común denominador, según ella, casi "
            "nunca es el cirujano sino la falta de un plan B documentado antes de "
            "entrar a sala."
        ),
    },
    {
        "pair": "B", "full_name": "Abg. Gonzalo Manrique Trujillo", "bar_number": "42704",
        "specialties": L("Responsabilidad Civil Médica", "Negligencia Médica"),
        "medical_areas": M("Gastroenterología"),
        "years_experience": 13, "rating": 4.50, "resolved_cases": 26,
        "names_area": False,
        "bio": (
            "Construyó su reputación en un solo tipo de expediente: intervenciones "
            "que se prolongaron mucho más de lo previsto y terminaron con una "
            "reintervención de urgencia. El común denominador, según él, casi "
            "nunca es el cirujano sino la falta de un plan B documentado antes de "
            "entrar a sala. Litiga solo en Lima Metropolitana."
        ),
    },
    {
        "pair": "C", "full_name": "Abg. Pilar Zevallos Farfán", "bar_number": "42812",
        "specialties": L("Derecho Sanitario", "Bioética y Derecho"),
        "medical_areas": M("Gastroenterología"),
        "years_experience": 8, "rating": 4.20, "resolved_cases": 11,
        "names_area": False,
        "bio": (
            "Atiende sobre todo casos donde el paciente reclama no haber "
            "entendido de verdad el tratamiento crónico que le indicaron, más allá "
            "de haber firmado el papel correspondiente. Sostiene que buena parte "
            "de estos reclamos se resuelven si el médico documenta la conversación, "
            "no solo la firma."
        ),
    },
    {
        "pair": "C", "full_name": "Abg. Julio Aliaga Cornejo", "bar_number": "42933",
        "specialties": L("Derecho Sanitario", "Consentimiento Informado"),
        "medical_areas": M("Reumatología"),
        "years_experience": 8, "rating": 4.15, "resolved_cases": 10,
        "names_area": False,
        "bio": (
            "Atiende sobre todo casos donde el paciente reclama no haber "
            "entendido de verdad el tratamiento crónico que le indicaron, más allá "
            "de haber firmado el papel correspondiente. Sostiene que buena parte "
            "de estos reclamos se resuelven si el médico documenta la conversación, "
            "no solo la firma. Da seguimiento personal a cada caso hasta el cierre."
        ),
    },
    # ── Resto de relleno para completar el objetivo de 50 y reforzar Media/Alta ──
    {
        "full_name": "Abg. Diana Landa Ochoa", "bar_number": "43015",
        "specialties": L("Responsabilidad Civil Médica", "Derecho Médico"),
        "medical_areas": M("Pediatría", "Medicina General"),
        "years_experience": 21, "rating": 4.80, "resolved_cases": 71,
        "names_area": False,
        "bio": (
            "Es una de las de mayor antigüedad del directorio, con más de dos "
            "décadas de ejercicio. Empezó su carrera en un estudio grande y hace "
            "catorce años se independizó para litigar exclusivamente casos "
            "pediátricos. Su despacho queda a dos cuadras del hospital de niños, y "
            "no es casual."
        ),
    },
    {
        "full_name": "Abg. Elena Marín Bedoya", "bar_number": "43126",
        "specialties": L("Negligencia Médica", "Bioética y Derecho"),
        "medical_areas": M("Cardiología", "Neurología"),
        "years_experience": 15, "rating": 4.60, "resolved_cases": 40,
        "names_area": True,
        "bio": (
            "Se formó como enfermera antes de estudiar Derecho, y todavía usa esa "
            "base clínica para leer un electrocardiograma o una resonancia sin "
            "depender por completo del perito. Divide su cartera entre casos "
            "cardiológicos y neurológicos casi en partes iguales, y es una de las "
            "pocas del gremio que redacta ella misma el resumen técnico de cada "
            "expediente antes de entregarlo al perito externo."
        ),
    },
    {
        "full_name": "Abg. Christian Aguirre Terán", "bar_number": "43219",
        "specialties": L("Derecho Médico", "Seguros Médicos"),
        "medical_areas": M("Anestesiología"),
        "years_experience": 6, "rating": 4.15, "resolved_cases": 7,
        "names_area": False,
        "bio": (
            "Todavía comparte oficina con otros dos abogados jóvenes, pero ya "
            "construyó una relación estable con dos clínicas privadas que lo "
            "llaman de forma recurrente para revisar protocolos quirúrgicos antes "
            "de que ocurra un incidente, no después. Es la parte del trabajo que "
            "más le interesa: prevenir el litigio en vez de ganarlo."
        ),
    },
    {
        "full_name": "Abg. Fiorella Ponce Guevara", "bar_number": "43304",
        "specialties": L("Derecho Sanitario", "Derecho Penal Médico"),
        "medical_areas": M("Ginecología y Obstetricia", "Traumatología"),
        "years_experience": 12, "rating": 4.50, "resolved_cases": 31,
        "names_area": False,
        "bio": (
            "Litigó dos años en provincia antes de trasladarse a Lima, y sigue "
            "recibiendo casos derivados de colegas del norte del país. Sus casos "
            "más frecuentes combinan complicaciones obstétricas con lesiones "
            "traumáticas del parto instrumentado, un cruce poco común que pocos "
            "estudios de la capital manejan con la misma soltura."
        ),
    },
]


def _validate(record: dict) -> None:
    words = record["bio"].split()
    assert 40 <= len(words) <= 80, (
        f"{record['full_name']}: bio de {len(words)} palabras, fuera de 40-80"
    )
    assert record["medical_areas"], f"{record['full_name']} sin medical_areas"
    assert record["specialties"], f"{record['full_name']} sin specialties"


def build() -> list[dict]:
    lawyers = []
    for rec in EXISTING_LAWYERS:
        _validate(rec)
        lawyers.append(rec)
    for i, rec in enumerate(NEW_LAWYERS, start=1):
        _validate(rec)
        rec = dict(rec)
        rec["op"] = "insert"
        rec["lawyer_id"] = f"b4000000-0000-0000-0000-{i:012d}"
        lawyers.append(rec)
    return lawyers


# ═══════════════════════════════════════════════════════════════════════════
# Reporte: distribución, vocabulario (chequeo de degeneración), % área explícita
# ═══════════════════════════════════════════════════════════════════════════

_WORD_RE = re.compile(r"[a-záéíóúñ]+", re.IGNORECASE)


def _tokenize_like_production(text: str) -> list[str]:
    """Misma normalización que app/matching/model.py (_normalize): minúsculas
    + sin tildes, y los mismos SPANISH_STOPWORDS del vectorizador real."""
    normalized = _normalize(text)
    tokens = _WORD_RE.findall(normalized)
    stop = set(SPANISH_STOPWORDS)
    return [t for t in tokens if t not in stop and len(t) > 1]


def report(lawyers: list[dict]) -> None:
    print(f"\n=== Corpus generado: {len(lawyers)} abogados ===\n")

    # (2) Distribución por área médica vs objetivo
    area_counts = Counter()
    for l in lawyers:
        for a in l["medical_areas"]:
            area_counts[a] += 1
    print("--- Distribución por área médica (real vs objetivo) ---")
    for area, (lo, hi) in TARGET_COVERAGE.items():
        real = area_counts.get(area, 0)
        flag = "OK" if lo <= real <= hi else ("BAJO" if real < lo else "ALTO")
        print(f"  {area:28s} real={real:2d}  objetivo=[{lo}-{hi}]  {flag}")

    # Distribución por etiqueta legal
    legal_counts = Counter()
    for l in lawyers:
        for s in l["specialties"]:
            legal_counts[s] += 1
    print("\n--- Distribución por etiqueta legal (specialties[]) ---")
    n = len(lawyers)
    for label in LEGAL_SPECIALTIES:
        c = legal_counts.get(label, 0)
        print(f"  {label:32s} {c:2d}  ({c / n * 100:4.1f}% de los perfiles)")

    # (3) Estadísticas de vocabulario — chequeo de degeneración TF-IDF
    lengths = [len(l["bio"].split()) for l in lawyers]
    mean_len = sum(lengths) / len(lengths)
    var = sum((x - mean_len) ** 2 for x in lengths) / len(lengths)
    std_len = var ** 0.5

    all_tokens: list[str] = []
    doc_token_sets: list[set] = []
    for l in lawyers:
        # igual que _lawyer_text en producción: specialties + medical_areas + bio
        full_text = " ".join(l["specialties"]) + " " + " ".join(l["medical_areas"]) + " " + l["bio"]
        toks = _tokenize_like_production(full_text)
        all_tokens.extend(toks)
        doc_token_sets.append(set(toks))

    vocab = set(all_tokens)
    freq = Counter(all_tokens)
    top20 = freq.most_common(20)
    # en cuántos documentos distintos aparece cada uno de los top-20 (document frequency)
    df_top20 = [(term, sum(1 for s in doc_token_sets if term in s)) for term, _ in top20]

    print("\n--- Estadísticas de vocabulario (texto vectorizado: specialties+medical_areas+bio) ---")
    print(f"  Términos únicos tras stopwords:  {len(vocab)}")
    print(f"  Longitud media de bio:            {mean_len:.1f} palabras (desv.est.={std_len:.1f})")
    print(f"  Longitud mín/máx:                 {min(lengths)}/{max(lengths)}")
    print("  Top 20 términos más frecuentes (término, frecuencia total, en cuántos de "
          f"{len(lawyers)} documentos aparece):")
    for (term, freq_count), (_, doc_count) in zip(top20, df_top20):
        pct_docs = doc_count / len(lawyers) * 100
        print(f"    {term:20s} freq={freq_count:3d}  presente en {doc_count:2d} perfiles ({pct_docs:4.1f}%)")

    max_doc_pct = max(dc for _, dc in df_top20) / len(lawyers) * 100
    print(f"\n  Chequeo de degeneración: el término más común del top-20 aparece en "
          f"{max_doc_pct:.1f}% de los perfiles.")
    if max_doc_pct > 60:
        print("  ALERTA: por encima de 60% — riesgo real de matriz TF-IDF degenerada, "
              "revisar antes de continuar.")
    else:
        print("  Por debajo del 60% — no hay un término dominando el corpus.")

    # (4) % de bios que nombran su área médica explícitamente
    named = sum(1 for l in lawyers if l.get("names_area"))
    print(f"\n--- Mención explícita del área médica en la bio ---")
    print(f"  {named}/{len(lawyers)} perfiles ({named / len(lawyers) * 100:.1f}%) "
          "nombran su área médica en el texto libre; el resto la deja implícita "
          "(terminología clínica, tipo de procedimiento) o solo en medical_areas[].")

    # Verificación cruzada: ¿alguna bio marcada como "no nombra el área" en
    # realidad la contiene como substring? (detector honesto, no writes silencioso)
    mismatches = []
    for l in lawyers:
        bio_norm = _normalize(l["bio"])
        for area in l["medical_areas"]:
            if _normalize(area) in bio_norm and not l.get("names_area"):
                mismatches.append((l["full_name"], area))
    if mismatches:
        print("\n  AVISO: estas bios están marcadas como 'no nombra el área' pero el "
              "área aparece como substring del texto — revisar manualmente:")
        for name, area in mismatches:
            print(f"    {name}: '{area}'")
    else:
        print("  Verificación cruzada OK: ninguna bio marcada 'no nombra el área' "
              "contiene el nombre del área como substring.")

    # Pares difíciles
    pairs = Counter(r.get("pair") for r in NEW_LAWYERS if r.get("pair"))
    print(f"\n--- Pares deliberadamente difíciles ---")
    print(f"  {len(pairs)} pares ({sum(pairs.values())} perfiles) con bio casi idéntica "
          "y area/especialidad distinta.")


V12_PATH = Path(__file__).resolve().parents[2] / "backend" / "src" / "main" / "resources" \
    / "db" / "migration" / "V12__seed_lawyers.sql"

_BCRYPT_FORMAT = re.compile(r"^\$2[aby]\$\d{2}\$[./A-Za-z0-9]{53}$")


def _generate_unauthenticable_hash() -> str:
    """Hash bcrypt de una contraseña aleatoria de un solo uso, descartada de
    inmediato. El secreto en si NUNCA se retorna, se imprime ni se guarda --
    solo existe en esta funcion mientras se hashea, y muere con el scope.

    Se autoverifica antes de devolver el hash: (1) formato bcrypt valido
    ($2a/2b/2y + costo + 53 chars de sal+hash), para que AuthService la
    rechace por credenciales invalidas y no por una excepcion de parseo;
    (2) bcrypt.checkpw() confirma que el hash es funcional; (3) confirma que
    NO coincide con la contraseña demo compartida (Demo123!), para no
    reintroducir sin darse cuenta la misma debilidad que se esta corrigiendo.
    """
    secret = secrets.token_urlsafe(32)  # ~43 chars de entropia, un solo uso
    hashed = bcrypt.hashpw(secret.encode(), bcrypt.gensalt()).decode()

    assert _BCRYPT_FORMAT.match(hashed), f"Hash con formato bcrypt invalido: {hashed!r}"
    assert bcrypt.checkpw(secret.encode(), hashed.encode()), "El hash generado no verifica contra su propio secreto"
    assert not bcrypt.checkpw(b"Demo123!", hashed.encode()), "Colision con la contraseña demo compartida"

    del secret  # fuera de scope de todas formas al retornar; explicito por claridad
    return hashed


def _sql_str(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def _sql_array(values: list[str]) -> str:
    return "ARRAY[" + ", ".join(_sql_str(v) for v in values) + "]"


_SURNAME_PARTICLES = {"del", "de", "la", "las", "los", "van", "von"}


def _slug_email(full_name: str) -> str:
    # "Abg. Rocío Ochoa Manrique" -> "rocio.ochoa"
    # "Abg. Marco Del Águila Rosales" -> "marco.delaguila" (apellido paterno
    # compuesto: la particula 'del' se funde con el siguiente token, si no,
    # el email trunca en la particula sola -> "marco.del@...").
    parts = full_name.replace("Abg.", "").replace("Dra.", "").replace("Dr.", "").split()
    norm = [unicodedata.normalize("NFKD", p).encode("ascii", "ignore").decode().lower() for p in parts]
    if len(norm) < 2:
        return f"{norm[0]}@sinapsistencia.pe"
    given = norm[0]
    surname_tokens = [norm[1]]
    idx = 2
    while surname_tokens[-1] in _SURNAME_PARTICLES and idx < len(norm):
        surname_tokens.append(norm[idx])
        idx += 1
    surname = "".join(surname_tokens)
    return f"{given}.{surname}@sinapsistencia.pe"


def build_v12_sql(lawyers: list[dict]) -> str:
    lines = [
        "-- ============================================================================",
        "-- Sinapsistencia -- V12: Corpus DS-03 de perfiles de abogados",
        "--",
        "-- Generado por ml-service/evaluation/build_corpus.py -- NO editar a mano.",
        "-- Preserva los 12 perfiles ya sembrados (UPDATE, id/nombre intactos) y agrega",
        "-- perfiles nuevos (INSERT) hasta completar el corpus DS-03 (docs/MATCHING-SPEC.md).",
        "--",
        "-- NO incluye columnas de carga de trabajo (current_caseload/max_caseload):",
        "-- decision documentada -- ninguna variante de ablacion las consume y no existe",
        "-- fuente de verdad en produccion para max_caseload.",
        "--",
        "-- Los 33 perfiles nuevos quedan is_active=TRUE (candidatos plenos del matching",
        "-- en vivo, igual que en la evaluacion offline) pero NO autenticables: cada uno",
        "-- tiene un password_hash bcrypt de una contraseña aleatoria de un solo uso,",
        "-- generada y descartada en build_corpus.py. Ninguna se imprime ni se guarda.",
        "--",
        "-- Detalle completo de estas decisiones: docs/datasheet-corpus-ds03.md",
        "-- ============================================================================",
        "",
    ]

    updates = [l for l in lawyers if l.get("op") == "update"]
    inserts_new = [l for l in lawyers if l.get("op") == "insert"]

    lines.append("-- ── UPDATE: 12 perfiles anclados (11 por FK + Lucia Fernandez, que SI tenia fila")
    lines.append("-- desde V3 -- error de investigacion corregido, ver comentario en build_corpus.py) ──")
    lines.append("-- bio siempre se reescribe. medical_areas se fija explicitamente aunque el valor no")
    lines.append("-- cambie (verificado campo a campo contra V3/V4/V5): asi el UPDATE queda auto-documentado")
    lines.append("-- y no depende de que 'ya coincida' con lo que habia antes. specialties se preserva")
    lines.append("-- salvo dos correcciones puntuales de valores invalidos (Diego Huaman y Lucia")
    lines.append("-- Fernandez tenian 'Responsabilidad Civil Profesional', que no es uno de los 8")
    lines.append("-- valores de LEGAL_SPECIALTIES; Lucia ademas tenia 'Medicina de Emergencia' en")
    lines.append("-- medical_areas, que no es una de las 20 de baselines.py).")
    for l in updates:
        lines.append(
            f"UPDATE lawyer_profiles SET\n"
            f"    specialties = {_sql_array(l['specialties'])},\n"
            f"    medical_areas = {_sql_array(l['medical_areas'])},\n"
            f"    bio = {_sql_str(l['bio'])}\n"
            f"    WHERE user_id = {_sql_str(l['lawyer_id'])};  -- {l['full_name']}"
        )
    lines.append("")

    lines.append(f"-- ── INSERT: {len(inserts_new)} perfiles nuevos del corpus DS-03 ──")
    lines.append("-- is_active=TRUE: deben ser candidatos PLENOS del matching en vivo, igual que")
    lines.append("-- en la evaluacion offline (excluirlos con is_active=FALSE reintroduce la misma")
    lines.append("-- divergencia evaluacion/produccion que la Correccion 2 evito a nivel de campos,")
    lines.append("-- aqui a nivel de 33 perfiles completos). La cuenta queda no-autenticable porque")
    lines.append("-- cada password_hash es un bcrypt de una contraseña aleatoria de un solo uso,")
    lines.append("-- generada y descartada en build_corpus.py -- nunca impresa ni guardada en ningun")
    lines.append("-- lado. Ver docs/datasheet-corpus-ds03.md.")
    for i, l in enumerate(inserts_new, start=1):
        email = _slug_email(l["full_name"])
        phone = f"+51 9{20 + (i % 80):02d} {100 + i:03d} {200 + i:03d}"
        password_hash = _generate_unauthenticable_hash()
        lines.append(
            f"INSERT INTO profiles (id, email, name, role, is_active, password_hash) VALUES\n"
            f"    ({_sql_str(l['lawyer_id'])}, {_sql_str(email)}, {_sql_str(l['full_name'])}, "
            f"'lawyer', TRUE, {_sql_str(password_hash)});"
        )
        lines.append(
            "INSERT INTO lawyer_profiles (user_id, cab, specialties, medical_areas, "
            "years_experience, rating, resolved_cases, available, phone, bio) VALUES\n"
            f"    ({_sql_str(l['lawyer_id'])}, {_sql_str(l['bar_number'])}, "
            f"{_sql_array(l['specialties'])}, {_sql_array(l['medical_areas'])},\n"
            f"     {l['years_experience']}, {l['rating']}, {l['resolved_cases']}, TRUE, "
            f"{_sql_str(phone)}, {_sql_str(l['bio'])});"
        )
        lines.append("")

    return "\n".join(lines)


def main() -> None:
    lawyers = build()
    report(lawyers)

    out_records = []
    for l in lawyers:
        out_records.append({
            "lawyer_id": l["lawyer_id"],
            "full_name": l["full_name"],
            "bar_number": l["bar_number"],
            "specialties": l["specialties"],
            "medical_areas": l["medical_areas"],
            "years_experience": l["years_experience"],
            "rating": l["rating"],
            "resolved_cases": l["resolved_cases"],
            "biography": l["bio"],
        })

    OUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    OUT_PATH.write_text(
        json.dumps(out_records, indent=2, ensure_ascii=False), encoding="utf-8"
    )
    print(f"\nEscrito: {OUT_PATH.resolve()}  ({len(out_records)} perfiles)")

    sql = build_v12_sql(lawyers)
    V12_PATH.parent.mkdir(parents=True, exist_ok=True)
    V12_PATH.write_text(sql, encoding="utf-8")
    print(f"Escrito (SIN APLICAR): {V12_PATH.resolve()}")


if __name__ == "__main__":
    main()
