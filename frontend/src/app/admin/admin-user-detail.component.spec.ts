import { HttpErrorResponse } from '@angular/common/http';
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
  secondFactorActive: false,
};

const enrolled: AdminUser = {
  ...user,
  secondFactorActive: true,
  secondFactorConfirmedAt: '2026-07-30T08:00:00Z',
};

const importJob: TransferJob = {
  id: '22222222-2222-4222-8222-222222222222',
  kind: TransferJobKindEnum.Import,
  state: TransferJobStateEnum.Ready,
  createdAt: '2026-07-24T00:00:00Z',
  expiresAt: '2026-07-25T00:00:00Z',
};

function problem(status: number, body: Record<string, unknown>): HttpErrorResponse {
  return new HttpErrorResponse({ status, statusText: 'Error', error: body });
}

describe('AdminUserDetailComponent', () => {
  const api = {
    getUser: vi.fn(),
    cancelAdminImport: vi.fn(),
    deactivateUser: vi.fn(),
    createAdministrativePasswordReset: vi.fn(),
    scheduleUserDeletion: vi.fn(),
    clearUserMfa: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
    api.getUser.mockReturnValue(of(user));
    api.deactivateUser.mockReturnValue(of(undefined));
    api.cancelAdminImport.mockReturnValue(
      throwError(() => problem(409, { detail: 'Cancellation failed.' })),
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

  it('reloads another user after deactivation so status and actions are current', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const fixture = TestBed.createComponent(AdminUserDetailComponent);
    fixture.detectChanges();

    fixture.componentInstance.deactivate();

    expect(api.deactivateUser).toHaveBeenCalledWith(user.id);
    expect(api.getUser).toHaveBeenCalledTimes(2);
  });

  it('shows an initial user-load failure without requiring a loaded user', () => {
    api.getUser.mockReturnValueOnce(
      throwError(() => problem(500, { detail: 'Account could not be loaded.' })),
    );
    const fixture = TestBed.createComponent(AdminUserDetailComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
      'Account could not be loaded.',
    );
  });

  it('asks for the administrator password in a panel instead of a browser dialog', () => {
    const confirmed = vi.spyOn(window, 'confirm').mockReturnValue(true);
    api.createAdministrativePasswordReset.mockReturnValue(of({ resetUrl: 'https://x/reset' }));
    const fixture = TestBed.createComponent(AdminUserDetailComponent);
    fixture.detectChanges();

    fixture.componentInstance.begin('reset');
    fixture.detectChanges();

    expect(confirmed).not.toHaveBeenCalled();
    expect(api.createAdministrativePasswordReset).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('input[name="adminPassword"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('input[autocomplete="one-time-code"]')).toBeNull();
  });

  it('sends the administrator password, then adds the code the server asks for', () => {
    api.createAdministrativePasswordReset
      .mockReturnValueOnce(
        throwError(() => problem(401, { errorCode: 'AUTH_MFA_STEP_UP_REQUIRED' })),
      )
      .mockReturnValueOnce(of({ resetUrl: 'https://x/reset' }));
    const fixture = TestBed.createComponent(AdminUserDetailComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.begin('reset');
    component.currentPassword = 'admin-password';
    component.submitPending();
    fixture.detectChanges();

    expect(api.createAdministrativePasswordReset).toHaveBeenLastCalledWith(user.id, {
      currentPassword: 'admin-password',
      code: undefined,
    });
    expect(component.error()).toBe('');
    expect(
      fixture.nativeElement.querySelector('input[autocomplete="one-time-code"]'),
    ).not.toBeNull();

    component.prompt.code = '123456';
    component.submitPending();
    fixture.detectChanges();

    expect(api.createAdministrativePasswordReset).toHaveBeenLastCalledWith(user.id, {
      currentPassword: 'admin-password',
      code: '123456',
    });
    expect(component.pending()).toBeNull();
    expect(component.reset()?.resetUrl).toBe('https://x/reset');
  });

  it('collects the typed username alongside the password before deleting immediately', () => {
    api.scheduleUserDeletion.mockReturnValue(of(undefined));
    const fixture = TestBed.createComponent(AdminUserDetailComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.begin('delete');
    component.confirmation = 'member';
    component.currentPassword = 'admin-password';
    component.submitPending();

    expect(api.scheduleUserDeletion).toHaveBeenCalledWith(user.id, {
      mode: 'IMMEDIATE',
      confirmation: 'member',
      currentPassword: 'admin-password',
      code: undefined,
    });
  });

  it('shows the enrollment state and offers the clear action only when a factor is active', () => {
    const fixture = TestBed.createComponent(AdminUserDetailComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Not set up');
    expect(fixture.nativeElement.textContent).not.toContain('Clear second factor');

    api.getUser.mockReturnValue(of(enrolled));
    fixture.componentInstance.load();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Active since 2026-07-30T08:00:00Z');
    expect(fixture.nativeElement.textContent).toContain('Clear second factor');
  });

  it('clears the second factor through the confirmation panel and adds the code on demand', () => {
    api.getUser.mockReturnValue(of(enrolled));
    api.clearUserMfa
      .mockReturnValueOnce(
        throwError(() => problem(401, { errorCode: 'AUTH_MFA_STEP_UP_REQUIRED' })),
      )
      .mockReturnValueOnce(of(user));
    const fixture = TestBed.createComponent(AdminUserDetailComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.begin('clear-second-factor');
    component.currentPassword = 'admin-password';
    component.submitPending();
    fixture.detectChanges();

    expect(
      fixture.nativeElement.querySelector('input[autocomplete="one-time-code"]'),
    ).not.toBeNull();

    component.prompt.code = '123456';
    component.submitPending();
    fixture.detectChanges();

    expect(api.clearUserMfa).toHaveBeenLastCalledWith(user.id, {
      currentPassword: 'admin-password',
      code: '123456',
    });
    expect(component.pending()).toBeNull();
    expect(component.user()?.secondFactorActive).toBe(false);
  });

  it('shows visible focus on the blind-import file control', () => {
    const fixture = TestBed.createComponent(AdminUserDetailComponent);
    fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('input[type="file"]') as HTMLInputElement;
    input.focus();
    const styles = Array.from(document.querySelectorAll('style'))
      .map((style) => style.textContent)
      .join('\n');

    expect(styles).toMatch(/\.file-button[^{]*:focus-within/);
    expect(document.activeElement).toBe(input);
  });
});
