import { Component, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';

import { ProblemService } from '../core/problem.service';
import { TransfersService } from '../shared/generated-api/api/transfers.service';
import {
  type ExportRequest,
  ExportRequestScopeEnum,
} from '../shared/generated-api/model/exportRequest';
import { ImportApplyRequestStrategyEnum } from '../shared/generated-api/model/importApplyRequest';
import type { NotebookView } from '../shared/generated-api/model/notebookView';
import type { NoteSummary } from '../shared/generated-api/model/noteSummary';
import type { TransferJob } from '../shared/generated-api/model/transferJob';

type Step = 'menu' | 'working' | 'conflict' | 'done' | 'error';

@Component({
  selector: 'app-transfer-dialog',
  imports: [FormsModule],
  templateUrl: './transfer-dialog.component.html',
  styleUrl: './transfer-dialog.component.css',
})
export class TransferDialogComponent {
  readonly notebooks = input.required<NotebookView[]>();
  readonly notes = input.required<NoteSummary[]>();
  readonly closed = output<void>();
  readonly imported = output<void>();

  private readonly api = inject(TransfersService);
  private readonly problems = inject(ProblemService);
  protected readonly step = signal<Step>('menu');
  protected readonly job = signal<TransferJob | null>(null);
  protected readonly errors = signal<string[]>([]);
  protected readonly action = signal('');
  protected readonly exported = signal(false);
  protected readonly ExportScope = ExportRequestScopeEnum;
  protected readonly ImportApplyRequestStrategyEnum = ImportApplyRequestStrategyEnum;
  exportScope: ExportRequestScopeEnum = ExportRequestScopeEnum.All;
  exportNotebook = '';
  exportNote = '';
  private operationSequence = 0;

  protected async exportData(): Promise<void> {
    const request: ExportRequest = { scope: this.exportScope };
    if (this.exportScope === ExportRequestScopeEnum.Notebook)
      request.resourceId = this.exportNotebook;
    if (this.exportScope === ExportRequestScopeEnum.Note) request.resourceId = this.exportNote;
    if (this.exportScope !== ExportRequestScopeEnum.All && !request.resourceId) return;
    const operation = this.begin('Preparing export…');
    try {
      const created = await firstValueFrom(this.api.createExport(request));
      if (!this.active(operation)) {
        await this.cancelJob(created);
        return;
      }
      const completed = await this.poll(operation, created, (id) =>
        firstValueFrom(this.api.getExport(id)),
      );
      if (!completed || !this.active(operation)) return;
      if (completed.state !== 'SUCCEEDED' || !completed.downloadUrl) return this.fail(completed);
      const anchor = document.createElement('a');
      anchor.href = completed.downloadUrl;
      anchor.download = `glacier-export-${new Date().toISOString().slice(0, 10)}.glacier.json`;
      anchor.click();
      this.exported.set(true);
      this.step.set('menu');
    } catch (failure) {
      if (this.active(operation)) this.failRequest(failure);
    }
  }

  protected async inspect(file: File | null): Promise<void> {
    if (!file) return;
    const operation = this.begin('Inspecting import…');
    try {
      const created = await firstValueFrom(this.api.createImport(file));
      if (!this.active(operation)) {
        await this.cancelJob(created);
        return;
      }
      const inspected = await this.poll(operation, created, (id) =>
        firstValueFrom(this.api.getImport(id)),
      );
      if (!inspected || !this.active(operation)) return;
      if (inspected.state !== 'READY') return this.fail(inspected);
      this.job.set(inspected);
      if (inspected.hasConflicts) this.step.set('conflict');
      else await this.apply(ImportApplyRequestStrategyEnum.Preserve);
    } catch (failure) {
      if (this.active(operation)) this.failRequest(failure);
    }
  }

  protected async apply(strategy: ImportApplyRequestStrategyEnum): Promise<void> {
    const current = this.job();
    if (!current) return;
    const operation = this.begin('Applying import…');
    try {
      const queued = await firstValueFrom(this.api.applyImport(current.id, { strategy }));
      if (!this.active(operation)) return;
      const completed = await this.poll(operation, queued, (id) =>
        firstValueFrom(this.api.getImport(id)),
      );
      if (!completed || !this.active(operation)) return;
      if (completed.state !== 'SUCCEEDED') return this.fail(completed);
      this.job.set(completed);
      this.step.set('done');
      this.imported.emit();
    } catch (failure) {
      if (this.active(operation)) this.failRequest(failure);
    }
  }

  protected async cancel(): Promise<void> {
    ++this.operationSequence;
    const current = this.job();
    this.step.set('menu');
    this.job.set(null);
    if (current) {
      await this.cancelJob(current);
    }
  }

  protected close(): void {
    ++this.operationSequence;
    const current = this.job();
    if (current && ['working', 'conflict', 'error'].includes(this.step()))
      void this.cancelJob(current);
    this.closed.emit();
  }

  private begin(message: string): number {
    const operation = ++this.operationSequence;
    this.action.set(message);
    this.errors.set([]);
    this.step.set('working');
    return operation;
  }

  private async poll(
    operation: number,
    initial: TransferJob,
    load: (id: string) => Promise<TransferJob>,
  ): Promise<TransferJob | null> {
    if (!this.active(operation)) return null;
    let current = initial;
    this.job.set(current);
    while (this.active(operation) && ['QUEUED', 'RUNNING'].includes(current.state)) {
      await new Promise((resolve) => setTimeout(resolve, 500));
      if (!this.active(operation)) return null;
      const loaded = await load(current.id);
      if (!this.active(operation)) return null;
      current = loaded;
      this.job.set(current);
    }
    return this.active(operation) ? current : null;
  }

  private active(operation: number): boolean {
    return operation === this.operationSequence;
  }

  private async cancelJob(job: TransferJob): Promise<void> {
    const request =
      job.kind === 'EXPORT' ? this.api.cancelExport(job.id) : this.api.cancelImport(job.id);
    try {
      await firstValueFrom(request);
    } catch {
      // The job may already have reached a terminal state.
    }
  }

  private fail(job: TransferJob): void {
    this.job.set(job);
    this.errors.set(job.errors?.length ? job.errors : ['The transfer could not be completed.']);
    this.step.set('error');
  }

  private failRequest(failure: unknown): void {
    this.errors.set([this.problems.message(failure)]);
    this.step.set('error');
  }
}
