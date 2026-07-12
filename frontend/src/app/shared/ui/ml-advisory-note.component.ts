import { Component } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';

/** Mensaje ético HU-43: las recomendaciones ML son apoyo, no decisión. */
@Component({
  selector: 'app-ml-advisory-note',
  imports: [LucideAngularModule],
  template: `
    <p class="flex items-start gap-1.5 text-[11px] leading-relaxed text-slate-400">
      <lucide-icon name="info" class="mt-px h-3.5 w-3.5 shrink-0" />
      {{ note }}
    </p>
  `,
})
export class MlAdvisoryNoteComponent {
  readonly note =
    'Las recomendaciones del sistema son un apoyo a la decisión, no una decisión definitiva: ' +
    'la elección del profesional y la revisión humana por el abogado son siempre necesarias.';
}
