import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ProjetsComponent } from './projets';
import { ProjetService } from '../../services/projet.service';

describe('ProjetsComponent', () => {
  const data = [{ id: 1, sujet: 'DevOps' }];
  const service = {
    getAll: vi.fn(() => of(data)),
    add: vi.fn(() => of(data[0])),
    update: vi.fn(() => of(data[0])),
    delete: vi.fn(() => of(undefined)),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [ProjetsComponent],
      providers: [{ provide: ProjetService, useValue: service }],
    }).compileComponents();
  });

  it('renders projets loaded on init', async () => {
    const fixture = TestBed.createComponent(ProjetsComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('DevOps');
  });

  it('add, edit and delete flows', () => {
    const c = TestBed.createComponent(ProjetsComponent).componentInstance;
    c.openAdd();
    c.update('sujet', 'Kubernetes');
    c.save();
    expect(service.add).toHaveBeenCalledWith({ sujet: 'Kubernetes' });

    c.edit(data[0]);
    c.save();
    expect(service.update).toHaveBeenCalledWith(data[0]);
    expect(c.showForm()).toBe(false);

    vi.spyOn(window, 'confirm').mockReturnValue(true);
    c.delete(1);
    expect(service.delete).toHaveBeenCalledWith(1);
  });
});
