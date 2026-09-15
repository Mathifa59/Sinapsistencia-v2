"""Instrumento de adjudicación para el panel de ABOGADOS (2026-09-14),
derivado del mismo pool de 187 pares únicos que ya usa el panel de médicos
-- NO es una corrida nueva de build_test_collection.py ni un pool nuevo.

Motivo de un instrumento distinto al de médicos
(docs/adjudicacion-definitivo-*.xlsx, 215 filas cada uno): hasta 10 abogados
sin relación previa con el proyecto no tienen la misma disponibilidad que el
panel médico, y el pool completo (215 filas, ~21-32 h medidas en el piloto)
no es razonable pedírselo a alguien sin vínculo con la tesis. En vez de
reducir --n-queries (lo que dejaría consultas enteras sin cobertura), se
seleccionó un SUBCONJUNTO FIJO del pool completo por criterio explícito, no
al azar -- preregistrado 2026-09-14, antes de recibir ningún juicio de este
panel:

  1. Cobertura mínima: las 20 consultas presentes, al menos 1 candidato cada
     una -- ninguna consulta en cero.
  2. Las 5 consultas de especialidad escasa (q16-q20) con más candidatos que
     el resto (4 cada una) -- preservan el caso adverso que el diseño
     original protegía, aunque NO con cobertura completa: cobertura completa
     de las 5 solas (10+10+8+10+11=49) ya casi agota el presupuesto total de
     40-50 pares antes de cubrir las otras 15 consultas. Desviación
     documentada en docs/datasheet-ds04.md §5, con los números exactos.
  3. Los 3 pares "difíciles" del corpus DS-03, con un matiz real encontrado
     al recomputar el pool (no asumido): el Par B (Estefanía Rojas / Gonzalo
     Manrique) NUNCA co-ocurre en el mismo pool de ninguna consulta y NUNCA
     produce intrusión -- es el par "aburrido por diseño"
     (datasheet-ds04.md §2.2). No se puede incluir "el par junto en una
     consulta de intrusión" porque esa consulta no existe. Se incluye cada
     miembro por separado en su propia área correcta (Estefanía en q11,
     Gonzalo en q12) como confirmación, con juicio humano de un panel
     distinto, de que este par no genera ambigüedad -- no repite la prueba
     de los otros dos pares, la valida desde otro ángulo.
     Par A (Rubén Gutiérrez / Karina Sotelo): juntos en q01, su única
     consulta de intrusión real.
     Par C (Pilar Zevallos / Julio Aliaga): intruyen juntos en 6 de 8
     consultas (q04, q06, q11, q14, q16, q20); Julio solo, sin Pilar, en las
     otras 2 (q08, q18).
  4. Total: 49 pares únicos (verificado, tabla completa en el hilo de
     preregistro y en docs/datasheet-ds04.md §5) -- en el borde superior del
     rango 40-50 pedido, por decisión explícita de no recortar la cobertura
     de las escasas.

Candidatos "extra" (no obligatorios) que completan cada cupo por consulta:
criterio explícito, no aleatorio -- el candidato que aparece en más de las
7 variantes pooleadas para esa consulta (señal de que varios métodos de
ranking coinciden en recomendarlo), desempate por lawyer_id ascendente.

Reutiliza de build_instrument.py: write_instrucciones (parametrizado con un
texto distinto), write_adjudicacion, write_registro, YELLOW,
min_duplicate_gap -- ninguna de esas piezas depende de cómo se construyó el
pool, solo de la lista final de filas.

Uso -- panel de 10 copias idénticas (mismo --seed):
    cd ml-service
    for i in 01 02 03 04 05 06 07 08 09 10; do
        python evaluation/build_lawyer_panel.py --copy-id "$i" \
            --out "../docs/adjudicacion-abogados-$i.xlsx"
    done
"""

from __future__ import annotations

import argparse
import json
import sys
from collections import Counter
from pathlib import Path
from random import Random

if sys.stdout.encoding and sys.stdout.encoding.lower() != "utf-8":
    sys.stdout.reconfigure(encoding="utf-8")

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from openpyxl import Workbook  # noqa: E402

from build_test_collection import (  # noqa: E402
    CORPUS_PATH,
    QUERIES,
    RANDOM_STATE,
    SCARCE_AREAS,
    pool_for_query,
)
from build_instrument import (  # noqa: E402
    DEFAULT_JUSTIFICATION_RATE,
    insert_duplicates,
    write_adjudicacion,
    write_instrucciones,
    write_registro,
)

