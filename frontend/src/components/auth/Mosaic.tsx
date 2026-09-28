import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { get } from '@/lib/api'
import { BlurImage } from '@/components/ui/bits'
import { cn } from '@/lib/utils'
import type { MosaicTile } from '@/lib/types'

/** Below this many real photos the mosaic looks repetitive, so a generated pattern is used instead. */
const MIN_REAL_TILES = 6

interface Tile {
  key: string
  ratio: number
  tile?: MosaicTile
  tone?: string
}

/**
 * Columns of recent public photos drifting slowly past each other.
 *
 * Each column's track holds its tiles twice, so animating it by -50% loops
 * without a seam. Adjacent columns drift in opposite directions at different
 * speeds, which reads as depth rather than a conveyor belt. Motion stops
 * entirely for people who prefer reduced motion.
 */
export function Mosaic({ columns = 3, className }: { columns?: number; className?: string }) {
  const { data } = useQuery({
    queryKey: ['mosaic'],
    queryFn: () => get<MosaicTile[]>('/api/public/mosaic'),
    staleTime: 5 * 60_000,
    retry: false,
  })

  const cols = useMemo(() => buildColumns(data ?? [], columns), [data, columns])
  const live = (data?.length ?? 0) >= MIN_REAL_TILES

  return (
    <div className={cn('relative h-full overflow-hidden bg-[#111]', className)} aria-hidden>
      <div className="absolute inset-0 flex gap-3 p-3 [mask-image:linear-gradient(to_bottom,transparent,black_12%,black_88%,transparent)]">
        {cols.map((col, i) => (
          <div key={i} className="min-w-0 flex-1">
            <div
              className={cn(
                'flex flex-col gap-3 will-change-transform',
                i % 2 === 0 ? 'animate-drift-up' : 'animate-drift-down',
              )}
              style={{ animationDuration: `${80 + i * 25}s` }}
            >
              {[...col, ...col].map((t, j) => (
                <div
                  key={`${t.key}-${j}`}
                  className="w-full overflow-hidden rounded-[10px]"
                  style={{ aspectRatio: String(t.ratio) }}
                >
                  {t.tile ? (
                    <BlurImage src={t.tile.url} blurhash={t.tile.blurhash} className="size-full" />
                  ) : (
                    <div className="size-full" style={{ background: t.tone }} />
                  )}
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>

      {live && (
        <p className="absolute bottom-5 left-6 flex items-center gap-2 text-[12px] font-medium tracking-wide text-white/70">
          <span className="size-1.5 rounded-full bg-emerald-400" />
          Recently shared on Emanstagram
        </p>
      )}
    </div>
  )
}

function buildColumns(tiles: MosaicTile[], count: number): Tile[][] {
  const source: Tile[] =
    tiles.length >= MIN_REAL_TILES
      ? tiles.map((t, i) => ({
          key: `${i}-${t.url}`,
          tile: t,
          ratio: clamp(t.width && t.height ? t.width / t.height : 1, 0.75, 1.25),
        }))
      : generated()

  // Deal tiles round-robin, then pad each column so its track is always
  // taller than the viewport, even with only a handful of photos.
  const cols: Tile[][] = Array.from({ length: count }, () => [])
  source.forEach((t, i) => cols[i % count].push(t))
  return cols.map((col) => {
    const out = [...col]
    let n = 0
    while (out.length < 8 && col.length > 0) {
      const t = col[n % col.length]
      out.push({ ...t, key: `${t.key}-pad${n}` })
      n++
    }
    return out
  })
}

/** Quiet greyscale blocks of varying height, for a brand-new install with no photos yet. */
function generated(): Tile[] {
  const tones = ['#1c1c1c', '#262626', '#303030', '#1f1f1f', '#2a2a2a', '#353535', '#222222', '#2d2d2d']
  const ratios = [0.8, 1, 1.25, 0.75, 1, 0.9, 1.2, 0.8, 1.1]
  return Array.from({ length: 18 }, (_, i) => ({
    key: `g${i}`,
    ratio: ratios[i % ratios.length],
    tone: `linear-gradient(160deg, ${tones[i % tones.length]}, ${tones[(i + 3) % tones.length]})`,
  }))
}

function clamp(n: number, min: number, max: number) {
  return Math.min(max, Math.max(min, n))
}
