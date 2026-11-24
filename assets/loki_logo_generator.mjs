// Draws the Loki logo and writes every file that uses it (SVG masters + Android icons),
// so all copies come from one set of shapes and can never drift apart.
//
// Run from the project root:   node assets/loki_logo_generator.mjs
//
// The mark is an "L" with a play triangle resting in its corner. The slanted top of the L
// lines up exactly with the top edge of the triangle, so both read as one shape.

import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname } from 'node:path'

// ---- palette (keep in sync with app/src/main/res/values/colors.xml) -------------------------
const CHARCOAL = '#2B2D30'
const CHARCOAL_DARK = '#1C1E20'
const NEON = '#39FF14'
const GREEN = '#1FB80E'
const DEEP = '#0C6B08'

// ---- shapes, on the 108x108 grid Android uses for launcher icons (safe zone = middle 66) ----
const TRIANGLE = [[51, 34.5], [77, 49.5], [51, 64.5]]
const slope = (TRIANGLE[1][1] - TRIANGLE[0][1]) / (TRIANGLE[1][0] - TRIANGLE[0][0])
const topEdgeAt = x => TRIANGLE[0][1] + slope * (x - TRIANGLE[0][0])
const LETTER = [[31, topEdgeAt(31)], [43, topEdgeAt(43)], [43, 72.5], [75, 72.5], [75, 84.5], [31, 84.5]]

const RIM = 2.2 // width of the lighter bevelled edge around each shape
const TIGHT = { x: 21, y: 20.75, size: 66 } // square crop around the mark, for in-app use
// Launchers may crop the icon to a circle. Only the middle circle of radius 33 is always visible,
// and the corners of the L reach 38.6 from the centre, so the launcher copy is drawn smaller.
const LAUNCHER_SCALE = 0.82

// ---- geometry helpers ------------------------------------------------------------------------
const num = n => String(+n.toFixed(2))
const unit = ([x, y]) => { const l = Math.hypot(x, y); return [x / l, y / l] }

/** Moves every edge of a (clockwise) polygon inwards by `d` and returns the smaller polygon. */
function inset(points, d) {
    const lines = points.map((p, i) => {
        const q = points[(i + 1) % points.length]
        const [dx, dy] = unit([q[0] - p[0], q[1] - p[1]])
        return { px: p[0] - dy * d, py: p[1] + dx * d, dx, dy }
    })
    return lines.map((b, i) => {
        const a = lines[(i + lines.length - 1) % lines.length]
        const t = ((b.px - a.px) * b.dy - (b.py - a.py) * b.dx) / (a.dx * b.dy - a.dy * b.dx)
        return [a.px + a.dx * t, a.py + a.dy * t]
    })
}

/** Path data for a polygon with softly rounded corners. Same syntax for SVG and Android. */
function roundedPath(points, radius) {
    const parts = points.map((p, i) => {
        const prev = points[(i + points.length - 1) % points.length]
        const next = points[(i + 1) % points.length]
        const toPrev = unit([prev[0] - p[0], prev[1] - p[1]])
        const toNext = unit([next[0] - p[0], next[1] - p[1]])
        const start = [p[0] + toPrev[0] * radius, p[1] + toPrev[1] * radius]
        const end = [p[0] + toNext[0] * radius, p[1] + toNext[1] * radius]
        return `${i === 0 ? 'M' : 'L'}${num(start[0])},${num(start[1])} Q${num(p[0])},${num(p[1])} ${num(end[0])},${num(end[1])}`
    })
    return parts.join(' ') + ' Z'
}

const outer = [roundedPath(LETTER, 2), roundedPath(TRIANGLE, 2)]
const face = [roundedPath(inset(LETTER, RIM), 1), roundedPath(inset(TRIANGLE, RIM), 1)]

// ---- gradients (shared by SVG and Android so both look the same) ----------------------------
// rim: light catches the top-left edge and falls off to shadow at the bottom-right
const RIM_STOPS = [[0, '#E4FFD6'], [0.22, '#7DFF5E'], [0.6, GREEN], [1, '#053D04']]
const RIM_LINE = { x1: 31, y1: 23, x2: 77, y2: 85 }
// face: the neon-to-deep green body
const FACE_STOPS = [[0, NEON], [0.55, GREEN], [1, DEEP]]
const FACE_LINE = { x1: 54, y1: 23, x2: 54, y2: 85 }
// glow: soft neon halo behind the mark
const GLOW = { cx: 54, cy: 54, r: 40 }
const GLOW_STOPS = [[0, NEON, 0.34], [0.55, NEON, 0.1], [1, NEON, 0]]

// ---- SVG -------------------------------------------------------------------------------------
const svgStops = stops => stops.map(([o, c, a = 1]) =>
    `<stop offset="${o}" stop-color="${c}"${a < 1 ? ` stop-opacity="${a}"` : ''}/>`).join('')
