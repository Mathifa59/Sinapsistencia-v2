import { TestBed } from '@angular/core/testing';
import { MlAdvisoryNoteComponent } from './ml-advisory-note.component';
import { provideTestIcons } from '../../testing/test-icons';

describe('MlAdvisoryNoteComponent (nota ética HU-43)', () => {
  it('declara que la recomendación es un apoyo y no una decisión', () => {
    TestBed.configureTestingModule({ providers: [provideTestIcons()] });
    const fixture = TestBed.createComponent(MlAdvisoryNoteComponent);
    fixture.detectChanges();
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(texto).toContain('apoyo a la decisión');
    expect(texto).toContain('no una decisión definitiva');
    expect(texto).toContain('revisión humana');
  });
});
