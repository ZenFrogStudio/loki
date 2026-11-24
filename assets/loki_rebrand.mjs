// Renames NewPipe to Loki in everything a user reads, in every language, and removes
// NewPipe's store listing. Safe to run again at any time, for example after merging a new
// NewPipe release brings its texts back:
//
//     node assets/loki_rebrand.mjs        (from the project root)
//
// It does NOT touch code names (org.schabi.newpipe...), web addresses, or the few texts that
// are facts about NewPipe itself.

import { existsSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'

const NAME = 'Loki'

// Texts that must keep saying NewPipe because they describe NewPipe, not this app
const KEEP = new Set([
    'import_settings_vulnerable_format', // "deprecated since NewPipe 0.27.0"
    'loki_based_on_title', // the credit to NewPipe on the About page
    'loki_based_on_description'
])

// ---- 1. app text -----------------------------------------------------------------------------
const TEXT_FOLDERS = ['app/src/main/res', 'shared/src/commonMain/composeResources']

/** Renames outside web addresses, including the misspellings a few translations use. */
function renameInText(text) {
    return text
        .split(/(https?:\/\/\S+)/)
        .map((part, i) => (i % 2 === 1 ? part : part.replace(/new ?pipe/gi, NAME)))
        .join('')
}

/** Rewrites one strings.xml. Only the text between the tags changes, never comments or names. */
function rebrandStringsFile(path) {
    const before = readFileSync(path, 'utf8')
    const after = before.replace(
        /(<(string|plurals|string-array)\b[^>]*\bname="([^"]+)"[^>]*>)([\s\S]*?)(<\/\2>)/g,
        (all, open, tag, key, body, close) => (KEEP.has(key) ? all : open + renameInText(body) + close)
    )
    if (after !== before) writeFileSync(path, after)
    return after !== before
}

let changedFiles = 0
for (const folder of TEXT_FOLDERS) {
    for (const dir of readdirSync(folder)) {
        const file = join(folder, dir, 'strings.xml')
        if (dir.startsWith('values') && existsSync(file) && rebrandStringsFile(file)) changedFiles++
    }
}
console.log(`app text: renamed in ${changedFiles} file(s)`)

// ---- 2. store listing ------------------------------------------------------------------------
// NewPipe's listing (descriptions in 91 languages, a changelog per NewPipe release, screenshots
// of the red NewPipe app) does not describe Loki. Only the English listing is kept, and its
// text, icon.png and featureGraphic.png are Loki's own.
const LISTING = 'fastlane/metadata/android'
let removed = 0
const remove = path => {
    if (existsSync(path)) {
        rmSync(path, { recursive: true })
        removed++
    }
}
for (const locale of readdirSync(LISTING)) {
    if (locale !== 'en-US') remove(join(LISTING, locale))
}
remove(join(LISTING, 'en-US/changelogs'))
remove(join(LISTING, 'en-US/images/phoneScreenshots'))
remove(join(LISTING, 'en-US/images/tenInchScreenshots'))
console.log(`store listing: removed ${removed} NewPipe folder(s)`)

// ---- 3. NewPipe's project paperwork and artwork ----------------------------------------------
// Donation buttons, issue forms and contributor rules of the NewPipe project, its translated
// READMEs, and the source files of its logo. None of it is used to build the app.
removed = 0
for (const path of [
    '.github/FUNDING.yml',
    '.github/ISSUE_TEMPLATE',
    '.github/DISCUSSION_TEMPLATE',
    '.github/PULL_REQUEST_TEMPLATE.md',
    '.github/CONTRIBUTING.md'
]) remove(path)
for (const file of readdirSync('doc')) {
    if (/^README\..+\.md$/.test(file)) remove(join('doc', file)) // README.de.md and friends
}
for (const file of [
    'BETA_new_pipe_icon_5.svg', 'NP logo v2.svg', 'NewPipe_background_just1.svg',
    'android-tv-banner.svg', 'icon_5.png', 'new_pipe_icon_4.svg', 'new_pipe_icon_5.png',
    'new_pipe_icon_5.svg', 'new_pipe_icon_5_beta2.svg', 'pure_logo.svg',
    'channel_banner_desktop.svg', 'channel_banner_mobild.svg',
    'bitcoin_qr_code.png', 'bountysource_qr_code.png', 'liberapay_donate_button.svg',
    'liberapay_qr_code.png'
]) remove(join('assets', file))
console.log(`project files: removed ${removed} NewPipe item(s)`)
