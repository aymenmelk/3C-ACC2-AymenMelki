import { ProviderToken } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { EquipeService } from './equipe.service';
import { ProjetService } from './projet.service';
import { ProjetDetailleService } from './projet-detaille.service';
import { EntrepriseService } from './entreprise.service';

const API = '/api';

describe('CRUD services', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const cases = [
    { name: 'EntrepriseService', token: EntrepriseService, base: 'entreprise', body: { nom: 'Acme', adresse: 'Tunis' } },
    { name: 'EquipeService', token: EquipeService, base: 'equipe', body: { nom: 'Dev', specialite: 'Java' } },
    { name: 'ProjetService', token: ProjetService, base: 'projet', body: { sujet: 'DevOps' } },
    {
      name: 'ProjetDetailleService',
      token: ProjetDetailleService,
      base: 'projet-detaille',
      body: { description: 'CI', technologie: 'Spring', coutProvisoire: 10, dateDebut: '2026-01-01' },
    },
  ] as const;

  for (const c of cases) {
    describe(c.name, () => {
      let service: any;
      beforeEach(() => (service = TestBed.inject(c.token as ProviderToken<unknown>)));

      it('getAll -> GET /all', () => {
        service.getAll().subscribe((r: unknown) => expect(r).toEqual([c.body]));
        const req = http.expectOne(`${API}/${c.base}/all`);
        expect(req.request.method).toBe('GET');
        req.flush([c.body]);
      });

      it('getById -> GET /get/:id', () => {
        service.getById(7).subscribe();
        const req = http.expectOne(`${API}/${c.base}/get/7`);
        expect(req.request.method).toBe('GET');
        req.flush(c.body);
      });

      it('add -> POST /add with body', () => {
        service.add(c.body).subscribe();
        const req = http.expectOne(`${API}/${c.base}/add`);
        expect(req.request.method).toBe('POST');
        expect(req.request.body).toEqual(c.body);
        req.flush({ id: 1, ...c.body });
      });

      it('update -> PUT /update with body', () => {
        service.update({ id: 1, ...c.body }).subscribe();
        const req = http.expectOne(`${API}/${c.base}/update`);
        expect(req.request.method).toBe('PUT');
        expect(req.request.body.id).toBe(1);
        req.flush({ id: 1, ...c.body });
      });

      it('delete -> DELETE /delete/:id', () => {
        service.delete(4).subscribe();
        const req = http.expectOne(`${API}/${c.base}/delete/4`);
        expect(req.request.method).toBe('DELETE');
        req.flush(null);
      });
    });
  }

  it('EquipeService.assignToEntreprise -> PUT', () => {
    TestBed.inject(EquipeService).assignToEntreprise(1, 2).subscribe();
    const req = http.expectOne(`${API}/equipe/assign-entreprise/1/2`);
    expect(req.request.method).toBe('PUT');
    req.flush({});
  });

  it('ProjetDetailleService.assignToProjet -> PUT', () => {
    TestBed.inject(ProjetDetailleService).assignToProjet(3, 4).subscribe();
    const req = http.expectOne(`${API}/projet-detaille/assign-projet/3/4`);
    expect(req.request.method).toBe('PUT');
    req.flush({});
  });
});
