import { SecurityContext } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { DomSanitizer } from '@angular/platform-browser';

import { MarkdownService } from './markdown.service';

describe('MarkdownService', () => {
  let service: MarkdownService;
  let sanitizer: DomSanitizer;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(MarkdownService);
    sanitizer = TestBed.inject(DomSanitizer);
  });

  it('renders supported Markdown and isolates external links', () => {
    const html = sanitizer.sanitize(
      SecurityContext.HTML,
      service.render('# Heading\n\n**Bold** [link](https://example.com)'),
    );

    expect(html).toContain('<h1>Heading</h1>');
    expect(html).toContain('<strong>Bold</strong>');
    expect(html).toContain('target="_blank"');
    expect(html).toContain('rel="noopener noreferrer"');
  });

  it('does not execute or render raw HTML and dangerous URLs', () => {
    const html = sanitizer.sanitize(
      SecurityContext.HTML,
      service.render(
        '<img src=x onerror=alert(1)> <script>alert(1)</script> [x](javascript:alert(1))',
      ),
    );

    expect(html).not.toContain('<img');
    expect(html).not.toContain('<script');
    expect(html).not.toContain('javascript:');
  });

  it('restricts checklist Markdown to inline elements', () => {
    const html = sanitizer.sanitize(
      SecurityContext.HTML,
      service.renderInline('**bold** <h1>x</h1>'),
    );

    expect(html).toContain('<strong>bold</strong>');
    expect(html).not.toContain('<h1>');
  });

  it('renders only owned Glacier image references', () => {
    const id = '80e10c3b-aaba-4f1c-a427-7564b1d5eaaf';
    const html = sanitizer.sanitize(
      SecurityContext.HTML,
      service.render(`![attachment](glacier-img://${id}) ![external](https://example.com/x.png)`),
    );

    expect(html).toContain(`/api/v1/images/${id}`);
    expect(html).not.toContain('example.com/x.png');
  });
});
