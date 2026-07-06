import { ApplicationConfig, importProvidersFrom, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { QueryClient, provideTanStackQuery } from '@tanstack/angular-query-experimental';
import {
  Activity, AlertTriangle, Archive, ArrowLeft, ArrowRight, Award, Bell, BookOpen, Briefcase,
  Building2, Calendar, CalendarPlus, Camera, Check, CheckCircle, CheckCircle2, ChevronDown, ChevronRight,
  Circle, Clock, Cpu, Download, Edit3, Eye, EyeOff, FileText, FileWarning, FolderOpen, GitBranch, Heart, Info,
  Key, LayoutDashboard, Loader2, Lock, LockKeyhole, LogOut, LucideAngularModule, Mail, MailCheck, Menu,
  MessageSquare, Paperclip, Pen, PenLine, Pencil, Phone, Play, Plus,
  RefreshCw, Save, Scale, Search, Send, Settings, Shield, ShieldAlert, ShieldCheck, Star,
  Stethoscope, Trophy, User, Users, X, XCircle, Zap,
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
        Activity, AlertTriangle, Archive, ArrowLeft, ArrowRight, Award, Bell, BookOpen, Briefcase,
        Building2, Calendar, CalendarPlus, Camera, Check, CheckCircle, CheckCircle2, ChevronDown, ChevronRight,
        Circle, Clock, Cpu, Download, Edit3, Eye, EyeOff, FileText, FileWarning, FolderOpen, GitBranch, Heart, Info,
        Key, LayoutDashboard, Loader2, Lock, LockKeyhole, LogOut, Mail, MailCheck, Menu,
        MessageSquare, Paperclip, Pen, PenLine, Pencil, Phone, Play, Plus,
        RefreshCw, Save, Scale, Search, Send, Settings, Shield, ShieldAlert, ShieldCheck, Star,
        Stethoscope, Trophy, User, Users, X, XCircle, Zap,
      }),
    ),
  ],
};
