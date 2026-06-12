import { ApplicationConfig, importProvidersFrom, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { QueryClient, provideTanStackQuery } from '@tanstack/angular-query-experimental';
import {
  Activity, AlertTriangle, Archive, ArrowLeft, ArrowRight, Award, Bell, Briefcase,
  Building2, Calendar, Camera, Check, CheckCircle, CheckCircle2, ChevronDown, ChevronRight,
  Circle, Clock, Edit3, Eye, EyeOff, FileText, FileWarning, FolderOpen, Heart, Info,
  LayoutDashboard, Loader2, LogOut, LucideAngularModule, Mail, Menu, Pen, Phone, Plus,
  RefreshCw, Save, Scale, Search, Send, Settings, Shield, ShieldAlert, ShieldCheck, Star,
  Stethoscope, User, Users, X, XCircle, Zap,
} from 'lucide-angular';

import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideTanStackQuery(
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 30_000,
            retry: 1,
            refetchOnWindowFocus: false,
          },
        },
      }),
    ),
    importProvidersFrom(
      LucideAngularModule.pick({
        Activity, AlertTriangle, Archive, ArrowLeft, ArrowRight, Award, Bell, Briefcase,
        Building2, Calendar, Camera, Check, CheckCircle, CheckCircle2, ChevronDown, ChevronRight,
        Circle, Clock, Edit3, Eye, EyeOff, FileText, FileWarning, FolderOpen, Heart, Info,
        LayoutDashboard, Loader2, LogOut, Mail, Menu, Pen, Phone, Plus,
        RefreshCw, Save, Scale, Search, Send, Settings, Shield, ShieldAlert, ShieldCheck, Star,
        Stethoscope, User, Users, X, XCircle, Zap,
      }),
    ),
  ],
};
