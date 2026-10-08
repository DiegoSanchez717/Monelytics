import { AbstractControl, ValidationErrors } from '@angular/forms';
// BCrypt has a 72-byte input boundary, including multi-byte Unicode characters.
export function passwordByteLimit(control: AbstractControl): ValidationErrors | null {
  return new TextEncoder().encode(String(control.value ?? '')).length > 72
    ? { passwordBytes: true }
    : null;
}
