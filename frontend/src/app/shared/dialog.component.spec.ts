import { TestBed } from '@angular/core/testing';
import { DialogComponent } from './dialog.component';

describe('native modal lifecycle', () => {
  const showModal = vi.fn(function (this: HTMLDialogElement) {
    this.open = true;
  });
  const close = vi.fn(function (this: HTMLDialogElement) {
    this.open = false;
  });

  beforeEach(() => {
    showModal.mockClear();
    close.mockClear();
    Object.defineProperty(HTMLDialogElement.prototype, 'showModal', {
      configurable: true,
      value: showModal,
    });
    Object.defineProperty(HTMLDialogElement.prototype, 'close', {
      configurable: true,
      value: close,
    });
    TestBed.configureTestingModule({ imports: [DialogComponent] });
  });

  afterEach(() => {
    document.querySelectorAll('[data-dialog-test-opener]').forEach((element) => element.remove());
  });

  it('opens with the native modal API and closes before component removal', () => {
    const fixture = TestBed.createComponent(DialogComponent);
    fixture.componentRef.setInput('title', 'Add an IRA account');
    fixture.detectChanges();
    const element: HTMLDialogElement = fixture.nativeElement.querySelector('dialog');
    expect(showModal).toHaveBeenCalledOnce();
    expect(element.open).toBe(true);
    expect(element.getAttribute('aria-label')).toBe('Add an IRA account');
    fixture.destroy();
    expect(close).toHaveBeenCalledOnce();
    expect(element.open).toBe(false);
  });

  it('does not close an already closed dialog during cleanup', () => {
    const fixture = TestBed.createComponent(DialogComponent);
    fixture.detectChanges();
    const element: HTMLDialogElement = fixture.nativeElement.querySelector('dialog');
    element.open = false;
    fixture.destroy();
    expect(close).not.toHaveBeenCalled();
  });

  it('routes Escape cancellation through the same parent close action', () => {
    const fixture = TestBed.createComponent(DialogComponent);
    fixture.detectChanges();
    const closed = vi.fn();
    fixture.componentInstance.closed.subscribe(closed);
    const cancel = new Event('cancel', { cancelable: true });
    fixture.nativeElement.querySelector('dialog').dispatchEvent(cancel);
    expect(cancel.defaultPrevented).toBe(true);
    expect(closed).toHaveBeenCalledOnce();
  });

  it('returns focus to the connected opener when Angular destroys the dialog', () => {
    const opener = document.createElement('button');
    opener.setAttribute('data-dialog-test-opener', '');
    document.body.appendChild(opener);
    opener.focus();
    const fixture = TestBed.createComponent(DialogComponent);
    fixture.detectChanges();
    const dialog: HTMLDialogElement = fixture.nativeElement.querySelector('dialog');
    dialog.querySelector<HTMLButtonElement>('button')!.focus();
    expect(document.activeElement).not.toBe(opener);
    fixture.destroy();
    expect(document.activeElement).toBe(opener);
  });

  it('does not attempt to focus an opener that has been removed', () => {
    const opener = document.createElement('button');
    opener.setAttribute('data-dialog-test-opener', '');
    document.body.appendChild(opener);
    opener.focus();
    const fixture = TestBed.createComponent(DialogComponent);
    fixture.detectChanges();
    const focus = vi.spyOn(opener, 'focus');
    opener.remove();
    fixture.destroy();
    expect(close).toHaveBeenCalledOnce();
    expect(focus).not.toHaveBeenCalled();
  });
});
