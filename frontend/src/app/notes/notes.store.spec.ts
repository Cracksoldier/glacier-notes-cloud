import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { ProblemService } from '../core/problem.service';
import { ContentColor } from '../shared/generated-api/model/contentColor';
import type { ContentNote } from '../shared/generated-api/model/contentNote';
import type { NotebookView } from '../shared/generated-api/model/notebookView';
import type { NoteSummary } from '../shared/generated-api/model/noteSummary';
import { NoteType } from '../shared/generated-api/model/noteType';
import { NotesStore } from './notes.store';
import { NotesDataAccess } from './notes-data-access';

const notebook: NotebookView = {
  id: '11111111-1111-4111-8111-111111111111',
  name: 'Notes',
  color: ContentColor.Default,
  defaultNotebook: true,
  sortOrder: 0,
  noteCount: 2,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
  version: 0,
};

function summary(id: string): NoteSummary {
  return {
    id,
    notebookId: notebook.id,
    noteType: NoteType.Text,
    title: id,
    preview: '',
    checklistPreview: [],
    pinned: false,
    archived: false,
    color: ContentColor.Default,
    labelIds: [],
    imageIds: [],
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    version: 0,
  };
}

function content(id: string): ContentNote {
  return {
    ...summary(id),
    content: '',
    checklistItems: [],
  };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((complete) => {
    resolve = complete;
  });
  return { promise, resolve };
}

