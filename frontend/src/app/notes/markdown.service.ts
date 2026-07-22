import { Injectable, inject } from '@angular/core';
import { DomSanitizer, type SafeHtml } from '@angular/platform-browser';
import DOMPurify from 'dompurify';
import { Marked, Renderer } from 'marked';

const renderer = new Renderer();
renderer.html = ({ text }) => escapeHtml(text);

const marked = new Marked({ breaks: true, gfm: true, renderer });

@Injectable({ providedIn: 'root' })
export class MarkdownService {
  private readonly angularSanitizer = inject(DomSanitizer);

  render(value: string): SafeHtml {
    const html = marked.parse(value, { async: false }) as string;
    const clean = DOMPurify.sanitize(html, {
      FORBID_TAGS: ['style', 'form', 'input', 'button', 'img'],
      FORBID_ATTR: ['style'],
    });
    return this.angularSanitizer.bypassSecurityTrustHtml(this.secureLinks(clean));
  }

  renderInline(value: string): SafeHtml {
    const html = marked.parseInline(value, { async: false }) as string;
    const clean = DOMPurify.sanitize(html, {
      ALLOWED_TAGS: ['a', 'strong', 'em', 'del', 'code', 'br'],
      ALLOWED_ATTR: ['href', 'target', 'rel', 'title'],
    });
    return this.angularSanitizer.bypassSecurityTrustHtml(this.secureLinks(clean));
  }

  private secureLinks(clean: string): string {
    const template = document.createElement('template');
    template.innerHTML = clean;
    for (const link of template.content.querySelectorAll('a')) {
      link.target = '_blank';
      link.rel = 'noopener noreferrer';
    }
    return template.innerHTML;
  }
}

function escapeHtml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');
}
