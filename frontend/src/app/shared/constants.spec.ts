import { ROLE_DASHBOARD, ROLE_LABELS, ROLE_PORTAL_LABELS, type UserRole } from './constants';

const ROLES: UserRole[] = ['doctor', 'lawyer', 'admin'];

describe('constantes de rol', () => {
  it.each(ROLES)('el rol %s tiene etiqueta, portal y panel propio', (rol) => {
    expect(ROLE_LABELS[rol]).toBeTruthy();
    expect(ROLE_PORTAL_LABELS[rol]).toBeTruthy();
    expect(ROLE_DASHBOARD[rol]).toBe(`/${rol}/dashboard`);
  });

  it('los paneles no se comparten entre roles', () => {
    expect(new Set(Object.values(ROLE_DASHBOARD)).size).toBe(ROLES.length);
  });
});
