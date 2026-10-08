export function validateFreePlanState(state, now = Date.now()) {
  if (!state || typeof state !== 'object' || typeof state.accountId !== 'string' || !/^\d{12}$/.test(state.accountId)) {
    throw new Error('AWS account-plan response is incomplete');
  }
  if (state.accountPlanType !== 'FREE' || state.accountPlanStatus !== 'ACTIVE') {
    throw new Error('An active AWS FREE account plan is required; paid, legacy and expired plans are rejected');
  }
  const credits = state.accountPlanRemainingCredits;
  if (credits?.unit !== 'USD' || typeof credits.amount !== 'number' || !Number.isFinite(credits.amount) || credits.amount <= 0) {
    throw new Error('A verified positive USD credit balance is required');
  }
  const expiration = state.accountPlanExpirationDate;
  if (typeof expiration !== 'string' || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})$/.test(expiration)) {
    throw new Error('A valid AWS free-plan expiration timestamp is required');
  }
  const expiry = Date.parse(expiration);
  const [year, month, day] = expiration.slice(0, 10).split('-').map(Number);
  const calendarDate = new Date(Date.UTC(year, month - 1, day));
  if (calendarDate.getUTCFullYear() !== year || calendarDate.getUTCMonth() !== month - 1 || calendarDate.getUTCDate() !== day) {
    throw new Error('AWS free-plan expiration contains an invalid calendar date');
  }
  if (!Number.isFinite(expiry) || !Number.isFinite(now) || expiry <= now) {
    throw new Error('AWS free-plan expiration must be in the future');
  }
  return { creditsUsd: credits.amount, expiresAt: new Date(expiry).toISOString() };
}
