import { computed, Injectable, inject, signal } from '@angular/core';

import { ProblemService } from '../core/problem.service';
import { ContentColor } from '../shared/generated-api/model/contentColor';
import type { ContentNote } from '../shared/generated-api/model/contentNote';
import type { LabelView } from '../shared/generated-api/model/labelView';
import type { NotebookView } from '../shared/generated-api/model/notebookView';
import type { NoteSummary } from '../shared/generated-api/model/noteSummary';
import { NoteType } from '../shared/generated-api/model/noteType';
import type { NoteUpdate } from '../shared/generated-api/model/noteUpdate';
import { NotesDataAccess, type NotesView } from './notes-data-access';

@Injectable({ providedIn: 'root' })
export class NotesStore {
  private readonly api = inject(NotesDataAccess);
  private readonly problems = inject(ProblemService);

  readonly notebooks = signal<NotebookView[]>([]);
  readonly labels = signal<LabelView[]>([]);
  readonly notes = signal<NoteSummary[]>([]);
  readonly view = signal<NotesView | null>(null);
  readonly loading = signal(false);
  readonly loadingMore = signal(false);
  readonly nextCursor = signal<string | null>(null);
  readonly editor = signal<ContentNote | null>(null);
  readonly editorLoading = signal(false);

  readonly defaultNotebook = computed(
    () => this.notebooks().find((notebook) => notebook.defaultNotebook) ?? null,
  );
  readonly pinned = computed(() => this.notes().filter((note) => note.pinned));
  readonly others = computed(() => this.notes().filter((note) => !note.pinned));

  private requestSequence = 0;

  async initialize(): Promise<void> {
    try {
      const [notebooks, labels] = await Promise.all([
        this.api.listNotebooks(),
        this.api.listLabels(),
      ]);
      this.notebooks.set(notebooks);
      this.labels.set(labels);
    } catch (error) {
      this.problems.report(error);
      throw error;
    }
  }

  async loadView(view: NotesView): Promise<void> {
    const sequence = ++this.requestSequence;
    this.view.set(view);
    this.loading.set(true);
    try {
      const page = await this.api.listNotes(view);
      if (sequence !== this.requestSequence) return;
      this.notes.set(page.items);
      this.nextCursor.set(page.page.nextCursor ?? null);
    } catch (error) {
      if (sequence === this.requestSequence) this.problems.report(error);
    } finally {
      if (sequence === this.requestSequence) this.loading.set(false);
    }
  }

  async refresh(): Promise<void> {
    const view = this.view();
    if (!view || this.editor()) return;
    await Promise.all([this.reloadReferences(), this.loadView(view)]);
  }

  async loadMore(): Promise<void> {
    const view = this.view();
    const cursor = this.nextCursor();
    if (!view || !cursor || this.loadingMore()) return;
    this.loadingMore.set(true);
    try {
      const page = await this.api.listNotes(view, cursor);
      this.notes.update((current) => [
        ...current,
        ...page.items.filter((item) => !current.some((existing) => existing.id === item.id)),
      ]);
      this.nextCursor.set(page.page.nextCursor ?? null);
    } catch (error) {
      this.problems.report(error);
    } finally {
      this.loadingMore.set(false);
    }
  }

  async createNotebook(name: string): Promise<void> {
    if (!name.trim()) return;
    try {
      await this.api.createNotebook({ name: name.trim(), color: ContentColor.Default });
      await this.reloadReferences();
    } catch (error) {
      this.problems.report(error);
    }
  }

  async renameNotebook(notebook: NotebookView, name: string): Promise<void> {
    if (!name.trim() || name.trim() === notebook.name) return;
    try {
      await this.api.updateNotebook(notebook.id, { name: name.trim(), version: notebook.version });
      await this.reloadReferences();
    } catch (error) {
      this.problems.report(error);
    }
  }

