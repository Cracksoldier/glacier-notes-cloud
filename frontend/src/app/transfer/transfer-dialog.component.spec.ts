import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject } from 'rxjs';

import { ProblemService } from '../core/problem.service';
import { TransfersService } from '../shared/generated-api/api/transfers.service';
import {
  type TransferJob,
  TransferJobKindEnum,
  TransferJobStateEnum,
} from '../shared/generated-api/model/transferJob';
import { TransferDialogComponent } from './transfer-dialog.component';

function job(id: string, kind: TransferJobKindEnum, state: TransferJobStateEnum): TransferJob {
  return {
    id,
    kind,
    state,
    createdAt: '2026-07-24T00:00:00Z',
    expiresAt: '2026-07-25T00:00:00Z',
  };
}

describe('TransferDialogComponent', () => {
  let fixture: ComponentFixture<TransferDialogComponent>;
  const oldPoll = new Subject<TransferJob>();
  const api = {
    createExport: vi.fn(),
    getExport: vi.fn(),
    cancelExport: vi.fn(),
    createImport: vi.fn(),
    getImport: vi.fn(),
    cancelImport: vi.fn(),
  };

  beforeEach(() => {
    vi.useFakeTimers();
    vi.clearAllMocks();
    api.createExport.mockReturnValue(
      of(job('old-export', TransferJobKindEnum.Export, TransferJobStateEnum.Queued)),
    );
    api.getExport.mockReturnValue(oldPoll);
    api.cancelExport.mockReturnValue(of(undefined));
    api.createImport.mockReturnValue(
      of(job('new-import', TransferJobKindEnum.Import, TransferJobStateEnum.Queued)),
    );
    api.getImport.mockReturnValue(new Subject<TransferJob>());
    api.cancelImport.mockReturnValue(of(undefined));
    TestBed.configureTestingModule({
      providers: [
        { provide: TransfersService, useValue: api },
        { provide: ProblemService, useValue: { message: () => 'request failed' } },
      ],
    });
    fixture = TestBed.createComponent(TransferDialogComponent);
    fixture.componentRef.setInput('notebooks', []);
    fixture.componentRef.setInput('notes', []);
    fixture.detectChanges();
  });

  afterEach(() => {
    oldPoll.complete();
    vi.useRealTimers();
  });

  it('does not let a canceled poll overwrite a newly started operation', async () => {
    const component = fixture.componentInstance as unknown as {
      exportData(): Promise<void>;
      inspect(file: File): Promise<void>;
      cancel(): Promise<void>;
      job(): TransferJob | null;
    };
    const first = component.exportData();
    await Promise.resolve();
    await vi.advanceTimersByTimeAsync(500);
    expect(api.getExport).toHaveBeenCalledWith('old-export');

    await component.cancel();
    void component.inspect(new File(['{}'], 'import.glacier.json'));
    await Promise.resolve();
    expect(component.job()?.id).toBe('new-import');

    oldPoll.next(job('old-export', TransferJobKindEnum.Export, TransferJobStateEnum.Failed));
    oldPoll.complete();
    await first;

    expect(component.job()?.id).toBe('new-import');
  });

  it.each([
    {
      name: 'export',
      start: (component: { exportData(): Promise<void> }) => component.exportData(),
      configure: (created: Subject<TransferJob>) => api.createExport.mockReturnValue(created),
      cancel: () => api.cancelExport,
      created: job('late-export', TransferJobKindEnum.Export, TransferJobStateEnum.Queued),
    },
    {
      name: 'import',
      start: (component: { inspect(file: File): Promise<void> }) =>
        component.inspect(new File(['{}'], 'import.glacier.json')),
      configure: (created: Subject<TransferJob>) => api.createImport.mockReturnValue(created),
      cancel: () => api.cancelImport,
      created: job('late-import', TransferJobKindEnum.Import, TransferJobStateEnum.Queued),
    },
  ])('cancels a $name job created after the dialog closes', async (scenario) => {
    const created = new Subject<TransferJob>();
    scenario.configure(created);
    const component = fixture.componentInstance as unknown as {
      exportData(): Promise<void>;
      inspect(file: File): Promise<void>;
      close(): void;
    };

    const request = scenario.start(component);
    await Promise.resolve();
    component.close();
    created.next(scenario.created);
    created.complete();
    await request;

    expect(scenario.cancel()).toHaveBeenCalledWith(scenario.created.id);
  });

  it('cancels a live job when the dialog closes after polling fails', async () => {
    const component = fixture.componentInstance as unknown as {
      close(): void;
      job: { set(value: TransferJob): void };
      step: { set(value: 'error'): void };
    };
    const current = job('failed-poll', TransferJobKindEnum.Export, TransferJobStateEnum.Running);
    component.job.set(current);
    component.step.set('error');

    component.close();
    await Promise.resolve();

    expect(api.cancelExport).toHaveBeenCalledWith(current.id);
  });
});