DEFAULT_DUPLICATE_RATE = 0.15  # mismo criterio que el panel médico, para que la medición de
                                # consistencia intra-evaluador sea comparable entre paneles
MIN_TOTAL, MAX_TOTAL = 40, 50  # rango objetivo pedido

# ═══════════════════════════════════════════════════════════════════════════
# Los 6 abogados de los 3 pares difíciles del corpus DS-03
# (ml-service/evaluation/build_corpus.py). IDs verificados contra el corpus
# real, no asumidos por nombre.
# ═══════════════════════════════════════════════════════════════════════════
RUBEN = "b4000000-0000-0000-0000-000000000024"
KARINA = "b4000000-0000-0000-0000-000000000025"
ESTEFANIA = "b4000000-0000-0000-0000-000000000026"
GONZALO = "b4000000-0000-0000-0000-000000000027"
PILAR = "b4000000-0000-0000-0000-000000000028"
JULIO = "b4000000-0000-0000-0000-000000000029"

# ═══════════════════════════════════════════════════════════════════════════
# Objetivo por consulta + miembros obligatorios -- preregistrado 2026-09-14,
# verificado contra el pool real antes de fijarse (no antes). Ver docstring
# del módulo para el razonamiento completo de cada número.
# ═══════════════════════════════════════════════════════════════════════════
TARGET: dict[str, int] = {q["query_id"]: 1 for q in QUERIES}  # cobertura mínima
MANDATORY: dict[str, list[str]] = {q["query_id"]: [] for q in QUERIES}

for _qid in ("q16", "q17", "q18", "q19", "q20"):  # 5 escasas
    TARGET[_qid] = 4
MANDATORY["q16"] += [PILAR, JULIO]
MANDATORY["q18"] += [JULIO]
MANDATORY["q20"] += [PILAR, JULIO]

TARGET["q01"] = 4
MANDATORY["q01"] += [RUBEN, KARINA]

for _qid in ("q04", "q06", "q14"):  # Par C, intrusión, no escasas, sin Par B
    TARGET[_qid] = 3
    MANDATORY[_qid] += [PILAR, JULIO]

TARGET["q11"] = 4  # Par C (Pilar+Julio) + Par B (Estefanía, área correcta)
MANDATORY["q11"] += [PILAR, JULIO, ESTEFANIA]

TARGET["q08"] = 2  # Julio solo -- Pilar no aparece en el pool de esta consulta
MANDATORY["q08"] += [JULIO]

TARGET["q12"] = 2  # Gonzalo, Par B, área correcta -- ver nota del Par B arriba
MANDATORY["q12"] += [GONZALO]

assert set(TARGET) == {q["query_id"] for q in QUERIES}
EXPECTED_TOTAL = 49


def build_selection(lawyers: list[dict]) -> dict[str, list[str]]:
    """Recomputa el pool completo (determinista, mismo RANDOM_STATE que
    siempre) y aplica TARGET/MANDATORY para armar el subconjunto fijo.
    Reejecutar produce el mismo resultado -- no hay aleatoriedad aquí."""
    per_query_union: dict[str, list[str]] = {}
    per_query_variant_count: dict[str, Counter] = {}
    for idx, q in enumerate(QUERIES):
        variant_lists = pool_for_query(q, lawyers, idx)
        union = sorted({lid for ids in variant_lists.values() for lid in ids})
        per_query_union[q["query_id"]] = union
        counts: Counter = Counter()
        for ids in variant_lists.values():
            for lid in ids:
                counts[lid] += 1
        per_query_variant_count[q["query_id"]] = counts

    selection: dict[str, list[str]] = {}
    for q in QUERIES:
        qid = q["query_id"]
        mandatory = list(dict.fromkeys(MANDATORY[qid]))
        for m in mandatory:
            assert m in per_query_union[qid], (
                f"{m} no está en el pool real de {qid} -- el corpus o las consultas cambiaron, "
                f"esta selección preregistrada ya no es válida y debe rehacerse, no ajustarse a mano"
            )
        target = TARGET[qid]
        pool = per_query_union[qid]
        counts = per_query_variant_count[qid]
        remaining = max(0, target - len(mandatory))
        candidates = [lid for lid in pool if lid not in mandatory]
        candidates.sort(key=lambda lid: (-counts[lid], lid))
        selection[qid] = mandatory + candidates[:remaining]

    total = sum(len(v) for v in selection.values())
    assert total == EXPECTED_TOTAL, (
        f"La selección preregistrada esperaba {EXPECTED_TOTAL} pares únicos, salieron {total}. "
        f"El corpus o las consultas cambiaron desde que se fijó este criterio (2026-09-14) -- "
        f"no ajustar TARGET a mano para forzar {EXPECTED_TOTAL}, rehacer la selección."
    )
    assert MIN_TOTAL <= total <= MAX_TOTAL, f"{total} pares está fuera del rango pedido [{MIN_TOTAL}, {MAX_TOTAL}]"
    return selection


