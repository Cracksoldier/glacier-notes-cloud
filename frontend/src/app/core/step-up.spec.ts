import { HttpErrorResponse } from '@angular/common/http';

import { StepUpPrompt } from './step-up';

describe('StepUpPrompt', () => {
  function refusal(errorCode: string) {
    return new HttpErrorResponse({ error: { errorCode }, status: 401, statusText: 'Unauthorized' });
  }

  it('opens the code field on the first refusal and swallows the error', () => {
    const prompt = new StepUpPrompt();

    expect(prompt.handle(refusal('AUTH_MFA_STEP_UP_REQUIRED'))).toBe(true);
    expect(prompt.open()).toBe(true);
  });

  it('surfaces a repeated refusal so the field does not clear without explanation', () => {
    const prompt = new StepUpPrompt();
    prompt.handle(refusal('AUTH_MFA_STEP_UP_REQUIRED'));

    expect(prompt.handle(refusal('AUTH_MFA_STEP_UP_REQUIRED'))).toBe(false);
    expect(prompt.open()).toBe(true);
  });

  it('clears a rejected code but leaves the caller to report it', () => {
    const prompt = new StepUpPrompt();
    prompt.handle(refusal('AUTH_MFA_STEP_UP_REQUIRED'));
    prompt.code = '123456';

    expect(prompt.handle(refusal('AUTH_MFA_INVALID_CODE'))).toBe(false);
    expect(prompt.code).toBe('');
  });

  it('ignores failures that are not step-up refusals', () => {
    const prompt = new StepUpPrompt();

    expect(prompt.handle(new Error('offline'))).toBe(false);
    expect(prompt.open()).toBe(false);
  });
});
