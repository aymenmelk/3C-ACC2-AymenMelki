import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { EntreprisesComponent } from './entreprises';
import { EntrepriseService } from '../../services/entreprise.service';

describe('EntreprisesComponent', () => {
  const data = [{ id: 1, nom: 'Acme', adresse: 'Tunis' }];
  let service: { getAll: ReturnType<typeof vi.fn>; add: ReturnType<typeof vi.fn>; update: ReturnType<typeof vi.fn>; delete: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    service = {
      getAll: vi.fn(() => of(data)),
      add: vi.fn(() => of(data[0])),
      update: vi.fn(() => of(data[0])),
      delete: vi.fn(() => of(undefined)),
    };
    await TestBed.configureTestingModule({
      imports: [EntreprisesComponent],
      providers: [{ provide: EntrepriseService, useValue: service }],
    }).compileComponents();
  });

  it('loads entreprises on init and renders them', async () => {
    const fixture = TestBed.createComponent(EntreprisesComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(service.getAll).toHaveBeenCalled();
    expect(fixture.componentInstance.entreprises()).toEqual(data);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Acme');
  });

  it('adds a new entreprise and closes the form', () => {
    const c = TestBed.createComponent(EntreprisesComponent).componentInstance;
    c.openAdd();
    c.update('nom', 'New');
    c.update('adresse', 'Sfax');
    c.save();
    expect(service.add).toHaveBeenCalledWith({ nom: 'New', adresse: 'Sfax' });
    expect(c.showForm()).toBe(false);
  });

  it('edits an existing entreprise through update()', () => {
    const c = TestBed.createComponent(EntreprisesComponent).componentInstance;
    c.edit(data[0]);
    expect(c.editMode()).toBe(true);
    c.save();
    expect(service.update).toHaveBeenCalledWith(data[0]);
  });

  it('deletes only after confirmation', () => {
    const c = TestBed.createComponent(EntreprisesComponent).componentInstance;
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true);
    c.delete(1);
    expect(service.delete).not.toHaveBeenCalled();
    c.delete(1);
    expect(service.delete).toHaveBeenCalledWith(1);
    confirmSpy.mockRestore();
  });
});
