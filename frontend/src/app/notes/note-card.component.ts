import { Component, computed, inject, input, signal } from '@angular/core';

import { ContentColor } from '../shared/generated-api/model/contentColor';
import type { NoteSummary } from '../shared/generated-api/model/noteSummary';
import { MarkdownService } from './markdown.service';
import { NotesStore } from './notes.store';

@Component({
  selector: 'app-note-card',
  templateUrl: './note-card.component.html',
  styleUrl: './note-card.component.css',
})
export class NoteCardComponent {
  readonly note = input.required<NoteSummary>();

  protected readonly store = inject(NotesStore);
  protected readonly markdown = inject(MarkdownService);

  protected readonly menu = signal<'color' | 'move' | null>(null);
  protected readonly preview = computed(() => this.markdown.render(this.note().preview));
  protected readonly labels = computed(() =>
    this.store.labels().filter((label) => this.note().labelIds.includes(label.id)),
  );
  protected readonly colors = Object.values(ContentColor);
  protected readonly trashed = computed(() => Boolean(this.note().deletedAt));
  protected readonly firstImage = computed(() => this.note().imageIds[0] ?? null);

  protected imageUrl(id: string): string {
    return `/api/v1/images/${id}/thumbnail`;
  }

  protected open(): void {
    if (!this.trashed()) void this.store.openNote(this.note().id);
  }

  protected toggleMenu(event: Event, menu: 'color' | 'move'): void {
    event.stopPropagation();
    this.menu.update((current) => (current === menu ? null : menu));
  }

  protected chooseColor(event: Event, color: ContentColor): void {
    event.stopPropagation();
    this.menu.set(null);
    void this.store.setColor(this.note(), color);
  }

  protected chooseNotebook(event: Event, notebookId: string): void {
    event.stopPropagation();
    this.menu.set(null);
    void this.store.moveNote(this.note(), notebookId);
  }

  protected openLabel(event: Event, labelId: string): void {
    event.stopPropagation();
    window.location.assign(`/notes/labels/${labelId}`);
  }

  protected onPreviewClick(event: MouseEvent): void {
    if ((event.target as HTMLElement).closest('a')) event.stopPropagation();
  }

  protected purge(event: Event): void {
    event.stopPropagation();
    if (window.confirm('Delete this note forever? This cannot be undone.')) {
      void this.store.purgeNote(this.note());
    }
  }
}
