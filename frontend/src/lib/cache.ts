import type { InfiniteData, QueryClient } from '@tanstack/react-query'
import type { Page, Post } from './types'

/**
 * The same post can sit in the feed, a profile grid, the saved list and its
 * own detail query at once. Liking it in one place has to update all of
 * them, or the heart flips back when you navigate. This walks every cached
 * query and patches the post wherever it appears.
 */
export function patchPost(qc: QueryClient, postId: string, patch: Partial<Post> | ((p: Post) => Partial<Post>)) {
  const apply = (p: Post): Post => ({ ...p, ...(typeof patch === 'function' ? patch(p) : patch) })

  qc.getQueryCache()
    .getAll()
    .forEach((query) => {
      const data = query.state.data as unknown
      if (!data || typeof data !== 'object') return

      if (isPost(data) && data.id === postId) {
        qc.setQueryData(query.queryKey, apply(data))
        return
      }
      if (isInfinite(data)) {
        let touched = false
        const pages = data.pages.map((page) => {
          if (!page || !Array.isArray(page.items)) return page
          const items = page.items.map((item) => {
            if (isPost(item) && item.id === postId) {
              touched = true
              return apply(item)
            }
            return item
          })
          return { ...page, items }
        })
        if (touched) qc.setQueryData(query.queryKey, { ...data, pages })
      }
    })
}

/** Removes a deleted post from every cached list. */
export function removePost(qc: QueryClient, postId: string) {
  qc.getQueryCache()
    .getAll()
    .forEach((query) => {
      const data = query.state.data as unknown
      if (isInfinite(data)) {
        const pages = data.pages.map((page) =>
          page && Array.isArray(page.items)
            ? { ...page, items: page.items.filter((item) => !(isPost(item) && item.id === postId)) }
            : page,
        )
        qc.setQueryData(query.queryKey, { ...data, pages })
      }
    })
  qc.removeQueries({ queryKey: ['post', postId] })
}

/** All items across the loaded pages of an infinite query. */
export function flatten<T>(data: InfiniteData<Page<T>> | undefined): T[] {
  return data?.pages.flatMap((p) => p.items) ?? []
}

function isPost(value: unknown): value is Post {
  return !!value && typeof value === 'object' && 'likeCount' in value && 'media' in value && 'author' in value
}

function isInfinite(value: unknown): value is InfiniteData<Page<unknown>> {
  return !!value && typeof value === 'object' && Array.isArray((value as InfiniteData<unknown>).pages)
}

/** Standard options for a cursor-paginated list. */
export const cursorPaging = {
  initialPageParam: undefined as string | undefined,
  getNextPageParam: (last: Page<unknown>) => last.nextCursor ?? undefined,
}

export function withCursor(path: string, cursor: string | undefined, extra = ''): string {
  const sep = path.includes('?') ? '&' : '?'
  const params = [cursor ? `cursor=${encodeURIComponent(cursor)}` : '', extra].filter(Boolean).join('&')
  return params ? `${path}${sep}${params}` : path
}
