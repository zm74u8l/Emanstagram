import { useMutation, useQueryClient } from '@tanstack/react-query'
import { del, errorMessage, put } from '@/lib/api'
import { patchPost, removePost } from '@/lib/cache'
import { toast, toastError } from '@/stores/toast'
import type { Post } from '@/lib/types'

/**
 * Like, save and delete for a post, all optimistic: the UI changes on tap
 * and quietly reverts if the server disagrees.
 */
export function usePostActions(post: Post) {
  const qc = useQueryClient()

  const like = useMutation({
    mutationFn: (next: boolean) =>
      next
        ? put<{ liked: boolean; likeCount: number }>(`/api/posts/${post.id}/like`)
        : del<{ liked: boolean; likeCount: number }>(`/api/posts/${post.id}/like`),
    onMutate: (next) => {
      patchPost(qc, post.id, (p) => ({
        likedByMe: next,
        likeCount: Math.max(0, p.likeCount + (next === p.likedByMe ? 0 : next ? 1 : -1)),
      }))
    },
    onSuccess: (res) => patchPost(qc, post.id, { likedByMe: res.liked, likeCount: res.likeCount }),
    onError: (err, next) => {
      patchPost(qc, post.id, (p) => ({ likedByMe: !next, likeCount: Math.max(0, p.likeCount + (next ? -1 : 1)) }))
      toastError(errorMessage(err))
    },
  })

  const save = useMutation({
    mutationFn: (next: boolean) => (next ? put(`/api/posts/${post.id}/save`) : del(`/api/posts/${post.id}/save`)),
    onMutate: (next) => patchPost(qc, post.id, { savedByMe: next }),
    onSuccess: (_, next) => {
      if (next) toast('Saved to your collection')
      qc.invalidateQueries({ queryKey: ['saved'] })
    },
    onError: (err, next) => {
      patchPost(qc, post.id, { savedByMe: !next })
      toastError(errorMessage(err))
    },
  })

  const remove = useMutation({
    mutationFn: () => del(`/api/posts/${post.id}`),
    onSuccess: () => {
      removePost(qc, post.id)
      qc.invalidateQueries({ queryKey: ['profile'] })
      toast('Post deleted')
    },
    onError: (err) => toastError(errorMessage(err)),
  })

  return {
    toggleLike: () => like.mutate(!post.likedByMe),
    /** Double-tap only ever likes; it never un-likes. */
    likeOnce: () => {
      if (!post.likedByMe) like.mutate(true)
    },
    toggleSave: () => save.mutate(!post.savedByMe),
    remove,
  }
}
