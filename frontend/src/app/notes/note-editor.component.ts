import {
  AfterViewInit,
  Component,
  ElementRef,
  inject,
  input,
  OnDestroy,
  OnInit,
  signal,
  viewChild,
} from '@angular/core';

import { ProblemService } from '../core/problem.service';
import type { ChecklistItemInput } from '../shared/generated-api/model/checklistItemInput';
import { ContentColor } from '../shared/generated-api/model/contentColor';
import type { ContentNote } from '../shared/generated-api/model/contentNote';
import type { NoteUpdate } from '../shared/generated-api/model/noteUpdate';
import { MarkdownService } from './markdown.service';
import { NotesStore } from './notes.store';

type SaveState = 'saved' | 'dirty' | 'saving' | 'error' | 'conflict';
type ToolbarAction =
  | 'bold'
  | 'italic'
  | 'h1'
  | 'h2'
  | 'ul'
  | 'ol'
  | 'link'
  | 'code'
  | 'quote'
  | 'table';

interface DraftItem {
  key: string;
  id?: string;
  text: string;
  checked: boolean;
}

const SAVE_DELAY = 500;

@Component({
  selector: 'app-note-editor',
  templateUrl: './note-editor.component.html',
  styleUrl: './note-editor.component.css',
})
export class NoteEditorComponent implements OnInit, AfterViewInit, OnDestroy {
  readonly initialNote = input.required<ContentNote>();

  protected readonly store = inject(NotesStore);
  protected readonly markdown = inject(MarkdownService);
  protected readonly problems = inject(ProblemService);

  protected readonly note = signal<ContentNote | null>(null);
  protected readonly title = signal('');
  protected readonly content = signal('');
  protected readonly items = signal<DraftItem[]>([]);
  protected readonly pinned = signal(false);
  protected readonly archived = signal(false);
  protected readonly color = signal<ContentColor>(ContentColor.Default);
  protected readonly labelIds = signal<string[]>([]);
  protected readonly preview = signal(false);
  protected readonly saveState = signal<SaveState>('saved');
  protected readonly saveMessage = signal('Saved');
  protected readonly colors = Object.values(ContentColor);
  protected readonly toolbar: { action: ToolbarAction; label: string; icon?: string }[] = [
    { action: 'bold', label: 'Bold', icon: 'fa-bold' },
    { action: 'italic', label: 'Italic', icon: 'fa-italic' },
    { action: 'h1', label: 'Heading 1' },
    { action: 'h2', label: 'Heading 2' },
    { action: 'ul', label: 'Bulleted list', icon: 'fa-list-ul' },
    { action: 'ol', label: 'Numbered list', icon: 'fa-list-ol' },
    { action: 'link', label: 'Link', icon: 'fa-link' },
    { action: 'code', label: 'Code', icon: 'fa-code' },
    { action: 'quote', label: 'Blockquote', icon: 'fa-quote-left' },
    { action: 'table', label: 'Table', icon: 'fa-table' },
  ];

  private readonly dialogRef = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  private readonly textareaRef = viewChild<ElementRef<HTMLTextAreaElement>>('textarea');
  private timer: ReturnType<typeof setTimeout> | null = null;
  private dirty = false;
  private saving: Promise<boolean> | null = null;
  private closing = false;
  private itemSequence = 0;
  private conflictDraft: Omit<NoteUpdate, 'version'> | null = null;

  private readonly beforeUnload = (event: BeforeUnloadEvent): void => {
    if (this.dirty || this.saving) event.preventDefault();
  };

  ngOnInit(): void {
    this.applyNote(this.initialNote());
    window.addEventListener('beforeunload', this.beforeUnload);
  }

  ngAfterViewInit(): void {
    this.dialogRef().nativeElement.showModal();
  }

  ngOnDestroy(): void {
    if (this.timer) clearTimeout(this.timer);
    window.removeEventListener('beforeunload', this.beforeUnload);
    this.dialogRef().nativeElement.close();
  }

  protected changed(): void {
    this.dirty = true;
    this.saveState.set('dirty');
    this.saveMessage.set('Unsaved changes');
    if (this.timer) clearTimeout(this.timer);
    this.timer = setTimeout(() => void this.flush(), SAVE_DELAY);
  }

