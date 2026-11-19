import { THEME_IDS, THEME_LABELS, type ThemeId } from '../theme/themes'
import { useTheme } from '../theme/ThemeContext'
import './ThemeSwitcher.css'

function ThemeSwitcher() {
  const { theme, setTheme } = useTheme()

  return (
    <div
      className="theme-switcher"
      role="group"
      aria-label="Тема оформлення"
    >
      {THEME_IDS.map((id) => (
        <button
          key={id}
          type="button"
          className={`theme-switcher__swatch theme-switcher__swatch--${id}${
            theme === id ? ' theme-switcher__swatch--active' : ''
          }`}
          aria-label={THEME_LABELS[id]}
          aria-pressed={theme === id}
          title={THEME_LABELS[id]}
          onClick={() => setTheme(id as ThemeId)}
        />
      ))}
    </div>
  )
}

export default ThemeSwitcher
