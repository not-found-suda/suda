import fs from 'node:fs/promises'
import path from 'node:path'

const ROOT = process.cwd()
const SCAN_ROOTS = ['src', 'scripts']
const IGNORE_DIRS = new Set(['node_modules', 'dist', 'build', 'coverage', '.git', '.gradle'])
const ALLOWED_EXTENSIONS = new Set([
  '.ts',
  '.tsx',
  '.js',
  '.jsx',
  '.mjs',
  '.cjs',
  '.json',
  '.css',
  '.md',
  '.yml',
  '.yaml',
  '.html',
])

const EXTRA_FILES = new Set([
  'eslint.config.js',
  'vite.config.ts',
  'tsconfig.json',
  'tsconfig.app.json',
  'tsconfig.node.json',
  'index.html',
])

function isIgnoredPath(relativePath) {
  return relativePath.split(path.sep).some((segment) => IGNORE_DIRS.has(segment))
}

async function* walk(dir) {
  let entries = []
  try {
    entries = await fs.readdir(dir, { withFileTypes: true })
  } catch {
    return
  }

  for (const entry of entries) {
    const absolute = path.join(dir, entry.name)
    const relative = path.relative(ROOT, absolute)
    if (isIgnoredPath(relative)) {
      continue
    }

    if (entry.isDirectory()) {
      yield* walk(absolute)
      continue
    }

    if (!entry.isFile()) {
      continue
    }

    const extension = path.extname(entry.name)
    if (!ALLOWED_EXTENSIONS.has(extension) && !EXTRA_FILES.has(entry.name)) {
      continue
    }

    yield absolute
  }
}

function findIssues(content, relativePath) {
  const lines = content.split(/\r?\n/)
  const issues = []

  const patterns = [
    { name: 'replacement-char', regex: /\uFFFD/g },
    { name: 'question-mark-before-korean', regex: /\?[ㄱ-ㅎ가-힣ㅏ-ㅣ]/g },
  ]

  lines.forEach((line, index) => {
    for (const pattern of patterns) {
      if (!pattern.regex.test(line)) {
        continue
      }
      issues.push({
        file: relativePath,
        line: index + 1,
        type: pattern.name,
        text: line.trim(),
      })
    }
  })

  return issues
}

async function main() {
  const collected = []
  const seen = new Set()

  for (const scanRoot of SCAN_ROOTS) {
    const absoluteRoot = path.join(ROOT, scanRoot)
    for await (const file of walk(absoluteRoot)) {
      const relative = path.relative(ROOT, file)
      if (seen.has(relative)) {
        continue
      }
      seen.add(relative)

      const content = await fs.readFile(file, 'utf8')
      const issues = findIssues(content, relative)
      collected.push(...issues)
    }
  }

  for (const file of EXTRA_FILES) {
    const absolute = path.join(ROOT, file)
    const relative = path.relative(ROOT, absolute)
    if (seen.has(relative)) {
      continue
    }

    try {
      const content = await fs.readFile(absolute, 'utf8')
      const issues = findIssues(content, relative)
      collected.push(...issues)
      seen.add(relative)
    } catch {
      // 파일이 없는 경우는 무시
    }
  }

  if (collected.length === 0) {
    console.log('check:encoding passed (깨짐 문자열 없음)')
    return
  }

  console.error(`check:encoding failed (${collected.length}건)`)
  for (const issue of collected) {
    console.error(`${issue.file}:${issue.line} [${issue.type}] ${issue.text}`)
  }
  process.exitCode = 1
}

await main()
