import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService, errorMessage } from '../core/api.service';
import { currentMonth } from '../core/finance.utils';
import { AssistantQuestion, AssistantReply, Category } from '../core/models';
import { IconComponent } from '../shared/icon.component';

@Component({
  selector: 'ml-assistant',
  imports: [CurrencyPipe, DatePipe, ReactiveFormsModule, RouterLink, IconComponent],
  templateUrl: './assistant.component.html',
})
export class AssistantComponent {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  readonly categories = signal<Category[]>([]);
  readonly categoriesLoading = signal(true);
  readonly categoriesError = signal('');
  readonly loading = signal(false);
  readonly error = signal('');
  readonly reply = signal<AssistantReply | null>(null);
  readonly askedQuestion = signal('');
  readonly announcement = computed(() => {
    const answer = this.reply();
    return answer ? `Guidance ready. ${this.decisionLabel(answer)}. Review the factors below.` : '';
  });
  readonly examples = [
    {
      title: 'Before you buy',
      question: 'How would a new purchase fit my spending plan?',
      purchaseAmount: 200,
    },
    {
      title: 'A rainy-day fund',
      question: 'How can I start building an emergency fund?',
      purchaseAmount: null,
    },
    {
      title: 'Recurring spending',
      question: 'How should I review my bills and subscriptions?',
      purchaseAmount: null,
    },
  ];
  readonly form = this.fb.group({
    question: this.fb.nonNullable.control('', [
      Validators.required,
      Validators.pattern(/\S/),
      Validators.maxLength(1200),
    ]),
    purchaseAmount: this.fb.control<number | null>(null, [
      Validators.min(0.01),
      Validators.max(100000000),
      Validators.pattern(/^\d+(?:\.\d{1,2})?$/),
    ]),
    categoryId: this.fb.nonNullable.control(''),
    month: this.fb.nonNullable.control(currentMonth(), [
      Validators.required,
      Validators.pattern(/^\d{4}-(0[1-9]|1[0-2])$/),
    ]),
  });
  constructor() {
    void this.loadCategories();
  }
  async loadCategories(): Promise<void> {
    this.categoriesLoading.set(true);
    this.categoriesError.set('');
    try {
      this.categories.set((await this.api.categories()).filter((c) => c.type === 'EXPENSE'));
    } catch (error) {
      this.categoriesError.set(errorMessage(error));
    } finally {
      this.categoriesLoading.set(false);
    }
  }
  chooseExample(index: number): void {
    this.form.patchValue({
      question: this.examples[index].question,
      purchaseAmount: this.examples[index].purchaseAmount,
    });
    this.error.set('');
    document.getElementById('assistant-question')?.focus();
  }
  decisionLabel(reply: AssistantReply): string {
    return {
      NOT_REQUESTED: 'A little perspective',
      LIKELY_AFFORDABLE: 'Appears to fit your recorded plan',
      CAUTION: 'Leave a little more breathing room',
      NOT_AFFORDABLE: 'Beyond your recorded spending room',
      INSUFFICIENT_DATA: 'More information would help',
    }[reply.decision];
  }
  async ask(): Promise<void> {
    if (this.loading()) return;
    this.form.markAllAsTouched();
    this.error.set('');
    if (this.form.invalid) {
      this.error.set(
        'Enter a question, a valid month, and a positive purchase amount with up to two decimal places if you include one.',
      );
      return;
    }
    const value = this.form.getRawValue();
    if (
      value.categoryId &&
      !this.categories().some((category) => category.id === value.categoryId)
    ) {
      this.error.set('Choose an expense category from your own category list.');
      return;
    }
    const question: AssistantQuestion = { question: value.question.trim(), month: value.month };
    if (value.purchaseAmount !== null) question.purchaseAmount = value.purchaseAmount;
    if (value.categoryId) question.categoryId = value.categoryId;
    this.loading.set(true);
    this.reply.set(null);
    this.askedQuestion.set(question.question);
    try {
      this.reply.set(await this.api.askAssistant(question));
    } catch (error) {
      this.error.set(errorMessage(error));
    } finally {
      this.loading.set(false);
    }
  }
}
