import { createMarkdownRenderer } from 'vitepress'
import { fileURLToPath } from 'node:url'
import matter from 'gray-matter'

const root = fileURLToPath(new URL('..', import.meta.url))
// Use the site's parser for punctuation, duplicate headings and explicit IDs.
// Parsing needs no highlighter; executable snippets are checked separately.
const md = await createMarkdownRenderer(root, { highlight: text => text })
export function siteHeadings(text, file) {
  const tokens = md.parse(matter(text).content, { path: file, realPath: file })
  return tokens.filter(token => token.type === 'heading_open').map(token => token.attrGet('id'))
}