describe('NotesStore', () => {
  const api = {
    listNotebooks: vi.fn(),
    listLabels: vi.fn(),
    listNotes: vi.fn(),
    searchNotes: vi.fn(),
    createNote: vi.fn(),
    getNote: vi.fn(),
    updateNote: vi.fn(),
    trashNote: vi.fn(),
  };

  beforeEach(() => {
    vi.resetAllMocks();
    api.listNotebooks.mockResolvedValue([notebook]);
    api.listLabels.mockResolvedValue([]);
    TestBed.configureTestingModule({ providers: [{ provide: NotesDataAccess, useValue: api }] });
  });

  it('reports initialization failures without rejecting the lifecycle hook', async () => {
    const failure = new Error('reference load failed');
    api.listNotebooks.mockRejectedValueOnce(failure);
    const problems = TestBed.inject(ProblemService);
    const report = vi.spyOn(problems, 'report');
    const store = TestBed.inject(NotesStore);

    await expect(store.initialize()).resolves.toBeUndefined();

    expect(report).toHaveBeenCalledWith(failure);
  });

  it('loads cursor pages without duplicating notes', async () => {
    api.listNotes
      .mockResolvedValueOnce({
        items: [summary('a')],
        page: { size: 1, hasNext: true, nextCursor: 'next' },
      })
      .mockResolvedValueOnce({
        items: [summary('a'), summary('b')],
        page: { size: 2, hasNext: false },
      });
    const store = TestBed.inject(NotesStore);

    await store.initialize();
    await store.loadView({ kind: 'notebook', id: notebook.id });
    await store.loadMore();

    expect(api.listNotes).toHaveBeenLastCalledWith({ kind: 'notebook', id: notebook.id }, 'next');
    expect(store.notes().map((note) => note.id)).toEqual(['a', 'b']);
    expect(store.nextCursor()).toBeNull();
  });

  it('creates a generated-contract note in the selected notebook and opens it', async () => {
    const created = content('created');
    api.listNotes.mockResolvedValue({ items: [], page: { size: 0, hasNext: false } });
    api.createNote.mockResolvedValue(created);
    const store = TestBed.inject(NotesStore);

    await store.initialize();
    await store.loadView({ kind: 'notebook', id: notebook.id });
    await store.createNote(NoteType.Text);

    expect(api.createNote).toHaveBeenCalledWith(
      expect.objectContaining({ notebookId: notebook.id, noteType: NoteType.Text }),
    );
    expect(store.editor()).toEqual(created);
  });

  it('toggles a checklist card without opening the editor', async () => {
    const checklistItem = {
      id: 'item-1',
      text: 'First item',
      checked: false,
      sortOrder: 0,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
      version: 0,
    };
    const checklistContent = {
      ...content('checklist'),
      noteType: NoteType.Checklist,
      checklistItems: [checklistItem],
    };
    const checklistSummary = {
      ...summary('checklist'),
      noteType: NoteType.Checklist,
      checklistPreview: [checklistItem],
    };
    api.listNotes.mockResolvedValue({
      items: [checklistSummary],
      page: { size: 1, hasNext: false },
    });
    api.getNote.mockResolvedValue(checklistContent);
    api.updateNote.mockResolvedValue({
      ...checklistContent,
      checklistItems: [{ ...checklistItem, checked: true }],
    });
    const store = TestBed.inject(NotesStore);

    await store.initialize();
    await store.loadView({ kind: 'notebook', id: notebook.id });
    await store.toggleChecklistItem(checklistSummary, 'item-1');

    expect(api.updateNote).toHaveBeenCalledWith(
      'checklist',
      expect.objectContaining({
        checklistItems: [{ id: 'item-1', text: 'First item', checked: true }],
      }),
    );
    expect(store.editor()).toBeNull();
  });

  it('serializes checklist updates before a following trash action', async () => {
    const checklistItem = {
      id: 'item-1',
      text: 'First item',
      checked: false,
      sortOrder: 0,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
      version: 0,
    };
    const original = {
      ...content('checklist'),
      noteType: NoteType.Checklist,
      checklistItems: [checklistItem],
    };
    const updated = {
      ...original,
      checklistItems: [{ ...checklistItem, checked: true }],
      version: 1,
    };
    const checklistSummary = {
      ...summary('checklist'),
      noteType: NoteType.Checklist,
      checklistPreview: [checklistItem],
    };
    const pendingUpdate = deferred<ContentNote>();
    api.getNote.mockResolvedValueOnce(original).mockResolvedValueOnce(updated);
    api.updateNote.mockReturnValueOnce(pendingUpdate.promise);
    api.trashNote.mockResolvedValue(undefined);
    const store = TestBed.inject(NotesStore);

    const toggle = store.toggleChecklistItem(checklistSummary, 'item-1');
    await vi.waitFor(() => expect(api.updateNote).toHaveBeenCalledOnce());
    const trash = store.trashNote(checklistSummary);
    await Promise.resolve();

    expect(api.trashNote).not.toHaveBeenCalled();

    pendingUpdate.resolve(updated);
    await Promise.all([toggle, trash]);

    expect(api.trashNote).toHaveBeenCalledWith('checklist', 1);
  });

  it('keeps ranked search order and continues search cursor pages', async () => {
    api.listNotes.mockResolvedValue({ items: [], page: { size: 0, hasNext: false } });
    api.searchNotes
      .mockResolvedValueOnce({
        items: [summary('ranked-first')],
        page: { size: 1, hasNext: true, nextCursor: 'search-next' },
      })
      .mockResolvedValueOnce({
        items: [summary('ranked-second')],
        page: { size: 1, hasNext: false },
      });
    const store = TestBed.inject(NotesStore);
    const filters = { archive: 'ALL' as const, trash: 'ACTIVE' as const, pinned: true };

    await store.initialize();
    await store.loadView({ kind: 'notebook', id: notebook.id });
    await store.search('glacier', filters);
    await store.loadMore();

    expect(api.searchNotes).toHaveBeenNthCalledWith(1, 'glacier', filters);
    expect(api.searchNotes).toHaveBeenNthCalledWith(2, 'glacier', filters, 'search-next');
    expect(store.notes().map((item) => item.id)).toEqual(['ranked-first', 'ranked-second']);
  });

  it('ignores a stale cursor page after navigating to another view', async () => {
    const stalePage = deferred<{
      items: NoteSummary[];
      page: { size: number; hasNext: boolean; nextCursor?: string };
    }>();
    api.listNotes
      .mockResolvedValueOnce({
        items: [summary('old-first')],
        page: { size: 1, hasNext: true, nextCursor: 'old-next' },
      })
      .mockReturnValueOnce(stalePage.promise)
      .mockResolvedValueOnce({
        items: [summary('new-first')],
        page: { size: 1, hasNext: true, nextCursor: 'new-next' },
      });
    const store = TestBed.inject(NotesStore);

    await store.loadView({ kind: 'archive' });
    const oldRequest = store.loadMore();
    await store.loadView({ kind: 'trash' });
    stalePage.resolve({
      items: [summary('old-late')],
      page: { size: 1, hasNext: false },
    });
    await oldRequest;

    expect(store.notes().map((item) => item.id)).toEqual(['new-first']);
    expect(store.nextCursor()).toBe('new-next');
  });

  it('ignores a stale search page after starting another search', async () => {
    const stalePage = deferred<{
      items: NoteSummary[];
      page: { size: number; hasNext: boolean; nextCursor?: string };
    }>();
    const filters = { archive: 'ALL' as const, trash: 'ACTIVE' as const };
    api.listNotes.mockResolvedValue({
      items: [],
      page: { size: 0, hasNext: false },
    });
    api.searchNotes
      .mockResolvedValueOnce({
        items: [summary('old-search')],
        page: { size: 1, hasNext: true, nextCursor: 'old-search-next' },
      })
      .mockReturnValueOnce(stalePage.promise)
      .mockResolvedValueOnce({
        items: [summary('new-search')],
        page: { size: 1, hasNext: false },
      });
    const store = TestBed.inject(NotesStore);

    await store.loadView({ kind: 'archive' });
    await store.search('old', filters);
    const oldRequest = store.loadMore();
    await Promise.resolve();
    expect(api.searchNotes).toHaveBeenCalledTimes(2);
    await store.search('new', filters);
    stalePage.resolve({
      items: [summary('old-search-late')],
      page: { size: 1, hasNext: false },
    });
    await oldRequest;

    expect(store.notes().map((item) => item.id)).toEqual(['new-search']);
    expect(store.nextCursor()).toBeNull();
  });
});
