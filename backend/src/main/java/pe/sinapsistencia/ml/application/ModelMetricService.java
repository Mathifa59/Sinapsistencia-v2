package pe.sinapsistencia.ml.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.sinapsistencia.ml.infrastructure.ModelMetricRepository;
import pe.sinapsistencia.ml.web.dto.ModelMetricDto;

/** Consulta de métricas del modelo ML (HU-35). */
@Service
public class ModelMetricService {

	private final ModelMetricRepository metricRepository;

	public ModelMetricService(ModelMetricRepository metricRepository) {
		this.metricRepository = metricRepository;
	}

	@Transactional(readOnly = true)
	public List<ModelMetricDto> list() {
		return metricRepository.findAllByOrderByEvaluatedAtDesc().stream()
				.map(ModelMetricDto::from)
				.toList();
	}
}