  async reorderNotebook(notebook: NotebookView, direction: -1 | 1): Promise<void> {
    const list = this.notebooks();
    const index = list.findIndex((item) => item.id === notebook.id);
    const target = index + direction;
    if (index < 0 || target < 0 || target >= list.length) return;
    const reordered = [...list];
    [reordered[index], reordered[target]] = [reordered[target], reordered[index]];
    try {
      this.notebooks.set(
        await this.api.reorderNotebooks({
          notebooks: reordered.map((item) => ({ id: item.id, version: item.version })),
        }),
      );
    } catch (error) {
      this.notebooks.set(list);
      this.problems.report(error);
    }
  }

  async deleteNotebook(
    notebook: NotebookView,
    strategy: 'MOVE_TO_DEFAULT' | 'TRASH_NOTES',
  ): Promise<void> {
    try {
      await this.api.deleteNotebook(notebook.id, notebook.version, strategy);
      await this.reloadReferences();
      const view = this.view();
      if (view?.kind === 'notebook' && view.id === notebook.id) {
        const fallback = this.defaultNotebook();
        if (fallback) await this.loadView({ kind: 'notebook', id: fallback.id });
      }
    } catch (error) {
      this.problems.report(error);
    }
  }

  async createLabel(name: string): Promise<void> {
    if (!name.trim()) return;
    try {
      await this.api.createLabel({ name: name.trim() });
      await this.reloadReferences();
    } catch (error) {
      this.problems.report(error);
    }
  }

  async renameLabel(label: LabelView, name: string): Promise<void> {
    if (!name.trim() || name.trim() === label.name) return;
    try {
      await this.api.updateLabel(label.id, { name: name.trim(), version: label.version });
      await this.reloadReferences();
    } catch (error) {
      this.problems.report(error);
    }
  }

  async deleteLabel(label: LabelView): Promise<void> {
    try {
      await this.api.deleteLabel(label.id, label.version);
      await this.reloadReferences();
      await this.refresh();
    } catch (error) {
      this.problems.report(error);
    }
  }

  async createNote(noteType: NoteType): Promise<void> {
    const view = this.view();
    const notebookId = view?.kind === 'notebook' ? view.id : this.defaultNotebook()?.id;
    if (!notebookId) return;
    try {
      const note = await this.api.createNote({
        notebookId,
        noteType,
        title: '',
        content: '',
        checklistItems: [],
        pinned: false,
        archived: false,
        color: ContentColor.Default,
        labelIds: [],
        imageIds: [],
      });
      await this.loadView(this.view() ?? { kind: 'notebook', id: notebookId });
      this.editor.set(note);
    } catch (error) {
      this.problems.report(error);
    }
  }

  async openNote(id: string): Promise<void> {
    this.editorLoading.set(true);
    try {
      this.editor.set(await this.api.getNote(id));
    } catch (error) {
      this.problems.report(error);
    } finally {
      this.editorLoading.set(false);
    }
  }

  closeEditor(): void {
    this.editor.set(null);
  }

  async saveNote(note: ContentNote, input: NoteUpdate): Promise<ContentNote> {
    const updated = await this.api.updateNote(note.id, input);
    this.editor.set(updated);
    this.replaceSummary(updated);
    return updated;
  }

  async overwriteNote(note: ContentNote, input: Omit<NoteUpdate, 'version'>): Promise<ContentNote> {
    const latest = await this.api.getNote(note.id);
    return this.saveNote(latest, { ...input, version: latest.version });
  }

  async reloadEditorNote(id: string): Promise<ContentNote> {
    const latest = await this.api.getNote(id);
    this.editor.set(latest);
    this.replaceSummary(latest);
    return latest;
  }

  async convertEditorNote(note: ContentNote): Promise<ContentNote> {
    const converted = await this.api.convertNote(note);
    this.editor.set(converted);
    this.replaceSummary(converted);
    return converted;
  }

  async moveEditorNote(note: ContentNote, notebookId: string): Promise<ContentNote> {
    const moved = await this.api.moveNote(note.id, notebookId, note.version);
    this.editor.set(moved);
    await this.reloadReferences();
    return moved;
  }

  async trashEditorNote(note: ContentNote): Promise<void> {
    await this.api.trashNote(note.id, note.version);
    this.closeEditor();
    await this.refreshAfterMutation();
  }

