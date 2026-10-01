-- ============================================================================
-- V15: H-05 -- complejidad del procedimiento como entrada independiente de la
-- urgencia, y fotografia de las entradas efectivamente evaluadas por el RF.
--
-- Antes, CaseClassificationService.deriveComplexity derivaba la complejidad
-- SIEMPRE de la urgencia percibida (alta/critica -> complejidad alta) -- no
-- habia forma de que el medico reportara, por ejemplo, una urgencia critica
-- con un procedimiento de baja complejidad tecnica. Esta migracion agrega las
-- columnas; CaseClassificationService.java (mismo hallazgo) deja de derivar
-- cuando el caso trae complejidad reportada explicitamente.
--
-- Todos los campos quedan NULL-able para no romper filas historicas: un caso
-- o clasificacion antigua sin estos campos sigue siendo legible, y su origen
-- queda marcado como legacy en complexity_source cuando corresponda (lo
-- resuelve el backend al clasificar, no esta migracion).
-- ============================================================================

ALTER TABLE cases
    ADD COLUMN procedure_complexity VARCHAR(20),
    ADD COLUMN complexity_source    VARCHAR(50);

ALTER TABLE cases
    ADD CONSTRAINT chk_cases_procedure_complexity CHECK (
        procedure_complexity IS NULL OR procedure_complexity IN ('baja', 'media', 'alta')
    ),
    ADD CONSTRAINT chk_cases_complexity_source CHECK (
        complexity_source IS NULL OR complexity_source IN ('reported', 'inferred_from_urgency_legacy')
    );

COMMENT ON COLUMN cases.procedure_complexity IS
    'Complejidad del procedimiento reportada por el medico (H-05) -- entrada independiente de perceived_urgency. NULL en casos creados antes de este flujo.';
COMMENT ON COLUMN cases.complexity_source IS
    '''reported'' = el medico la indico explicitamente; ''inferred_from_urgency_legacy'' = derivada de perceived_urgency por compatibilidad con bodies antiguos del API; NULL = caso historico previo a H-05.';

ALTER TABLE ml_classifications
    ADD COLUMN input_snapshot  JSONB,
    ADD COLUMN pipeline_version VARCHAR(100);

COMMENT ON COLUMN ml_classifications.input_snapshot IS
    'Fotografia exacta de las 7 variables enviadas al Random Forest + metadata de evaluacion (complexity_source, evaluatedAt, eventDate, timeZone). NULL en clasificaciones historicas sin fotografia -- no se reconstruye retroactivamente.';
COMMENT ON COLUMN ml_classifications.pipeline_version IS
    'Version del pipeline de INTEGRACION (ej. risk-input-pipeline-v3), distinta de model_version (rf-v2/rules-v1): registra cambios de que se envia al modelo, no reentrenamiento.';

-- Lectura de la ultima clasificacion de un caso / historial ordenado (H-05,
-- GET /api/legal-cases/{id}/classifications). idx_ml_classifications_case_id
-- (V1) solo cubre el filtro por caso, no el orden determinista created_at
-- DESC, id DESC que pide el detalle y el historial.
CREATE INDEX idx_ml_classifications_case_id_created_at_id
    ON ml_classifications (case_id, created_at DESC, id DESC);
