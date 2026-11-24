export function canUseResearch(role?: string) {
  return role === 'researcher' || role === 'doctor' || role === 'admin';
}
export function canOpenSection(section: string, role?: string) {
  if (['admin', 'user-management', 'model-config'].includes(section)) return role === 'admin';
  if (['appointments', 'authorized-users', 'care-users', 'scale'].includes(section)) return role === 'doctor';
  if (section === 'verification-review') return role === 'admin';
  if (section === 'certification') return role === 'researcher' || role === 'doctor';
  if (['archive', 'risk', 'model', 'research-data', 'research-models', 'research-versions', 'research-experiments'].includes(section)) return canUseResearch(role);
  return true;
}


