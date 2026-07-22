import { Injectable, inject } from '@angular/core';
import { DomSanitizer, type SafeHtml } from '@angular/platform-browser';
import DOMPurify from 'dompurify';
import { Marked, Renderer } from 'marked';

const renderer = new Renderer();
renderer.html = ({ text }) => escapeHtml(text);
renderer.image = ({ href, text, title }) => {
  const match = /^glacier-img:\/\/([0-9a-fA-F-]{36})$/.exec(href);
  if (!match) return escapeHtml(text || '');
  const caption = escapeHtml(text || 'Image');
  const titleAttribute = title ? ` title="${escapeHtml(title)}"` : '';
  return `<img src="/api/v1/images/${match[1]}" alt="${caption}"${titleAttribute} loading="lazy">`;
};

const marked = new Marked({ breaks: true, gfm: true, renderer });

@Injectable({ providedIn: 'root' })
export class MarkdownService {
  private readonly angularSanitizer = inject(DomSanitizer);

  render(value: string): SafeHtml {
    const html = marked.parse(value, { async: false }) as string;
    const clean = DOMPurify.sanitize(html, {
      FORBID_TAGS: ['style', 'form', 'input', 'button'],
      FORBID_ATTR: ['style'],
      ALLOWED_URI_REGEXP: /^(?:\/api\/v1\/images\/[0-9a-fA-F-]{36}|https?:|mailto:)/,
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
    for (const image of template.content.querySelectorAll('img')) {
      image.loading = 'lazy';
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
