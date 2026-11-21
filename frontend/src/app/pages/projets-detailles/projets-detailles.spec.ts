import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ProjetsDetaillesComponent } from './projets-detailles';
import { ProjetDetailleService } from '../../services/projet-detaille.service';
import { ProjetService } from '../../services/projet.service';

describe('ProjetsDetaillesComponent', () => {
  const pds = [
    { id: 1, description: 'CI', technologie: 'Spring', coutProvisoire: 1500, dateDebut: '2026-01-15', projet: { id: 3 } },
  ];
  const service = {
    getAll: vi.fn(() => of(pds)),
    add: vi.fn(() => of(pds[0])),
    update: vi.fn(() => of(pds[0])),
    delete: vi.fn(() => of(undefined)),
  };
  const projetService = { getAll: vi.fn(() => of([{ id: 3, sujet: 'DevOps' }])) };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [ProjetsDetaillesComponent],
      providers: [
        { provide: ProjetDetailleService, useValue: service },
        { provide: ProjetService, useValue: projetService },
      ],
    }).compileComponents();
  });

  it('renders detailed projects on init', async () => {
    const fixture = TestBed.createComponent(ProjetsDetaillesComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.componentInstance.projets().length).toBe(1);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Spring');
  });

  it('adds with the selected projet', () => {
    const c = TestBed.createComponent(ProjetsDetaillesComponent).componentInstance;
    c.openAdd();
    c.update('description', 'Docker');
    c.update('technologie', 'Compose');
    c.update('coutProvisoire', 200);
    c.update('dateDebut', '2026-03-01');
    c.selectedProjetId.set(3);
    c.save();
    expect(service.add).toHaveBeenCalledWith({
      description: 'Docker', technologie: 'Compose', coutProvisoire: 200, dateDebut: '2026-03-01', projet: { id: 3 },
    });
  });

  it('edits and deletes', () => {
    const c = TestBed.createComponent(ProjetsDetaillesComponent).componentInstance;
    c.edit(pds[0]);
    expect(c.selectedProjetId()).toBe(3);
    c.save();
    expect(service.update).toHaveBeenCalledWith(pds[0]);
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    c.delete(1);
    expect(service.delete).not.toHaveBeenCalled();
  });
});
