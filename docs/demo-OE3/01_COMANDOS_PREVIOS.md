# Comandos previos — Demo OE3

Ejecutar **antes** de grabar. Orden recomendado:

## 1. Base de datos

```powershell
cd "c:\Users\Renato\Desktop\Renato_Documentos\UPC\2026-1\TP1\applicacion\Sinapsistencia-v2-main\Sinapsistencia-v2-main"
docker compose up -d
```

Verificar: Postgres en `localhost:5433`.

## 2. Servicio ML (obligatorio para matching y riesgo)

```powershell
cd ml-service
.venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

Verificar: `http://localhost:8000/health` responde OK.

## 3. Backend Spring Boot

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Verificar: `http://localhost:8080/api/ml/health` responde.

## 4. Frontend Angular

```powershell
cd frontend
npm start
```

Abrir: `http://localhost:4200`

## Cuentas demo

| Rol | Email | Contraseña |
|-----|-------|------------|
| Médico | `doctor.demo@sinapsistencia.pe` | `Demo123!` |
| Abogado | `lawyer.demo@sinapsistencia.pe` | `Demo123!` |
| Admin | `admin.demo@sinapsistencia.pe` | `Demo123!` |

## Checklist rápido pre-grabación

- [ ] Docker Postgres activo
- [ ] ML service puerto 8000
- [ ] Backend puerto 8080
- [ ] Frontend puerto 4200
- [ ] Navegador en ventana limpia (incógnito opcional)
- [ ] DevTools cerrado o en segunda pantalla (para capturar tiempos si aplica OE3-I2)
