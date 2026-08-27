# Taxonomía legal — sustento normativo de `LEGAL_SPECIALTIES`

Mapeo inverso: para cada una de las 8 etiquetas que un abogado real ve y elige al
registrarse en la plataforma (`frontend/src/app/shared/constants.ts` →
`LEGAL_SPECIALTIES`), qué norma peruana la sustenta. **La taxonomía normativa no
reemplaza las 8 etiquetas — las documenta.** El corpus DS-03
(`ml-service/evaluation/build_corpus.py`) usa exclusivamente estas 8 etiquetas en
`specialties[]`; el frontend no se modifica.

---

## 1. Las 8 etiquetas y su sustento

### Responsabilidad Civil Médica
- Código Civil (D.L. N.º 295, 1984), Libro VII, Título IX — Responsabilidad
  Extracontractual: art. 1969 (indemnización por dolo o culpa), art. 1970
  (responsabilidad objetiva por actividad riesgosa), art. 1985 (contenido de la
  indemnización: lucro cesante, daño a la persona, daño moral).
- Ley General de Salud (Ley N.º 26842, 1997), art. 15 (consentimiento
  informado) y art. 29 (historia clínica veraz y suficiente) — el
  incumplimiento de estos deberes es la base fáctica típica de la demanda civil.

### Derecho Penal Médico
- Código Penal (D.L. N.º 635, 1991): art. 111 (homicidio culposo) y art. 124
  (lesiones culposas) — los tipos penales aplicados a la negligencia médica en
  Perú cuando hay resultado de muerte o lesión.

### Derecho Sanitario
- Ley General de Salud (Ley N.º 26842, 1997) como marco general del sistema de
  salud.
- Vertiente administrativo-sancionadora: Ley N.º 29344 (2009, creó la SUNASA) +
  Decreto Legislativo N.º 1158 (2013, la fortaleció y renombró a SUSALUD) +
  Reglamento de Infracciones y Sanciones (D.S. N.º 031-2014-SA), que tipifica
  infracciones de IAFAS/IPRESS/UGIPRESS y regula el procedimiento administrativo
  sancionador (PAS).
- Es la etiqueta más amplia de las 8: cubre tanto el marco general de salud
  como el régimen administrativo-sancionador ante SUSALUD.

### Seguros Médicos
- Ley N.º 29946 (2012), Ley del Contrato de Seguro — regula las pólizas de
  responsabilidad civil profesional médica ("malpractice insurance").
- Supletoriamente, Ley N.º 29571 (Código de Protección y Defensa del
  Consumidor) cuando el asegurado tiene calidad de consumidor.

### Consentimiento Informado
- Ley General de Salud (Ley N.º 26842), art. 15 (derecho a que se le comunique
  todo lo necesario para dar consentimiento informado, y a negarse al
  tratamiento) y art. 4 (nadie puede ser sometido a tratamiento sin
  consentimiento, salvo excepciones legales).
- Mapea directamente a `informed_consent`, una de las 7 variables de entrada
  del clasificador de riesgo (`CLAUDE.md` §4.1). Según Ugarte Mostajo (2024)
  —citado en la memoria del proyecto— es el núcleo de la responsabilidad civil
  médica peruana, lo que justifica conservarla como categoría propia y no
  subsumirla en Responsabilidad Civil Médica.

### Negligencia Médica
- **No tiene un cuerpo normativo autónomo.** Es el concepto fáctico de "culpa"
  que activa tanto la vía civil (art. 1969 CC, "por culpa causa un daño")
  como la vía penal (arts. 111/124 CP, "por culpa"). Es transversal a
  Responsabilidad Civil Médica y Derecho Penal Médico, no una tercera rama
  independiente — ver §2.

### Bioética y Derecho
- **Sin correlato normativo directo en el Perú.** No existe una ley peruana
  que codifique "bioética y derecho" como rama jurídica autónoma; es un campo
  interdisciplinario (ética médica + derecho) que se apoya transversalmente en
  los principios de dignidad y autonomía del paciente ya presentes en la Ley
  General de Salud (arts. 4, 6, 15), pero sin estatuto legal propio.
- Es una descripción legítima del mercado legal peruano (así se documentó en
  la decisión de conservarla), no una categoría normativa codificada. Se
  documenta la ausencia de correlato en vez de forzar uno.

### Derecho Médico
- **Sin correlato normativo directo.** Es el término paraguas/genérico del
  mercado legal peruano para "abogado que atiende asuntos médico-legales en
  general" — en la práctica puede incluir cualquiera de las otras 7 ramas. No
  es una categoría jurídica codificada por una norma específica.

---

## 2. Las 8 etiquetas NO son mutuamente excluyentes

Esto importa para la rúbrica de adjudicación de Fase 2: un adjudicador que vea
dos abogados con etiquetas distintas puede estar viendo, en realidad, la misma
área jurídica descrita a dos niveles de especificidad distintos.

| Relación | Detalle |
|---|---|
| **Negligencia Médica** es transversal a **Responsabilidad Civil Médica** y **Derecho Penal Médico** | Comparte el mismo fundamento fáctico ("culpa") que ambas vías procesales; no es una tercera vía independiente, sino el hecho que las activa. |
| **Consentimiento Informado** suele ser un tema **dentro de** casos de Responsabilidad Civil Médica | Se conserva como etiqueta propia por su peso normativo directo (art. 15 LGS) y su mapeo a `informed_consent`, pero en la práctica es frecuentemente una alegación específica dentro de una demanda civil, no una vía procesal separada. |
| **Derecho Médico** y **Bioética y Derecho** son etiquetas genéricas/transversales | Un abogado puede llevar cualquiera de las otras 6 etiquetas y *además* describirse con estas dos, sin contradicción. No delimitan un área de práctica exclusiva. |
| **Derecho Sanitario** se solapa parcialmente con **Derecho Médico** | Ambas tienen un componente regulatorio/administrativo, pero Derecho Sanitario es más específico (marco de salud pública + SUSALUD) mientras que Derecho Médico es el paraguas general del mercado. |

**Implicación para la rúbrica de Fase 2:** el instrumento de adjudicación no
debe pedir al evaluador que elija "la" especialidad correcta como si fueran
categorías excluyentes — varias etiquetas pueden ser simultáneamente correctas
para un mismo abogado. La rúbrica debe anticipar esto explícitamente.

---

## 3. Distribución real en el corpus DS-03 (45 perfiles)

| Etiqueta | Perfiles | % |
|---|---|---|
| Responsabilidad Civil Médica | 16 | 35.6% |
| Derecho Médico | 15 | 33.3% |
| Derecho Sanitario | 15 | 33.3% |
| Negligencia Médica | 14 | 31.1% |
| Bioética y Derecho | 10 | 22.2% |
| Consentimiento Informado | 10 | 22.2% |
| Derecho Penal Médico | 9 | 20.0% |
| Seguros Médicos | 8 | 17.8% |

Suman más de 100% porque cada abogado tiene 1-3 etiquetas (no son excluyentes,
ver §2). Responsabilidad Civil Médica domina como ancla, consistente con ser
la vía real más frecuente en mala praxis médica.
