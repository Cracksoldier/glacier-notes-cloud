import { AfterViewInit, Directive, ElementRef, OnDestroy, output } from '@angular/core';

const FOCUSABLE =
  'button:not([disabled]), a[href], input:not([disabled]):not([type="hidden"]), ' +
  'select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

@Directive({
  selector: '[appModalFocus]',
  host: {
    '(keydown)': 'onKeydown($event)',
  },
})
export class ModalFocusDirective implements AfterViewInit, OnDestroy {
  readonly modalDismiss = output<void>();

  private readonly host: HTMLElement;
  private readonly opener: HTMLElement | null;
  private readonly inerted: HTMLElement[] = [];

  constructor(element: ElementRef<HTMLElement>) {
    this.host = element.nativeElement;
    this.opener = document.activeElement instanceof HTMLElement ? document.activeElement : null;
  }

  ngAfterViewInit(): void {
    this.excludeBackground();
    document.addEventListener('focusin', this.keepFocusInside, true);
    this.focusableElements()[0]?.focus();
  }

  ngOnDestroy(): void {
    document.removeEventListener('focusin', this.keepFocusInside, true);
    for (const element of this.inerted) element.inert = false;
    if (this.opener?.isConnected) this.opener.focus();
  }

  protected onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      event.preventDefault();
      event.stopPropagation();
      this.modalDismiss.emit();
      return;
    }
    if (event.key !== 'Tab') return;
    const focusable = this.focusableElements();
    if (!focusable.length) {
      event.preventDefault();
      this.host.focus();
      return;
    }
    const first = focusable[0];
    const last = focusable.at(-1) as HTMLElement;
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  private readonly keepFocusInside = (event: FocusEvent): void => {
    if (!this.host.contains(event.target as Node)) this.focusableElements()[0]?.focus();
  };

  private focusableElements(): HTMLElement[] {
    return Array.from(this.host.querySelectorAll<HTMLElement>(FOCUSABLE)).filter(
      (element) => !element.hidden && !element.closest('[inert]'),
    );
  }

  private excludeBackground(): void {
    let current = this.host;
    while (current.parentElement) {
      const parent = current.parentElement;
      for (const sibling of Array.from(parent.children)) {
        if (sibling instanceof HTMLElement && sibling !== current && !sibling.inert) {
          sibling.inert = true;
          this.inerted.push(sibling);
        }
      }
      if (parent === document.body) break;
      current = parent;
    }
  }
}