  protected setTitle(value: string): void {
    this.title.set(value);
    this.changed();
  }

  protected setContent(value: string): void {
    this.content.set(value);
    this.changed();
  }

  protected toggleLabel(id: string): void {
    this.labelIds.update((ids) =>
      ids.includes(id) ? ids.filter((item) => item !== id) : [...ids, id],
    );
    this.changed();
  }

  protected addItem(): void {
    this.items.update((items) => [
      ...items,
      { key: `new-${this.itemSequence++}`, text: '', checked: false },
    ]);
    this.changed();
  }

  protected updateItem(index: number, text: string): void {
    this.items.update((items) => items.map((item, i) => (i === index ? { ...item, text } : item)));
    this.changed();
  }

  protected toggleItem(index: number): void {
    this.items.update((items) =>
      items.map((item, i) => (i === index ? { ...item, checked: !item.checked } : item)),
    );
    this.changed();
  }

  protected removeItem(index: number): void {
    this.items.update((items) => items.filter((_, i) => i !== index));
    this.changed();
  }

  protected moveItem(index: number, offset: -1 | 1): void {
    const target = index + offset;
    const items = [...this.items()];
    if (target < 0 || target >= items.length) return;
    [items[index], items[target]] = [items[target], items[index]];
    this.items.set(items);
    this.changed();
  }

  protected async close(): Promise<void> {
    if (this.closing) return;
    this.closing = true;
    const saved = await this.flush();
    if (saved) {
      this.store.closeEditor();
      await this.store.refresh();
    }
    this.closing = false;
  }

  protected async retry(): Promise<void> {
    this.dirty = true;
    await this.flush();
  }

  protected async keepDraft(): Promise<void> {
    const note = this.note();
    if (!note || !this.conflictDraft) return;
    this.saveState.set('saving');
    this.saveMessage.set('Overwriting with your draft…');
    try {
      const updated = await this.store.overwriteNote(note, this.conflictDraft);
      this.applyNote(updated);
      this.conflictDraft = null;
    } catch (error) {
      this.fail(error);
    }
  }

  protected async loadServerCopy(): Promise<void> {
    const note = this.note();
    if (!note) return;
    try {
      this.applyNote(await this.store.reloadEditorNote(note.id));
      this.conflictDraft = null;
    } catch (error) {
      this.fail(error);
    }
  }

  protected async convert(): Promise<void> {
    if (!(await this.flush())) return;
    const note = this.note();
    if (!note) return;
    try {
      this.applyNote(await this.store.convertEditorNote(note));
    } catch (error) {
      this.fail(error);
    }
  }

  protected async moveTo(notebookId: string): Promise<void> {
    if (!(await this.flush())) return;
    const note = this.note();
    if (!note || note.notebookId === notebookId) return;
    try {
      this.applyNote(await this.store.moveEditorNote(note, notebookId));
    } catch (error) {
      this.fail(error);
    }
  }

  protected async trash(): Promise<void> {
    if (!window.confirm('Move this note to trash?') || !(await this.flush())) return;
    const note = this.note();
    if (!note) return;
    try {
      await this.store.trashEditorNote(note);
    } catch (error) {
      this.fail(error);
    }
  }

  protected toolbarAction(action: ToolbarAction): void {
    const textarea = this.textareaRef()?.nativeElement;
    if (!textarea) return;
    const start = textarea.selectionStart;
    const end = textarea.selectionEnd;
    const value = this.content();
    const selected = value.slice(start, end);
    let insertion = selected;
    let selectionStart = start;
    let selectionEnd = end;
    const wrap = (marker: string) => {
      insertion = `${marker}${selected}${marker}`;
      selectionStart = start + marker.length;
      selectionEnd = selectionStart + selected.length;
    };
    if (action === 'bold') wrap('**');
    else if (action === 'italic') wrap('_');
    else if (action === 'code') wrap(selected.includes('\n') ? '```\n' : '`');
    else if (action === 'link') {
      insertion = `[${selected || 'link'}](https://)`;
      selectionStart = start + (selected || 'link').length + 3;
      selectionEnd = selectionStart + 8;
    } else if (action === 'table') {
      insertion = '| Column | Column |\n| --- | --- |\n| Value | Value |';
      selectionEnd = start + insertion.length;
    } else {
      const prefix =
        action === 'h1'
          ? '# '
          : action === 'h2'
            ? '## '
            : action === 'ul'
              ? '- '
              : action === 'ol'
                ? '1. '
                : '> ';
      insertion = (selected || 'Text')
        .split('\n')
        .map((line) => `${prefix}${line}`)
        .join('\n');
      selectionEnd = start + insertion.length;
    }
    this.content.set(`${value.slice(0, start)}${insertion}${value.slice(end)}`);
    this.changed();
    queueMicrotask(() => {
      textarea.focus();
      textarea.setSelectionRange(selectionStart, selectionEnd);
    });
  }

