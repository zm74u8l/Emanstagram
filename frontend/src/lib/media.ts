import { encode } from 'blurhash'

/**
 * Browser-side media preparation.
 *
 * Images are downscaled to at most 2048px on the long edge and re-encoded
 * as WebP before upload, typically turning a 4 MB phone photo into ~250 KB.
 * That saves the user's upload time and the server's egress budget, which
 * is the real limit on the free tier. EXIF rotation is applied first, so
 * portrait phone photos stay upright.
 */

export interface PreparedMedia {
  file: File
  width: number
  height: number
  durationMs?: number
  blurhash?: string
  previewUrl: string
  kind: 'image' | 'video'
}

const MAX_EDGE = 2048
const QUALITY = 0.86

export async function prepareImage(file: File, maxEdge = MAX_EDGE): Promise<PreparedMedia> {
  const bitmap = await decode(file)
  try {
    const scale = Math.min(1, maxEdge / Math.max(bitmap.width, bitmap.height))
    const width = Math.round(bitmap.width * scale)
    const height = Math.round(bitmap.height * scale)
    const blurhash = hashOf(bitmap)

    // Animated GIFs would lose their animation on a canvas, so they are sent as-is.
    if (file.type === 'image/gif') {
      return { file, width: bitmap.width, height: bitmap.height, blurhash, previewUrl: URL.createObjectURL(file), kind: 'image' }
    }

    const canvas = document.createElement('canvas')
    canvas.width = width
    canvas.height = height
    const ctx = canvas.getContext('2d')
    if (!ctx) throw new Error('Your browser could not process this image.')
    ctx.imageSmoothingQuality = 'high'
    ctx.drawImage(bitmap, 0, 0, width, height)

    const blob = await encodeCanvas(canvas)
    // Never make a file bigger: a small, already-compressed image may shrink
    // less than the re-encode costs.
    const useOriginal = blob.size >= file.size && scale === 1 && file.type !== 'image/heic'
    const out = useOriginal ? file : new File([blob], renameTo(file.name, extensionFor(blob.type)), { type: blob.type })
    return { file: out, width, height, blurhash, previewUrl: URL.createObjectURL(out), kind: 'image' }
  } finally {
    bitmap.close()
  }
}

/** A centred square crop at 400x400, for profile pictures. */
export async function prepareAvatar(file: File, size = 400): Promise<PreparedMedia> {
  const bitmap = await decode(file)
  try {
    const side = Math.min(bitmap.width, bitmap.height)
    const sx = (bitmap.width - side) / 2
    const sy = (bitmap.height - side) / 2
    const canvas = document.createElement('canvas')
    canvas.width = size
    canvas.height = size
    const ctx = canvas.getContext('2d')
    if (!ctx) throw new Error('Your browser could not process this image.')
    ctx.imageSmoothingQuality = 'high'
    ctx.drawImage(bitmap, sx, sy, side, side, 0, 0, size, size)
    const blob = await encodeCanvas(canvas)
    const out = new File([blob], renameTo(file.name, extensionFor(blob.type)), { type: blob.type })
    return { file: out, width: size, height: size, previewUrl: URL.createObjectURL(out), kind: 'image' }
  } finally {
    bitmap.close()
  }
}

/** Reads a video's dimensions and duration, and a blurhash of its first frame. */
export function prepareVideo(file: File): Promise<PreparedMedia> {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const video = document.createElement('video')
    video.preload = 'metadata'
    video.muted = true
    video.playsInline = true
    video.src = url

    const fail = () => reject(new Error(`${file.name} couldn't be read. Try an MP4 or WebM file.`))
    video.onerror = fail
    video.onloadedmetadata = () => {
      // Seek slightly in: frame 0 is often black.
      video.currentTime = Math.min(0.1, (video.duration || 0) / 2)
    }
    video.onseeked = () => {
      let blurhash: string | undefined
      try {
        const canvas = document.createElement('canvas')
        canvas.width = 32
        canvas.height = 32
        const ctx = canvas.getContext('2d')
        if (ctx) {
          ctx.drawImage(video, 0, 0, 32, 32)
          blurhash = encode(ctx.getImageData(0, 0, 32, 32).data, 32, 32, 4, 3)
        }
      } catch {
        /* a blurhash is polish, not a requirement */
      }
      resolve({
        file,
        width: video.videoWidth,
        height: video.videoHeight,
        durationMs: Math.round((video.duration || 0) * 1000),
        blurhash,
        previewUrl: url,
        kind: 'video',
      })
    }
  })
}

// ---------------------------------------------------------------------

async function decode(file: File): Promise<ImageBitmap> {
  try {
    return await createImageBitmap(file, { imageOrientation: 'from-image' })
  } catch {
    throw new Error(
      file.type === 'image/heic' || /\.heic$/i.test(file.name)
        ? 'HEIC photos aren’t supported by this browser. Export as JPEG and try again.'
        : `${file.name} couldn't be read as an image.`,
    )
  }
}

function hashOf(bitmap: ImageBitmap): string | undefined {
  try {
    const canvas = document.createElement('canvas')
    canvas.width = 32
    canvas.height = 32
    const ctx = canvas.getContext('2d')
    if (!ctx) return undefined
    ctx.drawImage(bitmap, 0, 0, 32, 32)
    return encode(ctx.getImageData(0, 0, 32, 32).data, 32, 32, 4, 3)
  } catch {
    return undefined
  }
}

/** WebP where the browser can encode it (Safari < 17 can't), JPEG otherwise. */
function encodeCanvas(canvas: HTMLCanvasElement): Promise<Blob> {
  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (webp) => {
        if (webp && webp.type === 'image/webp') return resolve(webp)
        canvas.toBlob((jpeg) => (jpeg ? resolve(jpeg) : reject(new Error('Image encoding failed.'))), 'image/jpeg', QUALITY)
      },
      'image/webp',
      QUALITY,
    )
  })
}

function extensionFor(type: string): string {
  return type === 'image/webp' ? 'webp' : type === 'image/png' ? 'png' : 'jpg'
}

function renameTo(name: string, ext: string): string {
  const base = name.replace(/\.[^.]+$/, '') || 'image'
  return `${base}.${ext}`
}
