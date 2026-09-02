# Qué se vectoriza en el matching TF-IDF — nota técnica para Fase 3

Documenta exactamente qué campos entran al texto que vectoriza
`TfidfVectorizer` en `ml-service/app/matching/model.py`, porque tiene una
consecuencia directa sobre cómo se debe **interpretar** (no diseñar todavía)
el estudio de ablación de `docs/MATCHING-SPEC.md` §5. No es una propuesta de
cambio — es un hallazgo a tener en cuenta antes de diseñar Fase 3.

---

## 1. El documento del abogado no es solo la bio

`_lawyer_text()` (`app/matching/model.py:59-64`):

```python
def _lawyer_text(lawyer: dict) -> str:
    return " ".join([
        " ".join(lawyer.get("specialties", [])),
        " ".join(lawyer.get("medical_areas", [])),
        lawyer.get("bio", ""),
    ])
```

El texto que efectivamente vectoriza TF-IDF por cada abogado es:

```
specialties[] (unidas)  +  medical_areas[] (unidas)  +  bio
```

**No es solo la biografía.** Las etiquetas legales y las áreas médicas —campos
estructurados— entran al vector como texto plano, siempre, sin importar si la
bio los menciona o no.

## 2. El documento del médico tampoco es solo el texto del caso

`_doctor_text()` (`app/matching/model.py:67-73`):

```python
def _doctor_text(profile: DoctorProfile) -> str:
    return " ".join([
        profile.specialty or "",
        " ".join(profile.sub_specialties or []),
        profile.hospital or "",
        profile.case_text or "",
    ])
```

Del lado de la consulta pasa lo mismo: `specialty` (la especialidad médica del
caso) entra literal al vector de la consulta, además del texto libre
(`case_text`, construido en `RecommendationService.buildCaseText()` con
título + descripción + tipo de evento + especialidad — la especialidad
aparece **dos veces**: una como campo estructurado y otra dentro del texto
libre).

**Consecuencia para la interpretación de Fase 3.** Esta duplicación del
lado de la consulta no es un problema aislado: se combina con lo ya
documentado en §1 —que `medical_areas[]` del abogado entra al texto del
abogado como campo estructurado, con o sin que la bio lo mencione—. El
resultado es que `tfidf-full` está **más dominado por la coincidencia de
área** de lo que se estimó al diseñar la ablación original: no es solo que
el lado del abogado inyecta el área como texto plano (§1/§3), es que el
lado de la consulta *también* la inyecta duplicada (`specialty` como campo
estructurado, más `specialty` otra vez dentro de `case_text` vía
`buildCaseText()`). El coseno entre ambos vectores termina comparando, en
la práctica, un área contada casi dos veces del lado de la consulta contra
un área contada una vez del lado del abogado — un sesgo compuesto por dos
fuentes, no por una sola.

Esto refuerza que `bio-only` (§5) es la **única** variante que permite
comparar de forma limpia contra `area-match`: es la única que elimina por
completo tanto `medical_areas[]` del abogado como la duplicación de
`specialty` del lado de la consulta. Y anticipa, antes de correr Fase 3,
que la diferencia observada entre `tfidf-full` y `area-match` va a
**subestimar el aporte real de esta última más de lo que estimaba §3**
al considerar solo el lado del abogado — con dos fuentes de duplicación en
vez de una, la magnitud de la subestimación probablemente sea mayor de lo
que cualquiera de los dos lados del pipeline explicaría por separado.

## 3. Consecuencia para la ablación (§5 del spec)

Esto es lo que explica por qué "derecho" aparece en el 80% de los perfiles
del corpus DS-03 (viene de `specialties[]`, no de las bios) y por qué el
chequeo de IDF real (§ ver `ESTADO_ACTUAL_PROYECTO.md`/informe de Fase 1) lo
neutraliza correctamente para ese término genérico.

Pero para el área médica el efecto es distinto y más delicado: **el
componente textual (`tfidf-full`) ya contiene la señal de coincidencia de
área**, porque `medical_areas[]` del abogado y `specialty` del médico se
inyectan directamente al texto que compara el coseno — con o sin que la bio
nombre el área explícitamente. Es el mismo problema de correlación que se
atacó diseñando que solo el 28.9% de las bios nombren el área explícitamente
(`docs/taxonomia-legal.md` / informe de Fase 1), pero un nivel más arriba: ese
diseño reduce la correlación **dentro de la bio**, no la correlación que
introduce la concatenación de los campos estructurados en el propio pipeline
de vectorización.

**Implicación concreta:** al comparar `tfidf-full` contra `area-match` en el
estudio de ablación, la diferencia observada entre ambas variantes va a
**subestimar** el aporte real de `area-match`, porque `tfidf-full` no es un
componente textual "puro" — ya lleva una porción de la señal de área
mezclada por diseño del pipeline actual, no por accidente del corpus.

## 4. Qué NO se hace en este documento

No se propone ni se aplica ningún cambio a `app/matching/model.py`,
`_lawyer_text()`, `_doctor_text()` ni al esquema de vectorización.

## 5. Enmienda a Fase 3 — octava variante de ablación: `bio-only`

Consecuencia directa de §3: como `tfidf-full` (tal como está definida hoy en
`docs/MATCHING-SPEC.md` §5) vectoriza `specialties + medical_areas + bio`, ya
contiene la señal de coincidencia de área — no es un componente textual
"puro" con el que comparar `area-match` de forma separable.

Se agrega una **octava variante** al estudio de ablación:

| Variante | Descripción | Qué demuestra |
|---|---|---|
| `bio-only` | TF-IDF + coseno vectorizando **únicamente** el campo `bio`, sin concatenar `specialties[]` ni `medical_areas[]` | El aporte real del texto libre, aislado de la coincidencia de área que ya cargan los campos estructurados — es la única variante que permite comparar de forma limpia contra `area-match` |

Con esto, la comparación relevante para el argumento de tesis pasa a ser
`bio-only` vs. `area-match` (aporte textual puro vs. coincidencia
estructurada), y `tfidf-full` (como está implementada en producción hoy)
queda documentada como una variante que mezcla ambas señales por diseño del
pipeline — no por accidente del corpus.

Esta variante requiere una función de vectorización separada para la
evaluación offline (no toca `app/matching/model.py` en producción): se
implementa en `ml-service/evaluation/run_ablation.py` cuando se construya esa
Fase 3.
