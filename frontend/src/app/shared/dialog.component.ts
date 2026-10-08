import {
  AfterViewInit,
  Component,
  ElementRef,
  OnDestroy,
  ViewChild,
  input,
  output,
} from '@angular/core';
import { IconComponent } from './icon.component';
@Component({
  selector: 'wp-dialog',
  imports: [IconComponent],
  templateUrl: './dialog.component.html',
  styleUrl: './dialog.component.css',
})
export class DialogComponent implements AfterViewInit, OnDestroy {
  readonly title = input('');
  readonly closed = output<void>();
  private opener?: HTMLElement;
  @ViewChild('dialog') dialog!: ElementRef<HTMLDialogElement>;
  ngAfterViewInit(): void {
    const focused = document.activeElement;
    this.opener = focused instanceof HTMLElement ? focused : undefined;
    this.dialog.nativeElement.showModal();
  }
  ngOnDestroy(): void {
    // Angular may detach the dialog before destruction, so native close alone
    // cannot reliably return keyboard focus to the control that opened it.
    const element = this.dialog?.nativeElement;
    if (element?.open) element.close();
    if (this.opener?.isConnected) this.opener.focus({ preventScroll: true });
  }
  cancel(event: Event): void {
    event.preventDefault();
    this.closed.emit();
  }
}
