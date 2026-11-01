/** Ask before clearing the session. Returns true if the user confirmed. */
export function confirmLogout(): boolean {
  return window.confirm('Точно вийти з акаунта?')
}
