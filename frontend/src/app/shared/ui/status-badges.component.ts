import { Component, computed, input } from '@angular/core';
import { LucideAngularModule } from 'lucide-angular';
import { BadgeDirective } from './badge.directive';
import {
  CASE_PRIORITY_BADGE_VARIANT,
  CASE_PRIORITY_LABELS,
  CASE_STATUS_BADGE_VARIANT,
  CASE_STATUS_LABELS,
  CONTACT_REQUEST_STATUS_LABELS,
  DOCUMENT_STATUS_BADGE_VARIANT,
  DOCUMENT_STATUS_LABELS,
  AUDIT_ACTION_BADGE_VARIANT,
  AUDIT_ACTION_LABELS,
  ROLE_BADGE_VARIANT,
  ROLE_LABELS,
  type AuditAction,
  type CasePriority,
  type CaseStatus,
  type ContactRequestStatus,
  type DocumentStatus,
  type UserRole,
} from '../constants';

/** Badge de estado de consulta (flujo de 6 estados, HU-16). */
@Component({
  selector: 'app-case-status-badge',
  imports: [BadgeDirective],
  template: `<span appBadge [variant]="variant()">{{ label() }}</span>`,
})
export class CaseStatusBadgeComponent {
  readonly status = input.required<CaseStatus>();
  protected readonly label = computed(() => CASE_STATUS_LABELS[this.status()]);
  protected readonly variant = computed(() => CASE_STATUS_BADGE_VARIANT[this.status()]);
}

/** Badge de prioridad de consulta. */
@Component({
  selector: 'app-case-priority-badge',
  imports: [BadgeDirective],
  template: `<span appBadge [variant]="variant()">{{ label() }}</span>`,
})
export class CasePriorityBadgeComponent {
  readonly priority = input.required<CasePriority>();
  protected readonly label = computed(() => CASE_PRIORITY_LABELS[this.priority()]);
  protected readonly variant = computed(() => CASE_PRIORITY_BADGE_VARIANT[this.priority()]);
}

const DOCUMENT_STATUS_ICON: Record<DocumentStatus, string> = {
  firmado: 'check-circle',
  pendiente_firma: 'clock',
  borrador: 'edit-3',
  archivado: 'archive',
};

/** Badge de estado de documento, con icono. */
@Component({
  selector: 'app-document-status-badge',
  imports: [BadgeDirective, LucideAngularModule],
  template: `
    <span appBadge [variant]="variant()" class="flex items-center gap-1">
      <lucide-icon [name]="icon()" class="h-3 w-3" />
      {{ label() }}
    </span>
  `,
})
export class DocumentStatusBadgeComponent {
  readonly status = input.required<DocumentStatus>();
  protected readonly label = computed(() => DOCUMENT_STATUS_LABELS[this.status()]);
  protected readonly variant = computed(() => DOCUMENT_STATUS_BADGE_VARIANT[this.status()]);
  protected readonly icon = computed(() => DOCUMENT_STATUS_ICON[this.status()]);
}

const CONTACT_REQUEST_STATUS_VARIANT: Record<ContactRequestStatus, 'secondary' | 'success' | 'destructive' | 'outline'> = {
  pendiente: 'secondary',
  aceptado: 'success',
  rechazado: 'destructive',
  cancelado: 'outline',
};

/** Badge de estado de solicitud de contacto. */
@Component({
  selector: 'app-contact-request-status-badge',
  imports: [BadgeDirective],
  template: `<span appBadge [variant]="variant()">{{ label() }}</span>`,
})
export class ContactRequestStatusBadgeComponent {
  readonly status = input.required<ContactRequestStatus>();
  protected readonly label = computed(() => CONTACT_REQUEST_STATUS_LABELS[this.status()]);
  protected readonly variant = computed(() => CONTACT_REQUEST_STATUS_VARIANT[this.status()]);
}

/** Badge de acción de auditoría. */
@Component({
  selector: 'app-audit-action-badge',
  imports: [BadgeDirective],
  template: `<span appBadge [variant]="variant()">{{ label() }}</span>`,
})
export class AuditActionBadgeComponent {
  readonly action = input.required<AuditAction>();
  protected readonly label = computed(() => AUDIT_ACTION_LABELS[this.action()]);
  protected readonly variant = computed(() => AUDIT_ACTION_BADGE_VARIANT[this.action()]);
}

/** Badge de rol de usuario. */
@Component({
  selector: 'app-role-badge',
  imports: [BadgeDirective],
  template: `<span appBadge [variant]="variant()">{{ label() }}</span>`,
})
export class RoleBadgeComponent {
  readonly role = input.required<UserRole>();
  protected readonly label = computed(() => ROLE_LABELS[this.role()]);
  protected readonly variant = computed(() => ROLE_BADGE_VARIANT[this.role()]);
}
