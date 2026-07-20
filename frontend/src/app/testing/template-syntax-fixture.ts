import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-template-syntax-fixture',
  imports: [CommonModule, FormsModule],
  templateUrl: './template-syntax-fixture.html',
})
export class TemplateSyntaxFixture {
  protected label = 'template fixture';
  protected enabled = true;
  protected selected = 'one';
  protected readonly items = [
    { id: 1, label: 'One' },
    { id: 2, label: 'Two' },
  ];

  protected select(value: string): void {
    this.selected = value;
  }
}
