import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import OrganizationHeadsPanel from './OrganizationHeadsPanel'
import { ApiError } from '../api/client'
import type { OrganizationHead, RoleChange } from '../types/head'
import { ORG_ID } from '../test/support/testFixtures'

vi.mock('../api/heads', () => ({
  listHeads: vi.fn(),
  appointHead: vi.fn(),
  removeHead: vi.fn(),
  roleLog: vi.fn(),
}))

const { appointHead, listHeads, removeHead, roleLog } =
  await import('../api/heads')
const listMock = vi.mocked(listHeads)
const appointMock = vi.mocked(appointHead)
const removeMock = vi.mocked(removeHead)
const logMock = vi.mocked(roleLog)

const HEAD_ID = '9b1c7a40-0000-4000-8000-000000000001'

const head: OrganizationHead = {
  userId: HEAD_ID,
  email: 'head@ukma.edu.ua',
  appointedBy: null,
  appointedAt: '2026-10-21T13:00:00+03:00',
}

const logEntry: RoleChange = {
  id: 'c0ffee00-0000-4000-8000-000000000001',
  organizationId: ORG_ID,
  targetUserId: HEAD_ID,
  targetEmail: 'head@ukma.edu.ua',
  actorUserId: null,
  actorEmail: 'admin@ukma.edu.ua',
  action: 'APPOINTED',
  createdAt: '2026-10-21T13:00:00+03:00',
}

beforeEach(() => {
  listMock.mockResolvedValue([head])
  logMock.mockResolvedValue([logEntry])
})

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

function renderPanel() {
  return render(<OrganizationHeadsPanel organizationId={ORG_ID} />)
}

describe('OrganizationHeadsPanel', () => {
  it('показує голів організації', async () => {
    renderPanel()

    expect(await screen.findByText(/head@ukma\.edu\.ua/)).toBeInTheDocument()
    expect(listMock).toHaveBeenCalledWith(ORG_ID)
  })

  it('призначає голову за поштою і перезавантажує список', async () => {
    appointMock.mockResolvedValue(head)
    const user = userEvent.setup()
    renderPanel()
    await screen.findByText(/head@ukma\.edu\.ua/)

    await user.type(
      screen.getByLabelText('Призначити голову'),
      '  new@ukma.edu.ua  ',
    )
    await user.click(screen.getByRole('button', { name: 'Призначити' }))

    // пошту обрізаємо перед відправкою
    expect(appointMock).toHaveBeenCalledWith(ORG_ID, {
      email: 'new@ukma.edu.ua',
    })
    expect(await screen.findByText('Голову призначено')).toBeInTheDocument()
    expect(listMock).toHaveBeenCalledTimes(2)
  })

  it('знімає голову', async () => {
    removeMock.mockResolvedValue(undefined)
    const user = userEvent.setup()
    renderPanel()
    await screen.findByText(/head@ukma\.edu\.ua/)

    await user.click(screen.getByRole('button', { name: 'Зняти' }))

    expect(removeMock).toHaveBeenCalledWith(ORG_ID, HEAD_ID)
    expect(
      await screen.findByText('head@ukma.edu.ua більше не голова'),
    ).toBeInTheDocument()
  })

  it('останнього голову зняти не можна: показує повідомлення бекенду', async () => {
    removeMock.mockRejectedValue(
      new ApiError(
        409,
        '{"error":{"code":"LAST_HEAD"}}',
        '{"error":{"code":"LAST_HEAD","message":"Останнього голову зняти не можна"}}',
      ),
    )
    const user = userEvent.setup()
    renderPanel()
    await screen.findByText(/head@ukma\.edu\.ua/)

    await user.click(screen.getByRole('button', { name: 'Зняти' }))

    expect(
      await screen.findByText('Останнього голову зняти не можна'),
    ).toBeInTheDocument()
  })

  it('журнал ролей розкривається на вимогу', async () => {
    const user = userEvent.setup()
    renderPanel()
    await screen.findByText(/head@ukma\.edu\.ua/)

    expect(screen.queryByText(/призначено/)).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Журнал ролей (1)' }))

    expect(
      screen.getByText(
        /head@ukma\.edu\.ua — призначено \(admin@ukma\.edu\.ua\)/,
      ),
    ).toBeInTheDocument()
  })

  it('немає прав: панель показує повідомлення бекенду, а не падає', async () => {
    const body =
      '{"error":{"code":"FORBIDDEN","message":"Недостатньо прав для цієї дії"}}'
    listMock.mockRejectedValue(new ApiError(403, body, body))
    renderPanel()

    await waitFor(() =>
      expect(
        screen.getByText('Недостатньо прав для цієї дії'),
      ).toBeInTheDocument(),
    )
    expect(
      screen.getByRole('button', { name: 'Призначити' }),
    ).toBeInTheDocument()
  })
})
