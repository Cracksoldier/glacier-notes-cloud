import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { ContentColor } from '../shared/generated-api/model/contentColor';
import type { ContentNote } from '../shared/generated-api/model/contentNote';
import { NoteType } from '../shared/generated-api/model/noteType';
import type { NoteVersion } from '../shared/generated-api/model/noteVersion';
import { NoteVersionReason } from '../shared/generated-api/model/noteVersionReason';
import type { NoteVersionSummary } from '../shared/generated-api/model/noteVersionSummary';
import { NoteEditorComponent } from './note-editor.component';
import { NotesStore } from './notes.store';

const note: ContentNote = {
  id: '22222222-2222-4222-8222-222222222222',
  notebookId: '11111111-1111-4111-8111-111111111111',
  noteType: NoteType.Text,
  title: 'Before',
  content: '',
  checklistItems: [],
  pinned: false,
  archived: false,
  color: ContentColor.Default,
  labelIds: [],
  imageIds: [],
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
  version: 0,
};

function version(id: string, title: string): NoteVersion {
  return {
    id,
    noteId: note.id,
    sourceVersion: 1,
    reason: NoteVersionReason.EditorClose,
    snapshotAt: '2026-01-01T00:00:00Z',
    noteType: NoteType.Text,
    title,
    content: '',
    checklistItems: [],
    pinned: false,
    archived: false,
    color: ContentColor.Default,
    labelIds: [],
    imageIds: [],
  };
}

function summary(value: NoteVersion): NoteVersionSummary {
  return {
    id: value.id,
    noteId: value.noteId,
    sourceVersion: value.sourceVersion,
    reason: value.reason,
    snapshotAt: value.snapshotAt,
    noteType: value.noteType,
    title: value.title,
  };
}

