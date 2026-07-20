import { TestBed } from '@angular/core/testing';

import { TemplateSyntaxFixture } from './template-syntax-fixture';

describe('TemplateSyntaxFixture', () => {
  it('compiles representative Angular template syntax', async () => {
    await TestBed.configureTestingModule({ imports: [TemplateSyntaxFixture] }).compileComponents();
    const fixture = TestBed.createComponent(TemplateSyntaxFixture);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('button')).toHaveLength(2);
  });
});