const svgDefs = `<defs>
    <radialGradient id="bg" gradientUnits="userSpaceOnUse" cx="54" cy="40" r="78">${svgStops([[0, '#3A3D42'], [1, CHARCOAL_DARK]])}</radialGradient>
    <radialGradient id="glow" gradientUnits="userSpaceOnUse" cx="${GLOW.cx}" cy="${GLOW.cy}" r="${GLOW.r}">${svgStops(GLOW_STOPS)}</radialGradient>
    <linearGradient id="rim" gradientUnits="userSpaceOnUse" x1="${RIM_LINE.x1}" y1="${RIM_LINE.y1}" x2="${RIM_LINE.x2}" y2="${RIM_LINE.y2}">${svgStops(RIM_STOPS)}</linearGradient>
    <linearGradient id="face" gradientUnits="userSpaceOnUse" x1="${FACE_LINE.x1}" y1="${FACE_LINE.y1}" x2="${FACE_LINE.x2}" y2="${FACE_LINE.y2}">${svgStops(FACE_STOPS)}</linearGradient>
  </defs>`
const svgMark = `<circle cx="${GLOW.cx}" cy="${GLOW.cy}" r="${GLOW.r}" fill="url(#glow)"/>
  ${outer.map(d => `<path d="${d}" fill="url(#rim)"/>`).join('\n  ')}
  ${face.map(d => `<path d="${d}" fill="url(#face)"/>`).join('\n  ')}`

const svgIcon = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="512" height="512">
  <title>Loki app icon</title>
  ${svgDefs}
  <rect width="108" height="108" rx="24" fill="url(#bg)"/>
  <g transform="translate(54 54) scale(${LAUNCHER_SCALE}) translate(-54 -54)">
  ${svgMark}
  </g>
</svg>
`
const svgLogo = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${TIGHT.x} ${TIGHT.y} ${TIGHT.size} ${TIGHT.size}" width="512" height="512">
  <title>Loki logo</title>
  ${svgDefs}
  ${svgMark}
</svg>
`

/**
 * A wide banner: the mark on the left and the name on the right, on charcoal. The name is plain
 * text, so the banner has to be turned into a PNG by a browser (see the end of this file).
 */