describe('NoteEditorComponent', () => {
  let fixture: ComponentFixture<NoteEditorComponent>;
  let showModalDescriptor: PropertyDescriptor | undefined;
  let closeDescriptor: PropertyDescriptor | undefined;
  const store = {
    labels: signal([]),
    notebooks: signal([]),
    saveNote: vi.fn(),
    closeEditor: vi.fn(),
    refresh: vi.fn(),
    snapshotEditorNote: vi.fn(),
    overwriteNote: vi.fn(),
    reloadEditorNote: vi.fn(),
    listNoteVersions: vi.fn(),
    getNoteVersion: vi.fn(),
    restoreNoteVersion: vi.fn(),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    vi.useFakeTimers();
    showModalDescriptor = Object.getOwnPropertyDescriptor(HTMLDialogElement.prototype, 'showModal');
    closeDescriptor = Object.getOwnPropertyDescriptor(HTMLDialogElement.prototype, 'close');
    Object.defineProperty(HTMLDialogElement.prototype, 'showModal', {
      configurable: true,
      value: vi.fn(),
    });
    Object.defineProperty(HTMLDialogElement.prototype, 'close', {
      configurable: true,
      value: vi.fn(),
    });
    store.saveNote.mockImplementation(async (_note: ContentNote, update: { title: string }) => ({
      ...note,
      title: update.title,
      version: 1,
    }));
    store.snapshotEditorNote.mockResolvedValue(undefined);
    TestBed.configureTestingModule({ providers: [{ provide: NotesStore, useValue: store }] });
    fixture = TestBed.createComponent(NoteEditorComponent);
    fixture.componentRef.setInput('initialNote', note);
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.destroy();
    if (showModalDescriptor) {
      Object.defineProperty(HTMLDialogElement.prototype, 'showModal', showModalDescriptor);
    } else {
      Reflect.deleteProperty(HTMLDialogElement.prototype, 'showModal');
    }
    if (closeDescriptor) {
      Object.defineProperty(HTMLDialogElement.prototype, 'close', closeDescriptor);
    } else {
      Reflect.deleteProperty(HTMLDialogElement.prototype, 'close');
    }
    vi.useRealTimers();
  });

  it('debounces edits for 500 ms and saves the known optimistic version', async () => {
    const title = fixture.nativeElement.querySelector(
      '[aria-label="Note title"]',
    ) as HTMLInputElement;
    title.value = 'After';
    title.dispatchEvent(new Event('input'));

    await vi.advanceTimersByTimeAsync(499);
    expect(store.saveNote).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(1);

    expect(store.saveNote).toHaveBeenCalledWith(
      note,
      expect.objectContaining({ title: 'After', version: 0 }),
    );
    fixture.detectChanges();
    expect(
      (fixture.nativeElement.querySelector('.save-state__message') as HTMLElement).textContent,
    ).toBe('Saved');
  });

  it('snapshots meaningful saved state when the editor closes', async () => {
    const title = fixture.nativeElement.querySelector(
      '[aria-label="Note title"]',
    ) as HTMLInputElement;
    title.value = 'After';
    title.dispatchEvent(new Event('input'));
    await vi.advanceTimersByTimeAsync(500);

    const close = fixture.nativeElement.querySelector(
      '[aria-label="Save and close"]',
    ) as HTMLButtonElement;
    close.click();
    await vi.runAllTimersAsync();

    expect(store.snapshotEditorNote).toHaveBeenCalledWith(expect.objectContaining({ version: 1 }));
    expect(store.closeEditor).toHaveBeenCalled();
  });

  it('still closes and refreshes when the secondary history snapshot fails', async () => {
    store.snapshotEditorNote.mockRejectedValueOnce(new Error('snapshot unavailable'));
    const title = fixture.nativeElement.querySelector(
      '[aria-label="Note title"]',
    ) as HTMLInputElement;
    title.value = 'After';
    title.dispatchEvent(new Event('input'));
    await vi.advanceTimersByTimeAsync(500);

    const close = fixture.nativeElement.querySelector(
      '[aria-label="Save and close"]',
    ) as HTMLButtonElement;
    close.click();
    await vi.runAllTimersAsync();

    expect(store.closeEditor).toHaveBeenCalled();
    expect(store.refresh).toHaveBeenCalled();
  });

  it('dismisses version history before closing the editor on Escape', () => {
    (
      fixture.componentInstance as unknown as {
        historyOpen: { set(value: boolean): void };
      }
    ).historyOpen.set(true);
    fixture.detectChanges();

    fixture.nativeElement
      .querySelector('dialog')
      .dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();

    expect(store.closeEditor).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).not.toContain('Version history');
  });

  it('ignores a dismissed history request after the panel is reopened', async () => {
    const oldVersion = version('old-version', 'Old response');
    const newVersion = version('new-version', 'New response');
    let resolveOld:
      | ((page: { items: NoteVersionSummary[]; page: { size: number; hasNext: boolean } }) => void)
      | undefined;
    store.listNoteVersions
      .mockReturnValueOnce(
        new Promise((resolve) => {
          resolveOld = resolve;
        }),
      )
      .mockResolvedValueOnce({
        items: [summary(newVersion)],
        page: { size: 1, hasNext: false },
      });
    store.getNoteVersion.mockImplementation(async (_noteId: string, versionId: string) =>
      versionId === newVersion.id ? newVersion : oldVersion,
    );
    const component = fixture.componentInstance as unknown as {
      openHistory(): Promise<void>;
      selectedVersion(): NoteVersion | null;
    };

    const firstOpen = component.openHistory();
    await Promise.resolve();
    fixture.nativeElement
      .querySelector('dialog')
      .dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));

    await component.openHistory();
    expect(component.selectedVersion()?.id).toBe(newVersion.id);

    resolveOld?.({
      items: [summary(oldVersion)],
      page: { size: 1, hasNext: false },
    });
    await firstOpen;

    expect(component.selectedVersion()?.id).toBe(newVersion.id);
  });

  it('offers copy, reload, and explicit overwrite after a stale save', async () => {
    store.saveNote.mockRejectedValueOnce(new HttpErrorResponse({ status: 409 }));
    const title = fixture.nativeElement.querySelector(
      '[aria-label="Note title"]',
    ) as HTMLInputElement;
    title.value = 'Local draft';
    title.dispatchEvent(new Event('input'));
    await vi.advanceTimersByTimeAsync(500);
    fixture.detectChanges();

    const actions = Array.from(
      fixture.nativeElement.querySelectorAll('.editor__conflict button'),
    ).map((button) => (button as HTMLButtonElement).textContent?.trim());
    expect(actions).toEqual(['Copy local draft', 'Reload server', 'Overwrite with draft']);
  });
});
