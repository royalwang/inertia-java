import test from 'node:test'
import assert from 'node:assert/strict'
import { siteHeadings } from './markdown-headings.mjs'

test('site anchors retain Chinese punctuation and normalize API headings', () => {
  assert.deepEqual(siteHeadings('# API\n\n## 会话、校验与 MVC 异常\n\n## `Prop.value` / merge\n\n## 2. 升级\n'), ['api', '会话、校验与-mvc-异常', 'prop-value-merge', '_2-升级'])
})
test('site anchors respect explicit IDs, duplicate headings and fenced examples', () => {
  assert.deepEqual(siteHeadings('# 标题\n\n## 指南 {#guide}\n\n## 重复\n\n## 重复\n\n```md\n## 示例\n```\n'), ['标题', 'guide', '重复', '重复-1'])
})