  protected onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      event.preventDefault();
      void this.close();
      return;
    }
    if (!(event.ctrlKey || event.metaKey)) return;
    if (event.key === 'Enter') {
      event.preventDefault();
      void this.close();
    } else if (event.key.toLowerCase() === 'b') {
      event.preventDefault();
      this.toolbarAction('bold');
    } else if (event.key.toLowerCase() === 'i') {
      event.preventDefault();
      this.toolbarAction('italic');
    }
  }

  protected onCancel(event: Event): void {
    event.preventDefault();
    void this.close();
  }

  protected onBackdrop(event: MouseEvent): void {
    if (event.target === this.dialogRef().nativeElement) void this.close();
  }

  private flush(): Promise<boolean> {
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = null;
    }
    if (this.saving)
      return this.saving.then(() => (this.dirty ? this.flush() : this.saveState() === 'saved'));
    if (!this.dirty) return Promise.resolve(this.saveState() === 'saved');
    const note = this.note();
    if (!note) return Promise.resolve(false);
    this.dirty = false;
    this.saveState.set('saving');
    this.saveMessage.set('Saving…');
    const input = this.draft(note.version);
    this.saving = this.store
      .saveNote(note, input)
      .then((updated) => {
        this.note.set(updated);
        this.items.set(this.itemsFrom(updated));
        this.saveState.set('saved');
        this.saveMessage.set('Saved');
        return true;
      })
      .catch((error: unknown) => {
        this.dirty = true;
        if (this.problems.isConflict(error)) {
          this.conflictDraft = this.draftWithoutVersion();
          this.saveState.set('conflict');
          this.saveMessage.set('Conflict: this note changed elsewhere.');
        } else {
          this.fail(error);
        }
        return false;
      })
      .finally(() => {
        this.saving = null;
      });
    return this.saving;
  }

  private draft(version: number): NoteUpdate {
    return { ...this.draftWithoutVersion(), version };
  }

  private draftWithoutVersion(): Omit<NoteUpdate, 'version'> {
    return {
      title: this.title(),
      content: this.note()?.noteType === 'TEXT' ? this.content() : '',
      checklistItems: this.note()?.noteType === 'CHECKLIST' ? this.checklistInput() : [],
      pinned: this.pinned(),
      archived: this.archived(),
      color: this.color(),
      labelIds: this.labelIds(),
    };
  }

  private checklistInput(): ChecklistItemInput[] {
    return this.items().map((item) => ({
      ...(item.id ? { id: item.id } : {}),
      text: item.text,
      checked: item.checked,
    }));
  }

  private applyNote(note: ContentNote): void {
    this.note.set(note);
    this.title.set(note.title);
    this.content.set(note.content);
    this.items.set(this.itemsFrom(note));
    this.pinned.set(note.pinned);
    this.archived.set(note.archived);
    this.color.set(note.color);
    this.labelIds.set(note.labelIds);
    this.dirty = false;
    this.saveState.set('saved');
    this.saveMessage.set('Saved');
  }

  private itemsFrom(note: ContentNote): DraftItem[] {
    return note.checklistItems.map((item) => ({
      key: item.id,
      id: item.id,
      text: item.text,
      checked: item.checked,
    }));
  }

  private fail(error: unknown): void {
    this.saveState.set('error');
    this.saveMessage.set(this.problems.message(error));
  }
}
