import { expect, test } from '@playwright/test'

test('administrator logs in and opens user administration', async ({ page }) => {
  await page.goto('/login')
  await expect(page.getByPlaceholder('请输入用户名：admin')).toBeFocused()
  await page.getByLabel('用户名').fill('admin')
  await page.getByLabel('密码').fill('Admin#ChangeMe123')
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/\/workspace/)
  await page.getByRole('link', { name: '用户管理' }).click()
  await expect(page.getByRole('heading', { name: '用户管理' })).toBeVisible()
})
