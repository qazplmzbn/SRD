import { getAuthHeader } from './index';
export type VerificationStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'REVOKED';
export interface DoctorApplication {
  id: string; applicantId: number; realName: string; institution: string; qualification: string;
  credentialNumber: string; notes: string; status: VerificationStatus; submittedAt: string;
  reviewedAt: string | null; reviewerId: number | null; reviewReason: string | null;
}
export interface ApplicationInput { realName: string; institution: string; qualification: string; credentialNumber: string; notes: string; consent: boolean }
const rawBase = import.meta.env.VITE_API_BASE;
const base = !rawBase || rawBase === 'undefined' ? '' : rawBase.replace(/\/api\/?$/, '').replace(/\/$/, '');
async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${base}/api${path}`, { ...init, headers: { ...getAuthHeader(), ...init?.headers } });
  const body = await response.json();
  if (!response.ok || !body.success) throw new Error(body.message || body.detail || '请求失败，请稍后重试');
  return body.data;
}
export const doctorVerificationApi = {
  mine: () => request<DoctorApplication[]>('/me/doctor-applications'),
  submit: (input: ApplicationInput, file: File) => {
    const body = new FormData();
    body.append('application', new Blob([JSON.stringify(input)], { type: 'application/json' })); body.append('proof', file);
    return request<{ id: string }>('/me/doctor-applications', { method: 'POST', body });
  },
  list: (page: number, status: string) => request<{items: DoctorApplication[]; total: number}>(`/admin/doctor-applications?page=${page}&status=${encodeURIComponent(status)}`),
  review: (id: string, decision: string, reason: string) => request<void>(`/admin/doctor-applications/${encodeURIComponent(id)}/review`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ decision, reason }),
  }),
  download: async (id: string) => {
    const response = await fetch(`${base}/api/doctor-applications/${encodeURIComponent(id)}/proof`, { headers: getAuthHeader(), cache: 'no-store' });
    if (!response.ok) throw new Error('材料下载失败或无权访问');
    const blob = await response.blob(); const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url;
    a.download = `qualification.${blob.type.includes('pdf') ? 'pdf' : blob.type.includes('png') ? 'png' : 'jpg'}`;
    a.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
  },
};
