import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { EquipesComponent } from './equipes';
import { EquipeService } from '../../services/equipe.service';
import { EntrepriseService } from '../../services/entreprise.service';

describe('EquipesComponent', () => {
  const equipes = [{ id: 1, nom: 'Back', specialite: 'Java', entreprise: { id: 5, nom: 'Acme' } }];
  const equipeService = {
    getAll: vi.fn(() => of(equipes)),
    add: vi.fn(() => of(equipes[0])),
    update: vi.fn(() => of(equipes[0])),
    delete: vi.fn(() => of(undefined)),
  };
  const entrepriseService = { getAll: vi.fn(() => of([{ id: 5, nom: 'Acme', adresse: 'Tunis' }])) };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [EquipesComponent],
      providers: [
        { provide: EquipeService, useValue: equipeService },
        { provide: EntrepriseService, useValue: entrepriseService },
      ],
    }).compileComponents();
  });

  it('loads equipes and entreprises on init', async () => {
    const fixture = TestBed.createComponent(EquipesComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.componentInstance.equipes()).toEqual(equipes);
    expect(fixture.componentInstance.entreprises().length).toBe(1);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Back');
  });

  it('sends the selected entreprise when adding', () => {
    const c = TestBed.createComponent(EquipesComponent).componentInstance;
    c.openAdd();
    c.update('nom', 'Ops');
    c.update('specialite', 'Docker');
    c.selectedEntrepriseId.set(5);
    c.save();
    expect(equipeService.add).toHaveBeenCalledWith({ nom: 'Ops', specialite: 'Docker', entreprise: { id: 5 } });
  });

  it('keeps the entreprise when editing and omits it when cleared', () => {
    const c = TestBed.createComponent(EquipesComponent).componentInstance;
    c.edit(equipes[0]);
    expect(c.selectedEntrepriseId()).toBe(5);
    c.selectedEntrepriseId.set(null);
    c.save();
    expect(equipeService.update).toHaveBeenCalledWith(expect.objectContaining({ id: 1, entreprise: undefined }));
  });

  it('deletes after confirmation', () => {
    const c = TestBed.createComponent(EquipesComponent).componentInstance;
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    c.delete(1);
    expect(equipeService.delete).toHaveBeenCalledWith(1);
    c.cancel();
    expect(c.showForm()).toBe(false);
  });
});
