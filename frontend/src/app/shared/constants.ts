/**
 * Constantes de UI portadas del legacy (labels en español exactos).
 * Cambio académico: CASE_STATUS_LABELS usa el flujo de 6 estados (HU-16)
 * y en la UI el "caso" se llama "consulta".
 */

export type UserRole = 'doctor' | 'lawyer' | 'admin';
export type CaseStatus = 'pendiente' | 'clasificada' | 'asignada' | 'en_revision' | 'respondida' | 'cerrada';
export type CasePriority = 'baja' | 'media' | 'alta' | 'critica';
export type DocumentStatus = 'borrador' | 'pendiente_firma' | 'firmado' | 'archivado';
export type DocumentType =
  | 'historia_clinica' | 'consentimiento_informado' | 'informe_medico' | 'receta'
  | 'orden_laboratorio' | 'certificado_medico' | 'documento_legal' | 'otro';
export type ContactRequestStatus = 'pendiente' | 'aceptado' | 'rechazado' | 'cancelado';

export const ROLE_LABELS: Record<UserRole, string> = {
  doctor: 'Médico',
  lawyer: 'Abogado',
  admin: 'Administrador',
};

export const ROLE_PORTAL_LABELS: Record<UserRole, string> = {
  doctor: 'Portal Médico',
  lawyer: 'Portal Legal',
  admin: 'Administración',
};

export const DOCUMENT_TYPE_LABELS: Record<DocumentType, string> = {
  historia_clinica: 'Historia Clínica',
  consentimiento_informado: 'Consentimiento Informado',
  informe_medico: 'Informe Médico',
  receta: 'Receta Médica',
  orden_laboratorio: 'Orden de Laboratorio',
  certificado_medico: 'Certificado Médico',
  documento_legal: 'Documento Legal',
  otro: 'Otro',
};

export const DOCUMENT_STATUS_LABELS: Record<DocumentStatus, string> = {
  borrador: 'Borrador',
  pendiente_firma: 'Pendiente de Firma',
  firmado: 'Firmado',
  archivado: 'Archivado',
};

/** Flujo de 6 estados de la consulta (HU-16). */
export const CASE_STATUS_LABELS: Record<CaseStatus, string> = {
  pendiente: 'Pendiente',
  clasificada: 'Clasificada',
  asignada: 'Asignada',
  en_revision: 'En Revisión',
  respondida: 'Respondida',
  cerrada: 'Cerrada',
};

export const CASE_PRIORITY_LABELS: Record<CasePriority, string> = {
  baja: 'Baja',
  media: 'Media',
  alta: 'Alta',
  critica: 'Crítica',
};

export const CONTACT_REQUEST_STATUS_LABELS: Record<ContactRequestStatus, string> = {
  pendiente: 'Pendiente',
  aceptado: 'Aceptado',
  rechazado: 'Rechazado',
  cancelado: 'Cancelado',
};

export const MEDICAL_SPECIALTIES = [
  'Medicina General', 'Cirugía General', 'Cardiología', 'Neurología', 'Oncología',
  'Pediatría', 'Ginecología y Obstetricia', 'Traumatología', 'Oftalmología',
  'Dermatología', 'Psiquiatría', 'Urología', 'Gastroenterología', 'Endocrinología',
  'Reumatología', 'Neumología', 'Nefrología', 'Infectología', 'Hematología',
  'Anestesiología',
];

export const LEGAL_SPECIALTIES = [
  'Derecho Médico', 'Responsabilidad Civil Médica', 'Derecho Penal Médico',
  'Bioética y Derecho', 'Seguros Médicos', 'Derecho Sanitario',
  'Negligencia Médica', 'Consentimiento Informado',
];

export interface NavigationItem {
  label: string;
  href: string;
  icon: string;
}

export const NAVIGATION_DOCTOR: NavigationItem[] = [
  { label: 'Dashboard', href: '/doctor/dashboard', icon: 'LayoutDashboard' },
  { label: 'Mis Consultas', href: '/doctor/cases', icon: 'Briefcase' },
  { label: 'Documentos', href: '/doctor/documents', icon: 'FileText' },
  { label: 'Abogados', href: '/doctor/lawyers', icon: 'Scale' },
  { label: 'Evaluación de Riesgo', href: '/doctor/risk', icon: 'AlertTriangle' },
  { label: 'Mi Perfil', href: '/doctor/profile', icon: 'User' },
];

export const NAVIGATION_LAWYER: NavigationItem[] = [
  { label: 'Dashboard', href: '/lawyer/dashboard', icon: 'LayoutDashboard' },
  { label: 'Solicitudes', href: '/lawyer/requests', icon: 'Bell' },
  { label: 'Médicos', href: '/lawyer/doctors', icon: 'Stethoscope' },
  { label: 'Mi Perfil', href: '/lawyer/profile', icon: 'User' },
];

/**
 * Nav admin del modelo académico: sin Pacientes/Episodios (datos identificables
 * fuera del Charter); en su lugar Métricas ML (HU-35/38).
 */
export const NAVIGATION_ADMIN: NavigationItem[] = [
  { label: 'Dashboard', href: '/admin/dashboard', icon: 'LayoutDashboard' },
  { label: 'Usuarios', href: '/admin/users', icon: 'Users' },
  { label: 'Documentos', href: '/admin/documents', icon: 'FolderOpen' },
  { label: 'Métricas ML', href: '/admin/metrics', icon: 'Activity' },
  { label: 'Auditoría', href: '/admin/audit', icon: 'ShieldCheck' },
];

export type AuditAction = 'login' | 'logout' | 'create' | 'update' | 'delete' | 'view' | 'sign' | 'download' | 'share';

export const AUDIT_ACTION_LABELS: Record<AuditAction, string> = {
  login: 'Inicio de sesión',
  logout: 'Cierre de sesión',
  create: 'Registro creado',
  update: 'Registro actualizado',
  delete: 'Registro eliminado',
  view: 'Consulta',
  sign: 'Firma',
  download: 'Descarga',
  share: 'Compartido',
};

export type BadgeVariant = 'default' | 'secondary' | 'destructive' | 'outline' | 'success' | 'warning' | 'info';

export const AUDIT_ACTION_BADGE_VARIANT: Record<AuditAction, BadgeVariant> = {
  login: 'success',
  logout: 'secondary',
  create: 'info',
  update: 'warning',
  delete: 'destructive',
  view: 'outline',
  sign: 'success',
  download: 'secondary',
  share: 'info',
};

export const ROLE_BADGE_VARIANT: Record<UserRole, BadgeVariant> = {
  doctor: 'info',
  lawyer: 'secondary',
  admin: 'warning',
};

export const CASE_STATUS_BADGE_VARIANT: Record<CaseStatus, BadgeVariant> = {
  pendiente: 'secondary',
  clasificada: 'info',
  asignada: 'info',
  en_revision: 'warning',
  respondida: 'success',
  cerrada: 'outline',
};

export const CASE_PRIORITY_BADGE_VARIANT: Record<CasePriority, BadgeVariant> = {
  baja: 'secondary',
  media: 'info',
  alta: 'warning',
  critica: 'destructive',
};

export const DOCUMENT_STATUS_BADGE_VARIANT: Record<DocumentStatus, BadgeVariant> = {
  borrador: 'secondary',
  pendiente_firma: 'warning',
  firmado: 'success',
  archivado: 'outline',
};

export const ROLE_DASHBOARD: Record<UserRole, string> = {
  doctor: '/doctor/dashboard',
  lawyer: '/lawyer/dashboard',
  admin: '/admin/dashboard',
};
