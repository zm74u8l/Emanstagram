import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { MoreHorizontal } from 'lucide-react'
import { useMutation } from '@tanstack/react-query'
import { errorMessage, patch } from '@/lib/api'
import { patchPost } from '@/lib/cache'
import { useQueryClient } from '@tanstack/react-query'
import { copyToClipboard, postUrl } from '@/lib/utils'
import { toast, toastError } from '@/stores/toast'
import { useAuth } from '@/stores/auth'
import { ActionSheet, ConfirmDialog, Dialog, type SheetAction } from '@/components/ui/Dialog'
import { Button } from '@/components/ui/Button'
import { Textarea } from '@/components/ui/Input'
import { ReportDialog } from '@/components/social'
import { usePostActions } from './usePostActions'
import type { Post } from '@/lib/types'

/** The "•••" on a post: copy link, go to post, edit, delete, report. */
export function PostMenu({ post, onDeleted }: { post: Post; onDeleted?: () => void }) {
  const me = useAuth((s) => s.user)
  const navigate = useNavigate()
  const { remove } = usePostActions(post)
  const [sheet, setSheet] = useState(false)
  const [confirm, setConfirm] = useState(false)
  const [report, setReport] = useState(false)
  const [edit, setEdit] = useState(false)

  const mine = me?.id === post.author.id
  const moderator = me?.role === 'MODERATOR' || me?.role === 'ADMIN'

  const actions: SheetAction[] = [
    ...(mine || moderator ? [{ label: 'Delete', tone: 'danger' as const, onClick: () => setConfirm(true) }] : []),
    ...(!mine ? [{ label: 'Report', tone: 'danger' as const, onClick: () => setReport(true) }] : []),
    ...(mine ? [{ label: 'Edit caption', onClick: () => setEdit(true) }] : []),
    { label: 'Go to post', onClick: () => navigate(`/p/${post.id}`) },
    {
      label: 'Copy link',
      onClick: () => copyToClipboard(postUrl(post.id)).then((ok) => toast(ok ? 'Link copied' : 'Couldn’t copy the link')),
    },
    { label: 'About this account', onClick: () => navigate(`/u/${post.author.username}`) },
  ]

  return (
    <>
      <button
        onClick={() => setSheet(true)}
        aria-label="More options"
        className="grid size-8 place-items-center rounded-full text-fg hover:bg-surface-muted"
      >
        <MoreHorizontal size={20} />
      </button>
      <ActionSheet open={sheet} onClose={() => setSheet(false)} actions={actions} />
      <ConfirmDialog
        open={confirm}
        onClose={() => setConfirm(false)}
        title="Delete post?"
        body="This can’t be undone. Likes and comments go with it."
        confirmLabel="Delete"
        busy={remove.isPending}
        onConfirm={() =>
          remove.mutate(undefined, {
            onSuccess: () => {
              setConfirm(false)
              onDeleted?.()
            },
          })
        }
      />
      <ReportDialog open={report} onClose={() => setReport(false)} target={{ postId: post.id }} />
      {edit && <EditCaptionDialog post={post} onClose={() => setEdit(false)} />}
    </>
  )
}

function EditCaptionDialog({ post, onClose }: { post: Post; onClose: () => void }) {
  const qc = useQueryClient()
  const [caption, setCaption] = useState(post.caption ?? '')
  const mutation = useMutation({
    mutationFn: () => patch<Post>(`/api/posts/${post.id}`, { caption }),
    onSuccess: (updated) => {
      patchPost(qc, post.id, { caption: updated.caption, editedAt: updated.editedAt })
      onClose()
    },
    onError: (err) => toastError(errorMessage(err)),
  })
  return (
    <Dialog open onClose={onClose} title="Edit caption">
      <div className="space-y-3 p-4">
        <Textarea
          value={caption}
          onChange={(e) => setCaption(e.target.value)}
          maxLength={2200}
          showCount
          autoFocus
          aria-label="Caption"
          className="min-h-32"
        />
        <Button className="w-full" loading={mutation.isPending} onClick={() => mutation.mutate()}>
          Save
        </Button>
      </div>
    </Dialog>
  )
}
