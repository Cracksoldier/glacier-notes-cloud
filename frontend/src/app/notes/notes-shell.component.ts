import { Component, computed, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { filter, Subscription } from 'rxjs';

import { AuthStore } from '../core/auth.store';
import { ProblemService } from '../core/problem.service';
import { ThemeService } from '../core/theme.service';
import type { LabelView } from '../shared/generated-api/model/labelView';
import type { NotebookView } from '../shared/generated-api/model/notebookView';
import { NoteType } from '../shared/generated-api/model/noteType';
import { NoteCardComponent } from './note-card.component';
import { NoteEditorComponent } from './note-editor.component';
import { NotesStore } from './notes.store';
import type { NotesView } from './notes-data-access';

@Component({
  selector: 'app-notes-shell',
  imports: [RouterLink, RouterOutlet, NoteCardComponent, NoteEditorComponent],
  templateUrl: './notes-shell.component.html',
  styleUrl: './notes-shell.component.css',
})
export class NotesShellComponent implements OnInit, OnDestroy {
  protected readonly store = inject(NotesStore);
  protected readonly auth = inject(AuthStore);
  protected readonly theme = inject(ThemeService);
  protected readonly problems = inject(ProblemService);
  private readonly router = inject(Router);

  protected readonly NoteType = NoteType;
  protected readonly drawerOpen = signal(false);
  protected readonly notebookCreating = signal(false);
  protected readonly notebookEditing = signal<string | null>(null);
  protected readonly labelCreating = signal(false);
  protected readonly labelEditing = signal<string | null>(null);
  protected readonly notebookDelete = signal<NotebookView | null>(null);
  protected readonly notebookDeleteStrategy = signal<'MOVE_TO_DEFAULT' | 'TRASH_NOTES'>(
    'MOVE_TO_DEFAULT',
  );
  protected readonly overlay = signal<'shortcuts' | 'settings' | 'transfer' | null>(null);

  protected readonly viewTitle = computed(() => {
    const view = this.store.view();
    if (!view) return 'Notes';
    if (view.kind === 'archive') return 'Archive';
    if (view.kind === 'trash') return 'Trash';
    if (view.kind === 'label')
      return this.store.labels().find((label) => label.id === view.id)?.name ?? 'Label';
    return this.store.notebooks().find((notebook) => notebook.id === view.id)?.name ?? 'Notebook';
  });

  private readonly subscriptions = new Subscription();
  private readonly focusHandler = () => void this.store.refresh();
  private readonly shortcutHandler = (event: KeyboardEvent) => this.onShortcut(event);

  async ngOnInit(): Promise<void> {
    this.subscriptions.add(
      this.router.events
        .pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd))
        .subscribe(() => {
          void this.activateRoute();
        }),
    );
    window.addEventListener('focus', this.focusHandler);
    document.addEventListener('keydown', this.shortcutHandler, true);
    await this.store.initialize();
    await this.activateRoute();
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
    window.removeEventListener('focus', this.focusHandler);
    document.removeEventListener('keydown', this.shortcutHandler, true);
  }

  protected closeDrawer(): void {
    this.drawerOpen.set(false);
  }

  protected navigateTo(commands: string[]): void {
    void this.router.navigate(commands);
  }

  protected async createNotebook(value: string): Promise<void> {
    this.notebookCreating.set(false);
    await this.store.createNotebook(value);
  }

  protected async renameNotebook(notebook: NotebookView, value: string): Promise<void> {
    this.notebookEditing.set(null);
    await this.store.renameNotebook(notebook, value);
  }

  protected async createLabel(value: string): Promise<void> {
    this.labelCreating.set(false);
    await this.store.createLabel(value);
  }

  protected async renameLabel(label: LabelView, value: string): Promise<void> {
    this.labelEditing.set(null);
    await this.store.renameLabel(label, value);
  }

  protected deleteLabel(label: LabelView): void {
    if (window.confirm(`Delete label “${label.name}”? Notes will be kept.`)) {
      void this.store.deleteLabel(label);
    }
  }

  protected async confirmNotebookDelete(): Promise<void> {
    const notebook = this.notebookDelete();
    if (!notebook) return;
    await this.store.deleteNotebook(notebook, this.notebookDeleteStrategy());
    this.notebookDelete.set(null);
    const fallback = this.store.defaultNotebook();
    if (fallback) void this.router.navigate(['/notes/notebooks', fallback.id]);
  }

  protected emptyTrash(): void {
    if (window.confirm('Permanently delete every note in Trash? This cannot be undone.')) {
      void this.store.emptyTrash();
    }
  }

  protected notebookActive(id: string): boolean {
    const view = this.store.view();
    return view?.kind === 'notebook' && view.id === id;
  }

  protected labelActive(id: string): boolean {
    const view = this.store.view();
    return view?.kind === 'label' && view.id === id;
  }

  protected logout(): void {
    this.auth.logout().subscribe({
      next: () => void this.router.navigate(['/login']),
      error: () => {
        this.auth.clear();
        void this.router.navigate(['/login']);
      },
    });
  }

  private async activateRoute(): Promise<void> {
    const view = this.viewFromUrl(this.router.url);
    if (!view) {
      const notebook = this.store.defaultNotebook();
      if (notebook)
        await this.router.navigate(['/notes/notebooks', notebook.id], { replaceUrl: true });
      return;
    }
    if (this.sameView(view, this.store.view())) return;
    this.closeDrawer();
    await this.store.loadView(view);
  }

  private viewFromUrl(url: string): NotesView | null {
    const path = url.split(/[?#]/)[0];
    if (path === '/notes/archive') return { kind: 'archive' };
    if (path === '/notes/trash') return { kind: 'trash' };
    const notebook = path.match(/^\/notes\/notebooks\/([^/]+)$/);
    if (notebook) return { kind: 'notebook', id: decodeURIComponent(notebook[1]) };
    const label = path.match(/^\/notes\/labels\/([^/]+)$/);
    if (label) return { kind: 'label', id: decodeURIComponent(label[1]) };
    return null;
  }

  private sameView(left: NotesView, right: NotesView | null): boolean {
    if (!right || left.kind !== right.kind) return false;
    if (left.kind === 'notebook' && right.kind === 'notebook') return left.id === right.id;
    if (left.kind === 'label' && right.kind === 'label') return left.id === right.id;
    return true;
  }

  private onShortcut(event: KeyboardEvent): void {
    if (!(event.ctrlKey || event.metaKey) || event.altKey || this.store.editor()) return;
    const key = event.key.toLowerCase();
    if (key === 'n') {
      event.preventDefault();
      void this.store.createNote(event.shiftKey ? NoteType.Checklist : NoteType.Text);
    } else if (key === 'e') {
      event.preventDefault();
      this.overlay.set('transfer');
    } else if (key === ',') {
      event.preventDefault();
      this.overlay.set('settings');
    } else if (key === '/') {
      event.preventDefault();
      this.overlay.set('shortcuts');
    }
  }
}
