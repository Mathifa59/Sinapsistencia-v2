package pe.sinapsistencia.matching.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.sinapsistencia.matching.web.dto.DoctorCardDto;
import pe.sinapsistencia.matching.web.dto.LawyerCardDto;
import pe.sinapsistencia.profile.infrastructure.DoctorProfileRepository;
import pe.sinapsistencia.profile.infrastructure.LawyerProfileRepository;

/** Directorios públicos de médicos y abogados disponibles en la plataforma. */
@Service
public class MatchingDirectoryService {

	private final DoctorProfileRepository doctorProfileRepository;
	private final LawyerProfileRepository lawyerProfileRepository;

	public MatchingDirectoryService(DoctorProfileRepository doctorProfileRepository,
			LawyerProfileRepository lawyerProfileRepository) {
		this.doctorProfileRepository = doctorProfileRepository;
		this.lawyerProfileRepository = lawyerProfileRepository;
	}

	@Transactional(readOnly = true)
	public List<DoctorCardDto> listDoctors() {
		return doctorProfileRepository.findAllByOrderByCreatedAtDesc().stream()
				.map(DoctorCardDto::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<LawyerCardDto> listLawyers() {
		return lawyerProfileRepository.findAllByOrderByRatingDesc().stream()
				.map(LawyerCardDto::from)
				.toList();
	}
}
