import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Bookmark, Camera, Grid3x3, Link2, MapPin, MoreHorizontal, Settings } from 'lucide-react'
import { ApiError, del, errorMessage, get, post, put } from '@/lib/api'
import { cursorPaging, flatten, withCursor } from '@/lib/cache'
import { copyToClipboard, formatCount } from '@/lib/utils'
import { toast, toastError } from '@/stores/toast'
import { Avatar } from '@/components/ui/Avatar'
import { Button } from '@/components/ui/Button'
import { ActionSheet } from '@/components/ui/Dialog'
import { EmptyState, Skeleton, Tabs, VerifiedBadge } from '@/components/ui/bits'
import { PostGrid, PostGridSkeleton } from '@/components/post/PostGrid'
import { InfiniteSentinel } from '@/components/InfiniteSentinel'
import { FollowButton, ReportDialog, UserListDialog } from '@/components/social'
import type { Conversation, Page, Post, Profile as ProfileType } from '@/lib/types'

type Tab = 'posts' | 'saved'

export default function Profile() {
  const { username = '' } = useParams()
  const { data: profile, error, isLoading } = useQuery({
    queryKey: ['profile', username],
    queryFn: () => get<ProfileType>(`/api/users/username/${encodeURIComponent(username)}`),
  })

  if (isLoading) return <ProfileSkeleton />
  if (error || !profile) {
    return (
      <EmptyState
        title="Sorry, this page isn’t available"
        body={
          error instanceof ApiError && error.status === 404
            ? 'The link may be broken, or the account may have been removed.'
            : errorMessage(error)
        }
        action={<Link to="/" className="text-[14px] font-semibold text-accent">Back to Emanstagram</Link>}
        className="min-h-[60dvh] justify-center"
      />
    )
  }
  return <ProfileView key={profile.id} profile={profile} />
}

function ProfileView({ profile }: { profile: ProfileType }) {
  const [tab, setTab] = useState<Tab>('posts')
  const [list, setList] = useState<'followers' | 'following' | null>(null)

  return (
    <div className="mx-auto w-full max-w-[935px] md:px-6 md:pt-8">
      {profile.bannerUrl && (
        <div className="aspect-[3/1] max-h-56 w-full overflow-hidden bg-surface-muted md:rounded-[var(--radius-card)]">
          <img src={profile.bannerUrl} alt="" className="size-full object-cover" />
        </div>
      )}

      <ProfileHeader profile={profile} onOpenList={setList} />

      {profile.blockedByMe ? (
        <EmptyState title={`You blocked @${profile.username}`} body="Unblock them to see their posts." className="border-t border-line" />
      ) : (
        <>
          <Tabs<Tab>
            tabs={[
              { value: 'posts', label: 'Posts', icon: <Grid3x3 size={13} /> },
              ...(profile.me ? [{ value: 'saved' as const, label: 'Saved', icon: <Bookmark size={13} /> }] : []),
            ]}
            value={tab}
            onChange={setTab}
          />
          {tab === 'posts' ? <UserPosts profile={profile} /> : <SavedPosts />}
        </>
      )}

      <UserListDialog
        open={list === 'followers'}
        onClose={() => setList(null)}
        title="Followers"
        path={`/api/users/${profile.username}/followers`}
        queryKey={['followers', profile.username]}
      />
      <UserListDialog
        open={list === 'following'}
        onClose={() => setList(null)}
        title="Following"
        path={`/api/users/${profile.username}/following`}
        queryKey={['following', profile.username]}
      />
    </div>
  )
}

