import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { AuthStore } from '../core/auth.store';
import { AdministrationService } from '../shared/generated-api/api/administration.service';
import {
  type AdminUser,
  AdminUserRoleEnum,
  AdminUserStatusEnum,
} from '../shared/generated-api/model/adminUser';
import {
  type TransferJob,
  TransferJobKindEnum,
  TransferJobStateEnum,
} from '../shared/generated-api/model/transferJob';
import { AdminUserDetailComponent } from './admin-user-detail.component';

const user: AdminUser = {
  id: '11111111-1111-4111-8111-111111111111',
  username: 'member',
  email: 'member@example.com',
  role: AdminUserRoleEnum.User,
  status: AdminUserStatusEnum.Active,
  createdAt: '2026-07-24T00:00:00Z',
  storageBytes: 0,
  noteCount: 0,
  notebookCount: 1,
  imageCount: 0,
};

const importJob: TransferJob = {
  id: '22222222-2222-4222-8222-222222222222',
  kind: TransferJobKindEnum.Import,
  state: TransferJobStateEnum.Ready,
  createdAt: '2026-07-24T00:00:00Z',
  expiresAt: '2026-07-25T00:00:00Z',
};

describe('AdminUserDetailComponent', () => {
  const api = {
    getUser: vi.fn(),
    cancelAdminImport: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
    api.getUser.mockReturnValue(of(user));
    api.cancelAdminImport.mockReturnValue(
      throwError(() => ({ error: { detail: 'Cancellation failed.' } })),
    );
    TestBed.configureTestingModule({
      providers: [
        { provide: AdministrationService, useValue: api },
        { provide: AuthStore, useValue: { session: () => null } },
        { provide: Router, useValue: { navigate: vi.fn() } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: () => user.id } } },
        },
      ],
    });
  });

  it('reports failed blind-import cancellation and always clears the busy state', async () => {
    const fixture = TestBed.createComponent(AdminUserDetailComponent);
    const component = fixture.componentInstance;
    component.importJob.set(importJob);
    component.importBusy.set(true);

    await expect(component.cancelImport()).resolves.toBeUndefined();

    expect(component.error()).toBe('Cancellation failed.');
    expect(component.importBusy()).toBe(false);
    expect(component.importJob()).toEqual(importJob);
  });
});
