export function canUseResearch(role?: string) {
  return role === 'admin' || role === 'researcher' || role === 'doctor';
}
export function canOpenSection(section: string, role?: string) {
  if (['admin-workspace', 'user-management'].includes(section)) return role === 'admin';
  if (['appointments', 'authorized-users', 'care-users', 'scale'].includes(section)) return role === 'doctor';
  if (section === 'verification-review') return role === 'admin';
  if (section === 'certification') return role === 'researcher' || role === 'doctor';
  if (['archive', 'risk', 'model', 'research-data', 'research-models', 'research-versions', 'research-experiments'].includes(section)) return canUseResearch(role);
  // Existing model configuration exposes credentials; research-safe model APIs follow in phase two.
  if (section === 'model-config') return role === 'admin';
  return true;
}


