/**
 * CHANGELOG 解析（NAV-03 / FR-v2.0-12）。
 * 放在纯 TS 模块而不是 `<script setup>`：Vue SFC 的 setup 块不允许 export，
 * 而且解析规则单独可测（版本倒序、四类齐全、当前版本标记）。
 */

export interface ChangelogSection {
  key: string
  items: string[]
}

export interface ChangelogRelease {
  title: string
  current: boolean
  sections: ChangelogSection[]
}

export function parseChangelog(markdown: string): ChangelogRelease[] {
  const result: ChangelogRelease[] = []
  let release: ChangelogRelease | null = null
  let section: ChangelogSection | null = null
  for (const line of markdown.split('\n')) {
    const text = line.trim()
    if (text.startsWith('## ')) {
      release = {
        title: text.slice(3).replace(/<!--.*?-->/g, '').trim(),
        current: /<!--\s*current\s*-->/.test(text) || text.includes('(current)'),
        sections: [],
      }
      result.push(release)
      section = null
    } else if (text.startsWith('### ') && release) {
      const anchor = /<!--\s*([a-z]+)\s*-->/.exec(text)
      section = {
        key: anchor ? anchor[1] : text.slice(4).replace(/<!--.*?-->/g, '').trim(),
        items: [],
      }
      release.sections.push(section)
    } else if (text.startsWith('- ') && section) {
      section.items.push(text.slice(2).trim())
    }
  }
  return result
}
