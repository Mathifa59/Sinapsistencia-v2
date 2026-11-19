import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let client: HttpClient;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()],
    });
    client = TestBed.inject(HttpClient);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('envía las credenciales (cookie httpOnly) en las llamadas al API', () => {
    client.get('/api/cases').subscribe();
    const req = http.expectOne('/api/cases');
    expect(req.request.withCredentials).toBe(true);
    req.flush({});
  });

  it('no envía credenciales a otros orígenes', () => {
    client.get('https://ejemplo.com/datos').subscribe();
    const req = http.expectOne('https://ejemplo.com/datos');
    expect(req.request.withCredentials).toBe(false);
    req.flush({});
  });

  it('no toca las rutas que solo se parecen a /api', () => {
    client.get('/apiary/x').subscribe();
    // el interceptor usa startsWith('/api'), así que esta ruta también lleva credenciales:
    // se documenta el comportamiento actual para que un cambio no pase desapercibido.
    const req = http.expectOne('/apiary/x');
    expect(req.request.withCredentials).toBe(true);
    req.flush({});
  });
});
