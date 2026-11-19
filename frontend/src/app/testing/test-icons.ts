import { importProvidersFrom } from '@angular/core';
import { Archive, CheckCircle, ChevronDown, Clock, Edit3, Eye, EyeOff, Info, LucideAngularModule } from 'lucide-angular';

/** Íconos que necesitan los componentes bajo prueba (en la app real se registran en app.config.ts). */
export function provideTestIcons() {
  return importProvidersFrom(LucideAngularModule.pick({ Archive, CheckCircle, ChevronDown, Clock, Edit3, Eye, EyeOff, Info }));
}