function ProfileHeader({ profile, onOpenList }: { profile: ProfileType; onOpenList: (l: 'followers' | 'following') => void }) {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [sheet, setSheet] = useState(false)
  const [report, setReport] = useState(false)

  const message = useMutation({
    mutationFn: () => post<Conversation>('/api/conversations/direct', { userId: profile.id }),
    onSuccess: (c) => navigate(`/messages/${c.id}`),
    onError: (err) => toastError(errorMessage(err)),
  })

  const block = useMutation({
    mutationFn: (next: boolean) => (next ? put(`/api/users/${profile.id}/block`) : del(`/api/users/${profile.id}/block`)),
    onSuccess: (_, next) => {
      toast(next ? `Blocked @${profile.username}` : `Unblocked @${profile.username}`)
      qc.invalidateQueries({ queryKey: ['profile', profile.username] })
      qc.invalidateQueries({ queryKey: ['feed'] })
    },
    onError: (err) => toastError(errorMessage(err)),
  })

  const stats = (
    <>
      <Stat value={profile.postCount} label={profile.postCount === 1 ? 'post' : 'posts'} />
      <Stat value={profile.followerCount} label={profile.followerCount === 1 ? 'follower' : 'followers'} onClick={() => onOpenList('followers')} />
      <Stat value={profile.followingCount} label="following" onClick={() => onOpenList('following')} />
    </>
  )

  const avatar = (
    <Avatar user={profile} size="2xl" ring={profile.hasActiveStory ? 'unseen' : undefined} />
  )

  return (
    <header className="px-4 pb-6 pt-5 md:px-0 md:pb-11 md:pt-2">
      <div className="flex items-center gap-7 md:items-start md:gap-24 md:px-12">
        {profile.hasActiveStory ? (
          <Link to={`/stories/${profile.username}`} aria-label="View story">
            {avatar}
          </Link>
        ) : (
          avatar
        )}

        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-x-4 gap-y-3">
            <h1 className="flex items-center gap-1.5 text-[20px] leading-tight">
              <span className="truncate">{profile.username}</span>
              {profile.verified && <VerifiedBadge className="size-[18px]" />}
            </h1>
            <div className="flex w-full items-center gap-2 max-md:order-last md:w-auto">
              {profile.me ? (
                <>
                  <Link to="/settings" className="flex-1 md:flex-none">
                    <Button variant="secondary" size="sm" className="w-full">Edit profile</Button>
                  </Link>
                  <Link to="/create?mode=story" aria-label="Add story" className="shrink-0 md:flex-none">
                    <Button variant="secondary" size="sm" className="w-8 whitespace-nowrap px-0 md:w-auto md:px-3">
                      <Camera size={15} /> <span className="hidden md:inline">Add story</span>
                    </Button>
                  </Link>
                  <Link to="/settings" aria-label="Settings" className="grid size-8 place-items-center md:hidden">
                    <Settings size={22} />
                  </Link>
                </>
              ) : profile.blockedByMe ? (
                <Button variant="secondary" size="sm" loading={block.isPending} onClick={() => block.mutate(false)}>
                  Unblock
                </Button>
              ) : (
                <>
                  <FollowButton
                    user={profile}
                    following={profile.following}
                    className="flex-1 md:flex-none"
                    onChange={(following, followerCount) =>
                      qc.setQueryData<ProfileType>(['profile', profile.username], (p) =>
                        p ? { ...p, following, followerCount: followerCount ?? p.followerCount } : p,
                      )
                    }
                  />
                  <Button variant="secondary" size="sm" className="flex-1 md:flex-none" loading={message.isPending} onClick={() => message.mutate()}>
                    Message
                  </Button>
                </>
              )}
              {!profile.me && (
                <button onClick={() => setSheet(true)} aria-label="More options" className="grid size-8 place-items-center rounded-full hover:bg-surface-muted">
                  <MoreHorizontal size={20} />
                </button>
              )}
            </div>
          </div>

          <div className="mt-5 hidden gap-10 text-[16px] md:flex">{stats}</div>

          <div className="mt-4 hidden md:block">
            <Bio profile={profile} />
          </div>
        </div>
      </div>

      <div className="mt-4 md:hidden">
        <Bio profile={profile} />
      </div>
      <div className="-mx-4 mt-5 flex justify-around border-t border-line pt-3 text-center text-[14px] md:hidden">{stats}</div>

      <ActionSheet
        open={sheet}
        onClose={() => setSheet(false)}
        actions={[
          profile.blockedByMe
            ? { label: 'Unblock', tone: 'danger', onClick: () => block.mutate(false) }
            : { label: 'Block', tone: 'danger', onClick: () => block.mutate(true) },
          { label: 'Report', tone: 'danger', onClick: () => setReport(true) },
          {
            label: 'Copy profile URL',
            onClick: () =>
              copyToClipboard(`${window.location.origin}/u/${profile.username}`).then((ok) =>
                toast(ok ? 'Link copied' : 'Couldn’t copy the link'),
              ),
          },
        ]}
      />
      <ReportDialog open={report} onClose={() => setReport(false)} target={{ userId: profile.id }} />
    </header>
  )
}

