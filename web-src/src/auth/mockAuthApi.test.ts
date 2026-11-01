import { beforeEach, describe, expect, it } from 'vitest'
import {
  isUkmaEmail,
  loginMock,
  registerMock,
  resendMockCode,
  verifyMock,
} from './mockAuthApi'

describe('mockAuthApi (US-001 shell)', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  describe('isUkmaEmail', () => {
    it.each(['student@ukma.edu.ua', 'A.B@UKMA.EDU.UA'])(
      'приймає %s',
      (email) => {
        expect(isUkmaEmail(email)).toBe(true)
      },
    )

    it.each(['a@gmail.com', 'ukma.edu.ua', ''])('відхиляє %s', (email) => {
      expect(isUkmaEmail(email)).toBe(false)
    })
  })

  describe('registerMock', () => {
    it('відхиляє не-ukma домен', () => {
      const result = registerMock('a@gmail.com', 'secret1')
      expect(result.ok).toBe(false)
      if (!result.ok) {
        expect(result.error).toMatch(/@ukma\.edu\.ua/)
      }
    })

    it('відхиляє короткий пароль', () => {
      const result = registerMock('a@ukma.edu.ua', '123')
      expect(result.ok).toBe(false)
    })

    it('створює pending і повертає demoCode', () => {
      const result = registerMock('new@ukma.edu.ua', 'secret12')
      expect(result.ok).toBe(true)
      if (result.ok) {
        expect(result.data.email).toBe('new@ukma.edu.ua')
        expect(result.data.demoCode).toMatch(/^\d{6}$/)
      }
    })

    it('повторна реєстрація активного акаунта — помилка', () => {
      const first = registerMock('dup@ukma.edu.ua', 'secret12')
      expect(first.ok).toBe(true)
      if (!first.ok) return
      const verified = verifyMock('dup@ukma.edu.ua', first.data.demoCode)
      expect(verified.ok).toBe(true)

      const second = registerMock('dup@ukma.edu.ua', 'other12')
      expect(second.ok).toBe(false)
    })
  })

  describe('verifyMock / loginMock', () => {
    it('правильний код активує; логін працює', () => {
      const reg = registerMock('ok@ukma.edu.ua', 'secret12')
      expect(reg.ok).toBe(true)
      if (!reg.ok) return

      const beforeLogin = loginMock('ok@ukma.edu.ua', 'secret12')
      expect(beforeLogin.ok).toBe(false)

      const verified = verifyMock('ok@ukma.edu.ua', reg.data.demoCode)
      expect(verified.ok).toBe(true)

      const loggedIn = loginMock('ok@ukma.edu.ua', 'secret12')
      expect(loggedIn.ok).toBe(true)
      if (loggedIn.ok) {
        expect(loggedIn.data.role).toBe('user')
      }
    })

    it('невірний код збільшує спроби', () => {
      const reg = registerMock('try@ukma.edu.ua', 'secret12')
      expect(reg.ok).toBe(true)
      if (!reg.ok) return

      const bad = verifyMock('try@ukma.edu.ua', '000000')
      expect(bad.ok).toBe(false)
      if (!bad.ok) {
        expect(bad.error).toMatch(/1\/5/)
      }
    })

    it('resendMockCode видає новий код', () => {
      const reg = registerMock('resend@ukma.edu.ua', 'secret12')
      expect(reg.ok).toBe(true)
      if (!reg.ok) return

      const resent = resendMockCode('resend@ukma.edu.ua')
      expect(resent.ok).toBe(true)
      if (!resent.ok) return

      const withOld = verifyMock('resend@ukma.edu.ua', reg.data.demoCode)
      expect(withOld.ok).toBe(false)

      const withNew = verifyMock('resend@ukma.edu.ua', resent.data.demoCode)
      expect(withNew.ok).toBe(true)
    })

    it('невірний пароль після активації', () => {
      const reg = registerMock('pass@ukma.edu.ua', 'secret12')
      expect(reg.ok).toBe(true)
      if (!reg.ok) return
      expect(verifyMock('pass@ukma.edu.ua', reg.data.demoCode).ok).toBe(true)

      const bad = loginMock('pass@ukma.edu.ua', 'wrong-password')
      expect(bad.ok).toBe(false)
    })
  })
})
