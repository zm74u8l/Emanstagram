import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { get } from '@/lib/api'
import { Avatar } from '@/components/ui/Avatar'
import { formatCount } from '@/lib/utils'
import type { User } from '@/lib/types'

const TABS = ['Posts', 'Reels', 'Saved', 'Tagged'] as const

export default function Profile() {
  const { username } = useParams<{ username: string }>()

  const { data: user, isLoading, isError } = useQuery<User>({
    queryKey: ['user', username],
    queryFn: () => get<User>(`/api/users/username/${username}`),
    enabled: Boolean(username),
    retry: false,
  })

  if (isLoading) {
    return (
      <div className="mx-auto w-full max-w-4xl px-4 py-6">
        <div className="flex gap-6">
          <div className="size-28 shrink-0 animate-shimmer rounded-full bg-surface-muted" />
          <div className="flex-1 space-y-3">
            <div className="h-6 w-40 animate-shimmer rounded bg-surface-muted" />
            <div className="h-4 w-64 animate-shimmer rounded bg-surface-muted" />
          </div>
        </div>
      </div>
    )
  }

  if (isError || !user) {
    return (
      <div className="grid min-h-[50dvh] place-items-center px-4 text-center">
        <div>
          <h1 className="text-lg font-semibold">Account not found</h1>
          <p className="mt-1 text-sm text-fg-muted">
            @{username} does not exist, or the account is private.
          </p>
        </div>
      </div>
    )
  }

  return (
    <div className="mx-auto w-full max-w-4xl px-4 py-6">
      <header className="flex flex-col gap-6 sm:flex-row sm:items-start">
        <Avatar user={user} size="xl" ring />

        <div className="min-w-0 flex-1 space-y-4">
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="text-xl font-semibold">{user.username}</h1>
            {user.verified && (
              <span className="rounded-full bg-accent/15 px-2 py-0.5 text-xs font-medium text-accent">
                Verified
              </span>
            )}
            <button
              className="rounded-xl border border-line px-4 py-1.5 text-sm font-medium hover:bg-surface-muted transition-colors"
              disabled
            >
              Follow
            </button>
          </div>

          <dl className="flex gap-6 text-sm">
            <div>
              <dt className="inline font-semibold">{formatCount(user.postCount)}</dt>{' '}
              <dd className="inline text-fg-muted">posts</dd>
            </div>
            <div>
              <dt className="inline font-semibold">
                {formatCount(user.followerCount)}
              </dt>{' '}
              <dd className="inline text-fg-muted">followers</dd>
            </div>
            <div>
              <dt className="inline font-semibold">
                {formatCount(user.followingCount)}
              </dt>{' '}
              <dd className="inline text-fg-muted">following</dd>
            </div>
          </dl>

          {(user.displayName || user.bio || user.website || user.location) && (
            <div className="space-y-1 text-sm">
              {user.displayName && <p className="font-semibold">{user.displayName}</p>}
              {user.pronouns && <p className="text-fg-subtle">{user.pronouns}</p>}
              {user.bio && <p className="whitespace-pre-wrap text-fg-muted">{user.bio}</p>}
              {user.website && (
                <a
                  href={user.website}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-block text-accent hover:underline"
                >
                  {user.website.replace(/^https?:\/\//, '')}
                </a>
              )}
              {user.location && <p className="text-fg-subtle">{user.location}</p>}
            </div>
          )}
        </div>
      </header>

      <nav className="mt-8 flex border-b border-line">
        {TABS.map((tab) => (
          <button
            key={tab}
            className="flex-1 border-b-2 border-transparent px-3 py-3 text-sm font-medium text-fg-muted transition-colors hover:text-fg"
          >
            {tab}
          </button>
        ))}
      </nav>

      <div className="mt-4 grid grid-cols-3 gap-1">
        {Array.from({ length: 9 }).map((_, i) => (
          <div
            key={i}
            className="aspect-square animate-shimmer rounded-lg bg-surface-muted"
          />
        ))}
      </div>
    </div>
  )
}