function svgBanner(width, height) {
    const markBox = height * 0.72 // drawn size of the square crop around the mark
    const scale = markBox / TIGHT.size
    const markX = width * 0.27 - markBox / 2
    const markY = (height - markBox) / 2
    const textSize = height * 0.4
    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${width} ${height}" width="${width}" height="${height}">
  <title>Loki banner</title>
  ${svgDefs}
  <defs>
    <radialGradient id="bannerBg" cx="0.27" cy="0.45" r="0.9">${svgStops([[0, '#3A3D42'], [1, CHARCOAL_DARK]])}</radialGradient>
    <linearGradient id="word" x1="0" y1="0" x2="0" y2="1">${svgStops([[0, '#7DFF5E'], [0.5, NEON], [1, GREEN]])}</linearGradient>
  </defs>
  <rect width="${width}" height="${height}" fill="url(#bannerBg)"/>
  <g transform="translate(${num(markX)} ${num(markY)}) scale(${num(scale)}) translate(${-TIGHT.x} ${-TIGHT.y})">
  ${svgMark}
  </g>
  <text x="${num(width * 0.47)}" y="${num(height / 2 + textSize * 0.36)}" font-family="Segoe UI, Roboto, Arial, sans-serif" font-size="${num(textSize)}" font-weight="700" fill="url(#word)">Loki</text>
</svg>
`
}

// ---- Android vector drawables ----------------------------------------------------------------
const argb = (hex, alpha = 1) => '#' + Math.round(alpha * 255).toString(16).padStart(2, '0').toUpperCase() + hex.slice(1)
const androidStops = stops => stops.map(([o, c, a = 1]) =>
    `                <item android:offset="${o}" android:color="${argb(c, a)}" />`).join('\n')
const linearFill = (line, stops) => `        <aapt:attr name="android:fillColor">
            <gradient android:type="linear" android:startX="${line.x1}" android:startY="${line.y1}" android:endX="${line.x2}" android:endY="${line.y2}">
${androidStops(stops)}
            </gradient>
        </aapt:attr>`
const gradientPath = (d, fill) => `    <path android:pathData="${d}">\n${fill}\n    </path>`
const flatPath = (d, color) => `    <path android:fillColor="${color}" android:pathData="${d}" />`

/**
 * `tight` crops to the mark for in-app use. Otherwise the full 108 launcher-icon grid is kept,
 * and `launcher` shrinks the mark so a circular crop cannot cut its corners.
 */
function androidVector(body, { tight, launcher, comment, gradients }) {
    const size = tight ? TIGHT.size : 108
    const open = tight
        ? `<group android:translateX="${-TIGHT.x}" android:translateY="${-TIGHT.y}">\n`
        : launcher
            ? `<group android:pivotX="54" android:pivotY="54" android:scaleX="${LAUNCHER_SCALE}" android:scaleY="${LAUNCHER_SCALE}">\n`
            : ''
    const close = tight || launcher ? '\n</group>' : ''
    return `<?xml version="1.0" encoding="utf-8"?>
<!-- ${comment} Generated by assets/loki_logo_generator.mjs, do not edit by hand. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"${gradients ? '\n    xmlns:aapt="http://schemas.android.com/aapt"' : ''}
    android:width="${size}dp"
    android:height="${size}dp"
    android:viewportWidth="${size}"
    android:viewportHeight="${size}">
${open}${body}${close}
</vector>
`
}

const glowBody = `    <path android:pathData="M${GLOW.cx - GLOW.r},${GLOW.cy} a${GLOW.r},${GLOW.r} 0 1,0 ${GLOW.r * 2},0 a${GLOW.r},${GLOW.r} 0 1,0 ${-GLOW.r * 2},0 Z">
        <aapt:attr name="android:fillColor">
            <gradient android:type="radial" android:centerX="${GLOW.cx}" android:centerY="${GLOW.cy}" android:gradientRadius="${GLOW.r}">
${androidStops(GLOW_STOPS)}
            </gradient>
        </aapt:attr>
    </path>`
const gradientBody = [
    ...outer.map(d => gradientPath(d, linearFill(RIM_LINE, RIM_STOPS))),
    ...face.map(d => gradientPath(d, linearFill(FACE_LINE, FACE_STOPS)))
].join('\n')
// Android 6 cannot draw gradients inside vectors, so it gets two flat greens instead
const flatBody = [...outer.map(d => flatPath(d, NEON)), ...face.map(d => flatPath(d, GREEN))].join('\n')
const whiteBody = outer.map(d => flatPath(d, '#FFFFFF')).join('\n')

// ---- write everything ------------------------------------------------------------------------
const RES = 'app/src/main/res'
const files = {
    'assets/loki_icon.svg': svgIcon,
    'assets/loki_logo.svg': svgLogo,
    'assets/loki_feature_graphic.svg': svgBanner(1024, 500), // store listing header
    'assets/loki_tv_banner.svg': svgBanner(640, 360), // Android TV home screen
    // in-app logo (splash screen, menu header)
    [`${RES}/drawable/ic_loki_logo.xml`]: androidVector(flatBody, { tight: true, comment: 'Loki logo, flat colours for Android 6.' }),
    [`${RES}/drawable-v24/ic_loki_logo.xml`]: androidVector(gradientBody, { tight: true, gradients: true, comment: 'Loki logo, full gradient.' }),
    // single-colour mark (notifications)
    [`${RES}/drawable/ic_loki_mark_white.xml`]: androidVector(whiteBody, { tight: true, comment: 'Loki mark in plain white, for notifications.' }),
    // launcher icon layers
    [`${RES}/drawable/ic_launcher_foreground.xml`]: androidVector(flatBody, { launcher: true, comment: 'Launcher icon foreground, flat colours for Android 6.' }),
    [`${RES}/drawable-v24/ic_launcher_foreground.xml`]: androidVector(glowBody + '\n' + gradientBody, { launcher: true, gradients: true, comment: 'Launcher icon foreground with glow.' }),
    [`${RES}/drawable/ic_launcher_monochrome.xml`]: androidVector(whiteBody, { launcher: true, comment: 'Launcher icon in one colour, for Android themed icons.' }),
    // About page (drawn inside a charcoal circle there)
    'shared/src/commonMain/composeResources/drawable/ic_foreground.xml': androidVector(flatBody, { comment: 'Loki logo for the About page.' })
}
for (const [path, content] of Object.entries(files)) {
    mkdirSync(dirname(path), { recursive: true })
    writeFileSync(path, content)
    console.log('wrote', path)
}
console.log('palette used:', { CHARCOAL, CHARCOAL_DARK, NEON, GREEN, DEEP })

// Three files have to be PNG pictures, which this script cannot make. After changing the logo,
// render them again with any Chromium browser (Edge shown here), from the project root:
//
//   EDGE="/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
//   shot() { "$EDGE" --headless --disable-gpu --hide-scrollbars --default-background-color=00000000 \
//            --window-size=$1 --screenshot="$(cygpath -w "$3")" "file:///$(cygpath -m "$PWD/$2")"; }
//   shot 512,512  assets/loki_icon.svg            fastlane/metadata/android/en-US/images/icon.png
//   shot 1024,500 assets/loki_feature_graphic.svg fastlane/metadata/android/en-US/images/featureGraphic.png
//   shot 640,360  assets/loki_tv_banner.svg       app/src/main/res/mipmap-xhdpi/tv_banner.png
