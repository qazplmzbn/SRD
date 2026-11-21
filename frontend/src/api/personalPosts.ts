import { getAuthHeader } from './index';

export interface PostInput { content: string; source: string; publishedAt: string | null }
export interface PersonalPost extends PostInput { id: string; importedAt: string }
export interface PostPage { items: PersonalPost[]; total: number; page: number; pageSize: number }

const base = (import.meta.env.VITE_API_BASE || '').replace(/\/api\/?$/, '').replace(/\/$/, '');
async function call<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${base}/api/me/posts${path}`, {
    ...init, headers: { 'Content-Type': 'application/json', ...getAuthHeader(), ...init?.headers },
  });
  const body = await response.json();
  if (!response.ok || !body.success) throw new Error(body.message || '请求失败，请稍后重试');
  return body.data;
}
export const personalPostsApi = {
  list: (page: number) => call<PostPage>(`?page=${page}&pageSize=10`),
  save: (posts: PostInput[]) => call<{ imported: number; skipped: number }>('/import', {
    method: 'POST', body: JSON.stringify({ consent: true, posts }),
  }),
  remove: (id: string) => call<void>(`/${encodeURIComponent(id)}`, { method: 'DELETE' }),
};

/** Parse locally: no file or unselected record is uploaded during preview. */
export function parsePostFile(name: string, text: string): PostInput[] {
  let records: unknown;
  if (name.toLowerCase().endsWith('.txt')) records = [{ content: text, source: '手动导入', publishedAt: null }];
  else if (name.toLowerCase().endsWith('.json')) records = JSON.parse(text.replace(/^\uFEFF/, ''));
  else throw new Error('目前支持 UTF-8 编码的 TXT 或 JSON 文件');
  if (!Array.isArray(records) || records.length === 0 || records.length > 100) throw new Error('文件须包含1至100条贴文');
  return records.map((value: unknown, index: number) => {
    const row = value as Partial<PostInput> | null;
    if (!row || typeof row.content !== 'string' || !row.content.trim() || row.content.length > 10000)
      throw new Error(`第${index + 1}条内容为空或超过10000字`);
    const source = row.source ?? '手动导入';
    if (typeof source !== 'string' || !source.trim() || source.length > 40) throw new Error(`第${index + 1}条来源须为1至40字`);
    if (row.publishedAt != null && (typeof row.publishedAt !== 'string' || !/^\d{4}-\d{2}-\d{2}T.*(?:Z|[+-]\d{2}:\d{2})$/.test(row.publishedAt) || Number.isNaN(Date.parse(row.publishedAt))))
      throw new Error(`第${index + 1}条时间须为带时区的ISO时间，未知时请填null`);
    return { content: row.content.trim(), source: source.trim(), publishedAt: row.publishedAt ?? null };
  });
}
