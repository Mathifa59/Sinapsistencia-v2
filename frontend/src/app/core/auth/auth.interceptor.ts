import { HttpInterceptorFn } from '@angular/common/http';

/**
 * El JWT viaja en cookie httpOnly `access_token`: basta con enviar
 * credenciales en cada request al API (equivale al withCredentials del legacy).
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (req.url.startsWith('/api')) {
    return next(req.clone({ withCredentials: true }));
  }
  return next(req);
};
