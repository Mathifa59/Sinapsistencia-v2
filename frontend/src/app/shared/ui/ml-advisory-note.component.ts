import { Component } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';

/** Mensaje ético HU-43: las recomendaciones ML son apoyo, no decisión. */
@Component({
  selector: 'app-ml-advisory-note',
  imports: [LucideAngularModule],
  template: `
    <div class="flex items-start gap-2.5 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
      <lucide-icon name="info" class="h-4 w-4 shrink-0 mt-0.5 text-amber-600" />
      <p class="leading-relaxed">
        {{ note }}
      </p>
    </div>
  `,
})
export class MlAdvisoryNoteComponent {
  readonly note =
    'Las recomendaciones del sistema son un apoyo a la decisión, no una decisión definitiva: ' +
    'la elección del profesional y la revisión humana por el abogado son siempre necesarias.';
}
