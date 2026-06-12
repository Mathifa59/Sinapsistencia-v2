package pe.sinapsistencia.profile.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import pe.sinapsistencia.auth.domain.Profile;

/** Perfil profesional del médico. La columna `embedding` (pgvector) queda sin mapear a propósito. */
@Entity
@Table(name = "doctor_profiles")
public class DoctorProfile {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, unique = true)
	private Profile user;

	@Column(nullable = false, length = 20)
	private String cmp;

	@Column(nullable = false, length = 150)
	private String specialty;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "sub_specialties", nullable = false, columnDefinition = "text[]")
	private List<String> subSpecialties = new ArrayList<>();

	@Column(nullable = false, length = 255)
	private String hospital = "";

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "hospital_id")
	private Hospital hospitalRef;

	@Column(name = "years_experience", nullable = false)
	private int yearsExperience;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(nullable = false, columnDefinition = "text[]")
	private List<String> languages = new ArrayList<>();

	@Column(nullable = false, length = 30)
	private String phone = "";

	@Column
	private String bio;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected DoctorProfile() {
	}

	public DoctorProfile(Profile user, String cmp, String specialty) {
		this.user = user;
		this.cmp = cmp;
		this.specialty = specialty;
	}

	public UUID getId() {
		return id;
	}

	public Profile getUser() {
		return user;
	}

	public String getCmp() {
		return cmp;
	}

	public void setCmp(String cmp) {
		this.cmp = cmp;
	}

	public String getSpecialty() {
		return specialty;
	}

	public void setSpecialty(String specialty) {
		this.specialty = specialty;
	}

	public List<String> getSubSpecialties() {
		return subSpecialties;
	}

	public void setSubSpecialties(List<String> subSpecialties) {
		this.subSpecialties = subSpecialties;
	}

	public String getHospital() {
		return hospital;
	}

	public void setHospital(String hospital) {
		this.hospital = hospital;
	}

	public Hospital getHospitalRef() {
		return hospitalRef;
	}

	public void setHospitalRef(Hospital hospitalRef) {
		this.hospitalRef = hospitalRef;
	}

	public int getYearsExperience() {
		return yearsExperience;
	}

	public void setYearsExperience(int yearsExperience) {
		this.yearsExperience = yearsExperience;
	}

	public List<String> getLanguages() {
		return languages;
	}

	public void setLanguages(List<String> languages) {
		this.languages = languages;
	}

	public String getPhone() {
		return phone;
	}

	public void setPhone(String phone) {
		this.phone = phone;
	}

	public String getBio() {
		return bio;
	}

	public void setBio(String bio) {
		this.bio = bio;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
