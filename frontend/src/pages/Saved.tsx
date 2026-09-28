import { SavedPosts } from './Profile'

/** The Saved collection on its own page (reached from the "More" menu). */
export default function Saved() {
  return (
    <div className="mx-auto w-full max-w-[935px] md:px-6 md:py-8">
      <header className="px-4 pb-6 pt-5 md:px-0">
        <h1 className="font-display text-[40px] leading-none">Saved</h1>
        <p className="mt-2 text-[14px] text-fg-muted">Only you can see what you’ve saved.</p>
      </header>
      <SavedPosts />
    </div>
  )
}