function Stat({ value, label, onClick }: { value: number; label: string; onClick?: () => void }) {
  const content = (
    <>
      <span className="font-semibold">{formatCount(value)}</span> <span className="text-fg-muted md:text-fg">{label}</span>
    </>
  )
  const cls = 'flex flex-col md:block md:leading-none'
  return onClick ? (
    <button onClick={onClick} className={`${cls} hover:opacity-70`}>
      {content}
    </button>
  ) : (
    <span className={cls}>{content}</span>
  )
}

function Bio({ profile }: { profile: ProfileType }) {
  return (
    <div className="space-y-0.5 text-[14px] leading-snug">
      <p className="font-semibold">
        {profile.effectiveName}
        {profile.pronouns && <span className="ml-2 font-normal text-fg-muted">{profile.pronouns}</span>}
      </p>
      {profile.bio && <p className="whitespace-pre-wrap">{profile.bio}</p>}
      {profile.location && (
        <p className="flex items-center gap-1 text-fg-muted">
          <MapPin size={13} /> {profile.location}
        </p>
      )}
      {profile.website && (
        <a
          href={profile.website}
          target="_blank"
          rel="noopener noreferrer nofollow"
          className="flex items-center gap-1 font-semibold text-accent hover:underline"
        >
          <Link2 size={14} />
          {profile.website.replace(/^https?:\/\//, '').replace(/\/$/, '')}
        </a>
      )}
      {profile.followsYou && !profile.me && (
        <p className="pt-1">
          <span className="rounded-[6px] bg-surface-muted px-2 py-0.5 text-[12px] font-medium text-fg-muted">Follows you</span>
        </p>
      )}
    </div>
  )
}

function UserPosts({ profile }: { profile: ProfileType }) {
  const query = useInfiniteQuery({
    queryKey: ['user-posts', profile.username],
    queryFn: ({ pageParam }) => get<Page<Post>>(withCursor(`/api/users/${profile.username}/posts`, pageParam, 'limit=24')),
    ...cursorPaging,
  })
  const posts = flatten(query.data)

  if (query.isLoading) return <PostGridSkeleton />
  if (posts.length === 0) {
    return profile.me ? (
      <EmptyState
        icon={<Camera size={30} strokeWidth={1.5} />}
        title="Share photos"
        body="When you share photos, they’ll appear on your profile."
        action={<Link to="/create" className="text-[14px] font-semibold text-accent">Share your first photo</Link>}
      />
    ) : (
      <EmptyState icon={<Camera size={30} strokeWidth={1.5} />} title="No posts yet" />
    )
  }
  return (
    <>
      <PostGrid posts={posts} />
      <InfiniteSentinel hasMore={!!query.hasNextPage} loading={query.isFetchingNextPage} onLoadMore={() => query.fetchNextPage()} />
    </>
  )
}

export function SavedPosts() {
  const query = useInfiniteQuery({
    queryKey: ['saved'],
    queryFn: ({ pageParam }) => get<Page<Post>>(withCursor('/api/me/saved', pageParam, 'limit=24')),
    ...cursorPaging,
  })
  const posts = flatten(query.data)

  if (query.isLoading) return <PostGridSkeleton />
  if (posts.length === 0) {
    return (
      <EmptyState
        icon={<Bookmark size={28} strokeWidth={1.5} />}
        title="Save"
        body="Save photos and videos you want to see again. Only you can see what you’ve saved."
      />
    )
  }
  return (
    <>
      <PostGrid posts={posts} />
      <InfiniteSentinel hasMore={!!query.hasNextPage} loading={query.isFetchingNextPage} onLoadMore={() => query.fetchNextPage()} />
    </>
  )
}

function ProfileSkeleton() {
  return (
    <div className="mx-auto w-full max-w-[935px] px-4 pt-6 md:px-6 md:pt-10">
      <div className="flex items-center gap-8 md:gap-24 md:px-12">
        <Skeleton className="size-[88px] rounded-full md:size-[150px]" />
        <div className="flex-1 space-y-4">
          <Skeleton className="h-6 w-40" />
          <Skeleton className="h-4 w-64" />
          <Skeleton className="h-4 w-48" />
        </div>
      </div>
      <div className="mt-12">
        <PostGridSkeleton />
      </div>
    </div>
  )
}
