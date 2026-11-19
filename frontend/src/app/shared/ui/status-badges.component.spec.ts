import { TestBed } from '@angular/core/testing';
import {
  AuditActionBadgeComponent,
  CasePriorityBadgeComponent,
  CaseStatusBadgeComponent,
  ContactRequestStatusBadgeComponent,
  DocumentStatusBadgeComponent,
  RoleBadgeComponent,
} from './status-badges.component';
import {
  AUDIT_ACTION_LABELS,
  CASE_PRIORITY_LABELS,
  CASE_STATUS_LABELS,
  CONTACT_REQUEST_STATUS_LABELS,
  DOCUMENT_STATUS_LABELS,
  ROLE_LABELS,
  type AuditAction,
  type CasePriority,
  type CaseStatus,
  type ContactRequestStatus,
  type DocumentStatus,
  type UserRole,
} from '../constants';
import { provideTestIcons } from '../../testing/test-icons';

function texto<T>(componente: new () => T, entrada: string, valor: string): string {
  const fixture = TestBed.createComponent(componente);
  fixture.componentRef.setInput(entrada, valor);
  fixture.detectChanges();
  return ((fixture.nativeElement as HTMLElement).textContent ?? '').trim();
}

describe('insignias de estado', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideTestIcons()],
    });
  });

  it.each(Object.keys(CASE_STATUS_LABELS) as CaseStatus[])('consulta «%s» muestra su etiqueta', (estado) => {
    expect(texto(CaseStatusBadgeComponent, 'status', estado)).toBe(CASE_STATUS_LABELS[estado]);
  });

  it.each(Object.keys(CASE_PRIORITY_LABELS) as CasePriority[])('prioridad «%s» muestra su etiqueta', (p) => {
    expect(texto(CasePriorityBadgeComponent, 'priority', p)).toBe(CASE_PRIORITY_LABELS[p]);
  });

  it.each(Object.keys(CONTACT_REQUEST_STATUS_LABELS) as ContactRequestStatus[])('solicitud «%s» muestra su etiqueta', (e) => {
    expect(texto(ContactRequestStatusBadgeComponent, 'status', e)).toBe(CONTACT_REQUEST_STATUS_LABELS[e]);
  });

  it.each(Object.keys(AUDIT_ACTION_LABELS) as AuditAction[])('acción de auditoría «%s» muestra su etiqueta', (a) => {
    expect(texto(AuditActionBadgeComponent, 'action', a)).toBe(AUDIT_ACTION_LABELS[a]);
  });

  it.each(Object.keys(ROLE_LABELS) as UserRole[])('rol «%s» muestra su etiqueta', (r) => {
    expect(texto(RoleBadgeComponent, 'role', r)).toBe(ROLE_LABELS[r]);
  });

  it.each(Object.keys(DOCUMENT_STATUS_LABELS) as DocumentStatus[])('documento «%s» muestra su etiqueta', (d) => {
    expect(texto(DocumentStatusBadgeComponent, 'status', d)).toBe(DOCUMENT_STATUS_LABELS[d]);
  });
});
