package pe.sinapsistencia.profile.infrastructure;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import pe.sinapsistencia.profile.domain.Hospital;

@Repository
public interface HospitalRepository extends JpaRepository<Hospital, UUID> {
}
