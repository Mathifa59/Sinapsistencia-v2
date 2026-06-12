package pe.sinapsistencia.matching.infrastructure;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import pe.sinapsistencia.matching.domain.ContactRequest;
import pe.sinapsistencia.matching.domain.ContactRequestStatus;

@Repository
public interface ContactRequestRepository
		extends JpaRepository<ContactRequest, UUID>, JpaSpecificationExecutor<ContactRequest> {

	@Override
	@EntityGraph(attributePaths = { "fromDoctor", "toLawyer", "legalCase" })
	List<ContactRequest> findAll(Specification<ContactRequest> spec, Sort sort);

	boolean existsByFromDoctorIdAndToLawyerIdAndStatus(UUID fromDoctorId, UUID toLawyerId,
			ContactRequestStatus status);
}
