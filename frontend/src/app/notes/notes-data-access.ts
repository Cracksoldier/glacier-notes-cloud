import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { LabelsService } from '../shared/generated-api/api/labels.service';
import { NotebooksService } from '../shared/generated-api/api/notebooks.service';
import { NotesService } from '../shared/generated-api/api/notes.service';
import type { ContentNote } from '../shared/generated-api/model/contentNote';
import type { LabelCreate } from '../shared/generated-api/model/labelCreate';
import type { LabelUpdate } from '../shared/generated-api/model/labelUpdate';
import type { NotebookCreate } from '../shared/generated-api/model/notebookCreate';
import type { NotebookReorder } from '../shared/generated-api/model/notebookReorder';
import type { NotebookUpdate } from '../shared/generated-api/model/notebookUpdate';
import type { NoteCreate } from '../shared/generated-api/model/noteCreate';
import type { NotePage } from '../shared/generated-api/model/notePage';
import { NoteType } from '../shared/generated-api/model/noteType';
import type { NoteUpdate } from '../shared/generated-api/model/noteUpdate';

export type NotesView =
  | { kind: 'notebook'; id: string }
  | { kind: 'label'; id: string }
  | { kind: 'archive' }
  | { kind: 'trash' };

@Injectable({ providedIn: 'root' })
export class NotesDataAccess {
  private readonly notebooks = inject(NotebooksService);
  private readonly labels = inject(LabelsService);
  private readonly notes = inject(NotesService);

  listNotebooks() {
    return firstValueFrom(this.notebooks.listNotebooks());
  }

  defaultNotebook() {
    return firstValueFrom(this.notebooks.getDefaultNotebook());
  }

  createNotebook(input: NotebookCreate) {
    return firstValueFrom(this.notebooks.createNotebook(input));
  }

  updateNotebook(id: string, input: NotebookUpdate) {
    return firstValueFrom(this.notebooks.updateNotebook(id, input));
  }

  reorderNotebooks(input: NotebookReorder) {
    return firstValueFrom(this.notebooks.reorderNotebooks(input));
  }

  deleteNotebook(id: string, version: number, strategy: 'MOVE_TO_DEFAULT' | 'TRASH_NOTES') {
    return firstValueFrom(this.notebooks.deleteNotebook(strategy, version, id));
  }

  listLabels() {
    return firstValueFrom(this.labels.listLabels());
  }

  createLabel(input: LabelCreate) {
    return firstValueFrom(this.labels.createLabel(input));
  }

  updateLabel(id: string, input: LabelUpdate) {
    return firstValueFrom(this.labels.updateLabel(id, input));
  }

  deleteLabel(id: string, version: number) {
    return firstValueFrom(this.labels.deleteLabel(version, id));
  }

  listNotes(view: NotesView, cursor?: string): Promise<NotePage> {
    const notebookId = view.kind === 'notebook' ? view.id : undefined;
    const labelId = view.kind === 'label' ? view.id : undefined;
    const archive = view.kind === 'archive' ? 'ARCHIVED' : view.kind === 'trash' ? 'ALL' : 'ACTIVE';
    const trash = view.kind === 'trash' ? 'TRASHED' : 'ACTIVE';
    return firstValueFrom(
      this.notes.listNotes(notebookId, labelId, undefined, undefined, archive, trash, cursor, 50),
    );
  }

  createNote(input: NoteCreate) {
    return firstValueFrom(this.notes.createNote(input));
  }

  getNote(id: string) {
    return firstValueFrom(this.notes.getNote(id));
  }

  updateNote(id: string, input: NoteUpdate) {
    return firstValueFrom(this.notes.updateNote(id, input));
  }

  moveNote(id: string, notebookId: string, version: number) {
    return firstValueFrom(this.notes.moveNote(id, { notebookId, version }));
  }

  convertNote(note: ContentNote) {
    return firstValueFrom(
      this.notes.convertNote(note.id, {
        targetType: note.noteType === NoteType.Text ? NoteType.Checklist : NoteType.Text,
        version: note.version,
      }),
    );
  }

  trashNote(id: string, version: number) {
    return firstValueFrom(this.notes.trashNote(id, { version }));
  }

  restoreNote(id: string, version: number) {
    return firstValueFrom(this.notes.restoreNote(id, { version }));
  }

  purgeNote(id: string, version: number) {
    return firstValueFrom(this.notes.purgeNote(version, id));
  }

  emptyTrash() {
    return firstValueFrom(this.notes.emptyTrash());
  }
}
