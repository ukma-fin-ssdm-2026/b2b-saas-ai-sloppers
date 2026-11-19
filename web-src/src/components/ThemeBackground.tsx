import Grainient from './Grainient'
import { useTheme } from '../theme/ThemeContext'
import { GRAINIENT_PRESETS, isGrainientTheme } from '../theme/themes'

function ThemeBackground() {
  const { theme } = useTheme()

  if (!isGrainientTheme(theme)) return null

  const preset = GRAINIENT_PRESETS[theme]

  return (
    <div className="app-theme-bg" aria-hidden="true">
      <Grainient
        key={theme}
        {...preset}
        interactive={false}
        clickSwirl={false}
        intro
      />
    </div>
  )
}

export default ThemeBackground