def load_doctor_duplicated_pairs(doctor_instrument_path: Path) -> set[tuple[str, str]]:
    """Lee docs/adjudicacion-definitivo-01.xlsx (cualquiera de las 5 copias
    médicas sirve, son idénticas) y devuelve el conjunto de pares
    (case_description, full_name) que quedaron duplicados ahí. Se usa para
    que, donde sea posible, los duplicados del panel de abogados sean LOS
    MISMOS pares que los del panel médico -- pedido explícito del
    investigador, para que la medición de consistencia intra-evaluador sea
    comparable entre paneles, no solo del mismo ~15%."""
    from openpyxl import load_workbook

    wb = load_workbook(doctor_instrument_path, read_only=True, data_only=True)
    ws = wb["Adjudicación"]
    seen: set[tuple[str, str]] = set()
    dups: set[tuple[str, str]] = set()
    for row in ws.iter_rows(min_row=2, values_only=True):
        case, lawyer = row[1], row[4]
        key = (case, lawyer)
        if key in seen:
            dups.add(key)
        else:
            seen.add(key)
    wb.close()
    return dups


def build_rows_from_selection(
    selection: dict[str, list[str]],
    queries: list[dict],
    lawyers: list[dict],
    rng: Random,
    duplicate_rate: float,
    justification_rate: float,
    priority_duplicate_pairs: set[tuple[str, str]] | None = None,
) -> list[dict]:
    """Misma lógica de cegamiento/duplicados/justificación que
    build_instrument.build_rows, pero sobre una selección FIJA en vez de
    recomputar el pool completo por consulta.

    `priority_duplicate_pairs`: pares (case_description, full_name) que se
    prefieren como origen de un duplicado, cuando estén disponibles dentro
    de esta selección -- para que coincidan con los duplicados del panel
    médico donde sea posible (ver load_doctor_duplicated_pairs). Si hay más
    candidatos prioritarios que duplicados a insertar, se elige entre ellos
    (con semilla); si hay menos, se completa con el resto de la selección.
    """
    lawyer_by_id = {l["lawyer_id"]: l for l in lawyers}
    query_by_id = {q["query_id"]: q for q in queries}

    base_rows: list[dict] = []
    for qid, lawyer_ids in selection.items():
        q = query_by_id[qid]
        ids = list(lawyer_ids)
        rng.shuffle(ids)  # cegamiento: orden de candidatos aleatorio, no por score ni por obligatoriedad
        for lid in ids:
            l = lawyer_by_id[lid]
            base_rows.append({
                "case_description": q["case_description"],
                "medical_specialty": q["medical_specialty"],
                "perceived_urgency": q["perceived_urgency"],
                "lawyer_id": lid,
                "full_name": l["full_name"],
                "specialties": ", ".join(l["specialties"]),
                "medical_areas": ", ".join(l["medical_areas"]),
                "years_experience": l["years_experience"],
            })
    rng.shuffle(base_rows)  # cegamiento adicional: no agrupar visiblemente por consulta

    # Duplicados: misma función corregida que usa el panel médico (2026-09-14,
    # ver docs/datasheet-ds04.md §6) -- lee de base_rows, nunca de la lista
    # que crece, y se autoverifica contra el conjunto pretendido de pares.
    rows = insert_duplicates(base_rows, rng, duplicate_rate, priority_duplicate_pairs)

    n_total = len(rows)
    n_justify = round(n_total * justification_rate)
    justify_idx = set(rng.sample(range(n_total), min(n_justify, n_total)))
    for i, row in enumerate(rows):
        row["justify"] = i in justify_idx

    return rows