  async toggleChecklistItem(summary: NoteSummary, itemId: string): Promise<void> {
    try {
      const note = await this.api.getNote(summary.id);
      await this.api.updateNote(note.id, {
        ...this.updateFrom(note),
        checklistItems: note.checklistItems.map((item) => ({
          id: item.id,
          text: item.text,
          checked: item.id === itemId ? !item.checked : item.checked,
        })),
      });
      await this.refreshAfterMutation();
    } catch (error) {
      this.problems.report(error);
    }
  }

  async togglePin(summary: NoteSummary): Promise<void> {
    await this.mutateSummary(summary, (note) => ({
      ...this.updateFrom(note),
      pinned: !note.pinned,
    }));
  }

  async setArchived(summary: NoteSummary, archived: boolean): Promise<void> {
    await this.mutateSummary(summary, (note) => ({ ...this.updateFrom(note), archived }));
  }

  async setColor(summary: NoteSummary, color: ContentColor): Promise<void> {
    await this.mutateSummary(summary, (note) => ({ ...this.updateFrom(note), color }));
  }

  async moveNote(summary: NoteSummary, notebookId: string): Promise<void> {
    try {
      await this.api.moveNote(summary.id, notebookId, summary.version);
      await this.refreshAfterMutation();
    } catch (error) {
      this.problems.report(error);
    }
  }

  async trashNote(summary: NoteSummary): Promise<void> {
    try {
      await this.api.trashNote(summary.id, summary.version);
      await this.refreshAfterMutation();
    } catch (error) {
      this.problems.report(error);
    }
  }

  async restoreNote(summary: NoteSummary): Promise<void> {
    try {
      await this.api.restoreNote(summary.id, summary.version);
      await this.refreshAfterMutation();
    } catch (error) {
      this.problems.report(error);
    }
  }

  async purgeNote(summary: NoteSummary): Promise<void> {
    try {
      await this.api.purgeNote(summary.id, summary.version);
      await this.refreshAfterMutation();
    } catch (error) {
      this.problems.report(error);
    }
  }

  async emptyTrash(): Promise<void> {
    try {
      await this.api.emptyTrash();
      await this.refreshAfterMutation();
    } catch (error) {
      this.problems.report(error);
    }
  }

  private async mutateSummary(
    summary: NoteSummary,
    update: (note: ContentNote) => NoteUpdate,
  ): Promise<void> {
    try {
      const note = await this.api.getNote(summary.id);
      await this.api.updateNote(note.id, update(note));
      await this.refreshAfterMutation();
    } catch (error) {
      this.problems.report(error);
    }
  }

  private updateFrom(note: ContentNote): NoteUpdate {
    return {
      title: note.title,
      content: note.content,
      checklistItems: note.checklistItems.map((item) => ({
        id: item.id,
        text: item.text,
        checked: item.checked,
      })),
      pinned: note.pinned,
      archived: note.archived,
      color: note.color,
      labelIds: note.labelIds,
      imageIds: note.imageIds,
      version: note.version,
    };
  }

  private async reloadReferences(): Promise<void> {
    const [notebooks, labels] = await Promise.all([
      this.api.listNotebooks(),
      this.api.listLabels(),
    ]);
    this.notebooks.set(notebooks);
    this.labels.set(labels);
  }

  private async refreshAfterMutation(): Promise<void> {
    await this.reloadReferences();
    const view = this.view();
    if (view) await this.loadView(view);
  }

  private replaceSummary(note: ContentNote): void {
    this.notes.update((items) =>
      items.map((item) =>
        item.id === note.id
          ? {
              id: note.id,
              notebookId: note.notebookId,
              noteType: note.noteType,
              title: note.title,
              preview: note.content.slice(0, 240),
              checklistPreview: note.checklistItems.slice(0, 5),
              pinned: note.pinned,
              archived: note.archived,
              color: note.color,
              labelIds: note.labelIds,
              imageIds: note.imageIds,
              deletedAt: note.deletedAt,
              createdAt: note.createdAt,
              updatedAt: note.updatedAt,
              version: note.version,
            }
          : item,
      ),
    );
  }
}
