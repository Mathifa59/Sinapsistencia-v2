package pe.sinapsistencia.profile.domain;

import java.math.BigDecimal;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import pe.sinapsistencia.auth.domain.Profile;

/** Perfil profesional del abogado. `available` soporta el toggle de disponibilidad (HU-40). */
@Entity
@Table(name = "lawyer_profiles")
public class LawyerProfile {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, unique = true)
	private Profile user;

	@Column(nullable = false, length = 20)
	private String cab;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(nullable = false, columnDefinition = "text[]")
	private List<String> specialties = new ArrayList<>();

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "medical_areas", nullable = false, columnDefinition = "text[]")
	private List<String> medicalAreas = new ArrayList<>();

	@Column(name = "years_experience", nullable = false)
	private int yearsExperience;

	@Column(nullable = false, precision = 3, scale = 2)
	private BigDecimal rating = BigDecimal.ZERO;

	@Column(name = "resolved_cases", nullable = false)
	private int resolvedCases;

	@Column(nullable = false)
	private boolean available = true;

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

	protected LawyerProfile() {
	}

	public LawyerProfile(Profile user, String cab) {
		this.user = user;
		this.cab = cab;
	}

	public UUID getId() {
		return id;
	}

	public Profile getUser() {
		return user;
	}

	public String getCab() {
		return cab;
	}

	public void setCab(String cab) {
		this.cab = cab;
	}

	public List<String> getSpecialties() {
		return specialties;
	}

	public void setSpecialties(List<String> specialties) {
		this.specialties = specialties;
	}

	public List<String> getMedicalAreas() {
		return medicalAreas;
	}

	public void setMedicalAreas(List<String> medicalAreas) {
		this.medicalAreas = medicalAreas;
	}

	public int getYearsExperience() {
		return yearsExperience;
	}

	public void setYearsExperience(int yearsExperience) {
		this.yearsExperience = yearsExperience;
	}

	public BigDecimal getRating() {
		return rating;
	}

	public void setRating(BigDecimal rating) {
		this.rating = rating;
	}

	public int getResolvedCases() {
		return resolvedCases;
	}

	public void setResolvedCases(int resolvedCases) {
		this.resolvedCases = resolvedCases;
	}

	public boolean isAvailable() {
		return available;
	}

	public void setAvailable(boolean available) {
		this.available = available;
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