# ═══════════════════════════════════════════════════════════════════════════
# Hoja de instrucciones -- versión abogados.
#
# Base: borrador del investigador (párrafos de apertura, pregunta única,
# escala 0/1/2, confianza, vía legal como dato dado, "no investigues ni
# consultes"), reproducido tal cual. Se agregan 5 piezas que el borrador no
# cubría pero que el instrumento SÍ necesita para funcionar correctamente --
# marcadas explícitamente, no mezcladas en silencio con el texto original:
#
#   1. Explicación de la columna «¿Justificar?» -- el borrador no la
#      menciona, pero la hoja Adjudicación sí la tiene (protocolo §5.4).
#      Sin esta línea, un abogado vería «Sí» en algunas filas sin saber qué
#      significa.
#   2. Nota sobre duplicados/cegamiento ("verás casos parecidos... no
#      trates de recordar tu respuesta anterior") -- sin ella, un
#      adjudicador que reconoce un duplicado podría copiar su respuesta
#      anterior en vez de responder de nuevo, lo que invalida la medición
#      de consistencia intra-evaluador que es la razón de ser del 15% de
#      duplicados.
#   3. Límite explícito de alcance ("no evalúes si hubo mala praxis ni la
#      gravedad clínica") -- mismo tipo de hallazgo que motivó el ajuste
#      post-piloto en la versión de médicos (§4.2 del protocolo, vía
#      jurídica como dato dado), aplicado por simetría al riesgo opuesto en
#      esta audiencia: un abogado podría inclinarse a evaluar el mérito
#      médico del caso en vez de si el abogado propuesto calza.
#   4. Aviso de datos simulados (Ley 29733) -- ya presente en la versión de
#      médicos, ausente en el borrador.
#   5. Puntero breve a la hoja Registro (tandas + declaración firmada).
# ═══════════════════════════════════════════════════════════════════════════
INSTRUCCIONES_TEXT_LAWYERS: list[tuple[str, str]] = [
    ("title", "Sinapsistencia — Validación de un sistema de recomendación"),
    ("p", "Soy estudiante de Ingeniería de Software y estoy desarrollando, como tesis, una "
          "plataforma que conecta a médicos con abogados especializados en derecho médico. "
          "Para validar si el sistema recomienda bien, necesito el criterio de alguien con tu "
          "experiencia — por eso te escribo."),
    ("p", "Vas a ver una serie de filas. En cada una hay el resumen breve de un caso "
          "médico-legal y el perfil de un abogado. Tu tarea es una sola pregunta por fila:"),
    ("q", "¿Qué tan apropiado es este abogado para atender este caso?"),
    ("scale", "     2  =  Claramente apropiado."),
    ("scale", "     1  =  Parcialmente apropiado."),
    ("scale", "     0  =  No apropiado."),
    ("p", "Además marcas tu confianza: Alta, Media o Baja. Marcar Baja es una respuesta "
          "válida — si el área médica del caso se escapa de tu experiencia, dilo ahí en vez "
          "de forzar una respuesta."),
    ("p", "La vía legal del caso (civil, penal, administrativa, etc.) se toma como un dato ya "
          "dado — no hace falta que evalúes si es la correcta, solo si el abogado propuesto "
          "encaja con lo que el caso plantea."),
    ("p", "No hace falta que investigues nada, ni que consultes el caso con nadie: lo que "
          "necesito es tu primera impresión profesional."),
    ("h", "Cosas importantes"),
    ("bullet", "Cada fila se juzga por separado. No estás comparando abogados entre sí ni "
               "eligiendo al mejor."),
    ("bullet", "No evalúes si hubo mala praxis ni qué tan grave fue el caso clínico. Solo si "
               "el abogado propuesto calza con lo que el caso plantea."),
    ("bullet", "Justificación: solo en las filas donde la columna «¿Justificar?» diga «Sí». "
               "Una frase corta basta."),
    ("bullet", "Verás casos que se parecen entre sí. Es a propósito — no trates de recordar "
               "qué respondiste antes en un caso similar."),
    ("bullet", "No consultes nada externo: ni buscadores, ni herramientas de inteligencia "
               "artificial, ni colegas. Lo que necesito es tu criterio profesional, el que "
               "traes de tu experiencia — no el de otra fuente."),
    ("h", "Cuándo y cómo"),
    ("p", "Puedes hacerlo cuando te acomode y en las tandas que quieras. Solo te pido que "
          "anotes en la hoja «Registro» las fechas y horarios en que trabajaste, y que al "
          "terminar completes la declaración que está al final de esa hoja."),
    ("p", "Los datos de este ejercicio son simulados. No corresponden a pacientes, casos ni "
          "profesionales reales."),
]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--duplicate-rate", type=float, default=DEFAULT_DUPLICATE_RATE)
    parser.add_argument("--justification-rate", type=float, default=DEFAULT_JUSTIFICATION_RATE)
    parser.add_argument("--seed", type=int, default=RANDOM_STATE,
                         help="NUNCA varía entre copias de un mismo panel -- todas las copias "
                              "de abogados deben compartir --seed para ser comparables fila a "
                              "fila (y con la selección fija, siempre producen las mismas 49 "
                              "filas base en el mismo orden entre sí).")
    parser.add_argument("--copy-id", type=str, default=None)
    parser.add_argument("--doctor-instrument", type=Path,
                         default=Path(__file__).resolve().parents[2] / "docs" / "adjudicacion-definitivo-01.xlsx",
                         help="Copia del panel médico de donde se leen sus pares duplicados, para "
                              "priorizarlos como duplicados aquí también (--n-duplicates lo permite). "
                              "Las 5 copias médicas son idénticas, cualquiera sirve.")
    parser.add_argument("--out", type=Path,
                         default=Path(__file__).resolve().parents[1] / "data" / "reference" / "ds04_pool_abogados.xlsx")
    args = parser.parse_args()

    lawyers = json.loads(CORPUS_PATH.read_text(encoding="utf-8"))
    selection = build_selection(lawyers)

    print("=== Selección fija (preregistrada 2026-09-14) ===")
    for q in QUERIES:
        qid = q["query_id"]
        n = len(selection[qid])
        scarce = " [ESCASA]" if q["medical_specialty"] in SCARCE_AREAS else ""
        print(f"  {qid} ({q['medical_specialty']}){scarce}: {n} candidatos")
    total = sum(len(v) for v in selection.values())
    print(f"TOTAL PARES ÚNICOS: {total}")

    priority_pairs: set[tuple[str, str]] = set()
    if args.doctor_instrument.exists():
        doctor_dups = load_doctor_duplicated_pairs(args.doctor_instrument)
        query_by_id = {q["query_id"]: q for q in QUERIES}
        lawyer_by_id = {l["lawyer_id"]: l["full_name"] for l in lawyers}
        selection_pairs = {
            (query_by_id[qid]["case_description"], lawyer_by_id[lid])
            for qid, ids in selection.items() for lid in ids
        }
        priority_pairs = doctor_dups & selection_pairs
        print(f"Pares duplicados en el panel médico que también están en esta selección: "
              f"{len(priority_pairs)} (se priorizan como duplicados aquí)")
    else:
        print(f"⚠ No se encontró {args.doctor_instrument} -- duplicados elegidos sin priorizar "
              f"coincidencia con el panel médico.")

    rng = Random(args.seed)
    rows = build_rows_from_selection(
        selection, QUERIES, lawyers, rng, args.duplicate_rate, args.justification_rate, priority_pairs,
    )
    n_dup = len(rows) - total
    print(f"Filas totales: {len(rows)} (únicos: {total}, duplicados: {n_dup})")
    print(f"Filas con justificación: {sum(1 for r in rows if r['justify'])} "
          f"({sum(1 for r in rows if r['justify']) / len(rows) * 100:.1f}%)")

    wb = Workbook()
    ws_instr = wb.active
    ws_instr.title = "Instrucciones"
    write_instrucciones(ws_instr, INSTRUCCIONES_TEXT_LAWYERS)

    ws_adj = wb.create_sheet("Adjudicación")
    write_adjudicacion(ws_adj, rows)

    ws_reg = wb.create_sheet("Registro")
    write_registro(ws_reg, len(rows), args.copy_id)

    args.out.parent.mkdir(parents=True, exist_ok=True)
    wb.save(args.out)
    copy_note = f" (copia {args.copy_id})" if args.copy_id else ""
    print(f"Escrito{copy_note}: {args.out.resolve()}")


if __name__ == "__main__":
    main()
