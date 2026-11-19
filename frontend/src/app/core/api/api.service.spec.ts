import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApiService } from './api.service';

describe('ApiService', () => {
  let api: ApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(ApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('desempaqueta {success, data} y devuelve solo los datos', async () => {
    const p = api.get<{ id: number }>('/api/cases/1');
    http.expectOne('/api/cases/1').flush({ success: true, data: { id: 1 } });
    expect(await p).toEqual({ id: 1 });
  });

  it('lanza Error con el mensaje del backend cuando success es false', async () => {
    const p = api.post('/api/cases', { a: 1 });
    http.expectOne('/api/cases').flush({ success: false, error: 'Consulta inválida' });
    await expect(p).rejects.toThrow('Consulta inválida');
  });

  it('lanza Error con un mensaje por defecto cuando success es false y no hay texto', async () => {
    const p = api.get('/api/x');
    http.expectOne('/api/x').flush({ success: false });
    await expect(p).rejects.toThrow('Error desconocido');
  });

  it('toma el mensaje del cuerpo de un error HTTP', async () => {
    const p = api.get('/api/x');
    http.expectOne('/api/x').flush({ success: false, error: 'No autorizado' }, { status: 401, statusText: 'Unauthorized' });
    await expect(p).rejects.toThrow('No autorizado');
  });

  it('si el error HTTP no trae cuerpo con mensaje, usa el código', async () => {
    const p = api.get('/api/x');
    http.expectOne('/api/x').flush({}, { status: 500, statusText: 'Server Error' });
    await expect(p).rejects.toThrow('Error 500');
  });

  it('si no hay conexión, informa que no se pudo conectar', async () => {
    const p = api.get('/api/x');
    http.expectOne('/api/x').error(new ProgressEvent('error'));
    await expect(p).rejects.toThrow('No se pudo conectar con el servidor');
  });

  it('omite los parámetros vacíos o indefinidos y convierte los demás a texto', async () => {
    const p = api.get('/api/cases', { status: 'pendiente', page: 2, q: '', owner: undefined });
    const req = http.expectOne((r) => r.url === '/api/cases');
    expect(req.request.params.keys().sort()).toEqual(['page', 'status']);
    expect(req.request.params.get('page')).toBe('2');
    req.flush({ success: true, data: [] });
    await p;
  });

  it.each([
    ['post', (a: ApiService) => a.post('/api/r', { x: 1 }), 'POST'],
    ['put', (a: ApiService) => a.put('/api/r', { x: 1 }), 'PUT'],
    ['patch', (a: ApiService) => a.patch('/api/r', { x: 1 }), 'PATCH'],
    ['delete', (a: ApiService) => a.delete('/api/r'), 'DELETE'],
  ])('%s usa el método HTTP correcto', async (_n, call, method) => {
    const p = call(api);
    const req = http.expectOne('/api/r');
    expect(req.request.method).toBe(method);
    req.flush({ success: true, data: null });
    await p;
  });

  it('post sin cuerpo envía un objeto vacío', async () => {
    const p = api.post('/api/auth/logout');
    const req = http.expectOne('/api/auth/logout');
    expect(req.request.body).toEqual({});
    req.flush({ success: true });
    await p;
  });

  it('postForm envía el FormData tal cual', async () => {
    const form = new FormData();
    form.append('file', new Blob(['x']), 'a.txt');
    const p = api.postForm('/api/profile/avatar', form);
    const req = http.expectOne('/api/profile/avatar');
    expect(req.request.body).toBe(form);
    req.flush({ success: true, data: { url: 'u' } });
    expect(await p).toEqual({ url: 'u' });
  });
});
