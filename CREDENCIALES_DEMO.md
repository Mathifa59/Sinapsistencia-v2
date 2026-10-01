# Credenciales de cuentas demo — Sinapsistencia v2

> Generado a partir de los seeds Flyway (`V3`, `V4`, `V5`, `V12`, `V14`) y
> `DemoAccountEmailConfigurer`. Todas estas cuentas son **datos simulados**
> (Ley 29733) — ningún nombre corresponde a una persona real. La contraseña
> de todas las cuentas autenticables es la misma: **`Demo123!`**.
>
> Este archivo no está en `.gitignore` por defecto — bórralo o ignóralo si no
> quieres que quede en el historial de git al hacer commit.

## Administrador

| Email | Password | Nombre |
|---|---|---|
| `admin.demo@sinapsistencia.pe` (fijo, no configurable por env var) | `Demo123!` | Jorge Ramírez Castillo |

## Médico demo (para crear casos de prueba)

| Email | Password | Nombre |
|---|---|---|
| `doctor.demo@sinapsistencia.pe` (o el valor de `DEMO_DOCTOR_EMAIL` en Railway) | `Demo123!` | Dr. Carlos Mendoza Aguilar |

## Abogados — autenticables (12)

Estos son los únicos abogados con los que puedes iniciar sesión. El primero
(Lucía Fernández) es la cuenta "abogado" del botón de login por rol; su
correo puede estar sobrescrito por `DEMO_LAWYER_EMAIL` en Railway.

| Email (seed) | Password | Nombre |
|---|---|---|
| `lawyer.demo@sinapsistencia.pe` (o `DEMO_LAWYER_EMAIL`) | `Demo123!` | Dra. Lucía Fernández Torres |
| `joaquin.espinoza@sinapsistencia.pe` | `Demo123!` | Abg. Joaquín Espinoza Ruiz |
| `daniela.vargas@sinapsistencia.pe` | `Demo123!` | Abg. Daniela Vargas Solís |
| `mateo.huaman@sinapsistencia.pe` | `Demo123!` | Abg. Mateo Huamán Ríos |
| `patricia.nunez@sinapsistencia.pe` | `Demo123!` | Abg. Patricia Núñez Flores |
| `mario.castillo@sinapsistencia.pe` | `Demo123!` | Abg. Mario Castillo Bravo |
| `paola.ramirez@sinapsistencia.pe` | `Demo123!` | Abg. Paola Ramírez Soto |
| `renato.salazar@sinapsistencia.pe` | `Demo123!` | Abg. Renato Salazar Méndez |
| `carmen.vega@sinapsistencia.pe` | `Demo123!` | Abg. Carmen Vega Ibáñez |
| `diego.huaman@sinapsistencia.pe` | `Demo123!` | Abg. Diego Huamán Vera |
| `ines.quispe@sinapsistencia.pe` | `Demo123!` | Abg. Inés Quispe Loayza |
| `jorge.paredes@sinapsistencia.pe` | `Demo123!` | Abg. Jorge Paredes Flores |

**Nota sobre el correo real en producción:** si en Railway está configurada
`DEMO_MAIL_BASE` (p. ej. `tucorreo@gmail.com`), `DemoAccountEmailConfigurer`
reescribe el correo de **todos estos abogados excepto Lucía Fernández** a un
alias `tucorreo+usuario@gmail.com` (ej. `tucorreo+joaquin.espinoza@gmail.com`),
para que los avisos de H-06 lleguen todos a la misma bandeja de prueba. Eso
cambia también el email de **login**, no solo el de notificación — si no
puedes entrar con el correo de la tabla, revisa esa variable en Railway.

## Abogados NO autenticables (41 perfiles — V12 y V14)

Los 33 abogados del corpus DS-03 (`V12`) y los 8 de refuerzo de
especialidades escasas (`V14`) **no tienen contraseña utilizable**: cada uno
tiene un hash bcrypt de una contraseña aleatoria de un solo uso, generada y
descartada en `build_corpus.py` (nunca impresa ni guardada en ningún lado,
ver `docs/datasheet-corpus-ds03.md`). Existen solo como candidatos del
matching en vivo (`is_active = TRUE`), no se puede iniciar sesión con ellos.

## Resumen rápido para login por rol (frontend)

El botón de login por rol en el frontend usa las mismas env vars que la
tabla de arriba, así que siempre coinciden:

- **Médico** → `doctor.demo@sinapsistencia.pe` / `Demo123!`
- **Abogado** → `lawyer.demo@sinapsistencia.pe` / `Demo123!`
- **Administrador** → `admin.demo@sinapsistencia.pe` / `Demo123!`
