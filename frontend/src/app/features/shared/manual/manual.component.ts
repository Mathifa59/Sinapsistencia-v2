import { Component, computed, inject } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';
import { AuthService } from '../../../core/auth/auth.service';
import { ROLE_LABELS, ROLE_PORTAL_LABELS, type UserRole } from '../../../shared/constants';

interface ManualSection {
  title: string;
  icon: string;
  items: string[];
}

const DOCTOR_SECTIONS: ManualSection[] = [
  {
    title: 'Casos clínico-legales',
    icon: 'briefcase',
    items: [
      'Registra nuevos casos desde Mis Casos con contexto simulado (Ley 29733).',
      'Edita casos en estado pendiente y ciérralos cuando recibas respuesta legal.',
      'Consulta la clasificación ML, justificación de prioridad y línea de tiempo.',
      'Genera informes imprimibles y marca las respuestas legales como revisadas.',
    ],
  },
  {
    title: 'Documentos y abogados',
    icon: 'file-text',
    items: [
      'Gestiona documentos clínicos y adjúntalos a casos.',
      'Busca abogados sugeridos por el sistema y envía solicitudes de contacto.',
      'Las recomendaciones ML son orientativas; la decisión final es humana.',
    ],
  },
  {
    title: 'Evaluación de riesgo',
    icon: 'alert-triangle',
    items: [
      'Usa la herramienta de evaluación de riesgo médico-legal antes de escalar un caso.',
      'Revisa el desglose por factor y las recomendaciones de mitigación.',
    ],
  },
];

const LAWYER_SECTIONS: ManualSection[] = [
  {
    title: 'Casos asignados',
    icon: 'briefcase',
    items: [
      'Revisa tus casos ordenados por prioridad (crítica primero).',
      'Filtra por estado y prioridad para gestionar tu carga de trabajo.',
      'Redacta respuestas legales con recomendaciones y observaciones.',
    ],
  },
  {
    title: 'Solicitudes de contacto',
    icon: 'bell',
    items: [
      'Acepta o rechaza solicitudes de médicos; al rechazar indica el motivo.',
      'Configura especialidades legales y áreas médicas en tu perfil.',
    ],
  },
  {
    title: 'Buenas prácticas',
    icon: 'scale',
    items: [
      'Toda respuesta legal debe ser revisada por el médico solicitante.',
      'El sistema no sustituye el criterio profesional ni la relación abogado-cliente.',
    ],
  },
];

const ADMIN_SECTIONS: ManualSection[] = [
  {
    title: 'Administración del sistema',
    icon: 'shield-check',
    items: [
      'Gestiona usuarios, documentos y revisa la auditoría de acciones.',
      'Monitorea métricas del modelo ML: precisión, recall y F1.',
      'Verifica el estado de salud del servicio de Machine Learning.',
    ],
  },
  {
    title: 'Métricas y calidad',
    icon: 'activity',
    items: [
      'Consulta la distribución de casos por estado, prioridad y especialidad.',
      'Evalúa el rendimiento del clasificador y del motor de matching.',
    ],
  },
  {
    title: 'Cumplimiento',
    icon: 'book-open',
    items: [
      'El sistema opera con datos simulados conforme a la Ley 29733.',
      'No se almacenan datos identificables de pacientes en el MVP.',
    ],
  },
];

const SECTIONS_BY_ROLE: Record<UserRole, ManualSection[]> = {
  doctor: DOCTOR_SECTIONS,
  lawyer: LAWYER_SECTIONS,
  admin: ADMIN_SECTIONS,
};

/** HU-44: manual de usuario según rol. */
@Component({
  selector: 'app-manual',
  imports: [LucideAngularModule],
  template: `
    <div class="max-w-3xl mx-auto space-y-6">
      <div>
        <h1 class="text-2xl font-bold text-slate-900">Manual de usuario</h1>
        <p class="text-slate-500 text-sm mt-1">{{ portalLabel() }} — guía de funcionalidades</p>
      </div>

      <div class="bg-white rounded-xl border border-slate-200 p-5">
        <p class="text-sm text-slate-600 leading-relaxed">
          Bienvenido a <strong>Sinapsistencia</strong>, plataforma de mediación médico-legal.
          Este manual describe las funciones disponibles para tu rol de <strong>{{ roleLabel() }}</strong>.
        </p>
      </div>

      @for (section of sections(); track section.title) {
        <div class="bg-white rounded-xl border border-slate-200 overflow-hidden">
          <div class="px-5 py-4 border-b border-slate-100 flex items-center gap-2">
            <lucide-icon [name]="section.icon" class="h-5 w-5 text-slate-600" />
            <h2 class="font-semibold text-slate-900">{{ section.title }}</h2>
          </div>
          <ul class="px-5 py-4 space-y-2.5">
            @for (item of section.items; track item) {
              <li class="flex items-start gap-2.5 text-sm text-slate-600">
                <lucide-icon name="chevron-right" class="h-4 w-4 text-blue-500 mt-0.5 shrink-0" />
                {{ item }}
              </li>
            }
          </ul>
        </div>
      }
    </div>
  `,
})
export class ManualComponent {
  private readonly auth = inject(AuthService);

  protected readonly role = this.auth.role;

  protected readonly sections = computed(() => {
    const r = this.role() ?? 'doctor';
    return SECTIONS_BY_ROLE[r];
  });

  protected readonly roleLabel = computed(() => ROLE_LABELS[this.role() ?? 'doctor']);
  protected readonly portalLabel = computed(() => ROLE_PORTAL_LABELS[this.role() ?? 'doctor']);
}
