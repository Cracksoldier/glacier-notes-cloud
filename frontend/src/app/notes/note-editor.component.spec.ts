import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { ContentColor } from '../shared/generated-api/model/contentColor';
import type { ContentNote } from '../shared/generated-api/model/contentNote';
import { NoteType } from '../shared/generated-api/model/noteType';
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

describe('NoteEditorComponent', () => {
  let fixture: ComponentFixture<NoteEditorComponent>;
  const store = {
    labels: signal([]),
    notebooks: signal([]),
    saveNote: vi.fn(),
    closeEditor: vi.fn(),
    refresh: vi.fn(),
  };

  beforeEach(async () => {
    vi.useFakeTimers();
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
    TestBed.configureTestingModule({ providers: [{ provide: NotesStore, useValue: store }] });
    fixture = TestBed.createComponent(NoteEditorComponent);
    fixture.componentRef.setInput('initialNote', note);
    fixture.detectChanges();
  });

  afterEach(() => vi.useRealTimers());

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
  });
});
