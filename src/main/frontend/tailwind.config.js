/** @type {import('tailwindcss').Config} */
//
// Tokens del sistema MODERNIST, la estética de toda la aplicación (decisión D-009,
// 2026-08-26). Origen:
//   los prototipos de diseño (login/_ds/modernist/styles.css)
//
// Los valores NO se copian aquí: se apuntan a las variables CSS que define
// `styles.css`. Así hay una sola fuente de verdad —retocar un token allí cambia a la
// vez las clases de componente (.btn, .input) y las utilidades de Tailwind—, que es
// justo lo que el readme del design system pide.
//
// La contrapartida de indirectar por var(): los modificadores de opacidad de Tailwind
// (`bg-accent/50`) no funcionan. No hacen falta — el sistema resuelve las mezclas con
// color-mix(), como en `.btn-secondary:hover`.
//
// Los tokens índigo de los prototipos de diseño (vistas_tailwind) (canvas #F4F7FE, brand #2B57E0,
// Inter) se retiran: son el otro lenguaje visual del repo de prototipos y quedó
// descartado. El sidebar y las tarjetas de esas vistas se reconstruyen con estos
// tokens en el hito 3. Ver docs/DECISIONES.md
module.exports = {
  content: [
    "../resources/templates/**/*.{html,js}",
    "../resources/static/js/**/*.js"
  ],
  theme: {
    extend: {
      colors: {
        bg: 'var(--color-bg)',
        surface: 'var(--color-surface)',
        ink: 'var(--color-text)',
        divider: 'var(--color-divider)',
        accent: {
          DEFAULT: 'var(--color-accent)',
          100: 'var(--color-accent-100)', 200: 'var(--color-accent-200)',
          300: 'var(--color-accent-300)', 400: 'var(--color-accent-400)',
          500: 'var(--color-accent-500)', 600: 'var(--color-accent-600)',
          700: 'var(--color-accent-700)', 800: 'var(--color-accent-800)',
          900: 'var(--color-accent-900)'
        },
        accent2: {
          DEFAULT: 'var(--color-accent-2)',
          100: 'var(--color-accent-2-100)', 200: 'var(--color-accent-2-200)',
          300: 'var(--color-accent-2-300)', 400: 'var(--color-accent-2-400)',
          500: 'var(--color-accent-2-500)', 600: 'var(--color-accent-2-600)',
          700: 'var(--color-accent-2-700)', 800: 'var(--color-accent-2-800)',
          900: 'var(--color-accent-2-900)'
        },
        // Semánticos (éxito, error, advertencia): no cambian con la marca.
        success: { DEFAULT: 'var(--color-success)', 100: 'var(--color-success-100)', 800: 'var(--color-success-800)' },
        danger: { DEFAULT: 'var(--color-danger)', 100: 'var(--color-danger-100)', 800: 'var(--color-danger-800)' },
        warning: { DEFAULT: 'var(--color-warning)', 100: 'var(--color-warning-100)', 800: 'var(--color-warning-800)' },
        // Sustituye la escala `neutral` de fábrica de Tailwind (grises fríos) por la
        // rampa cálida del sistema, generada en OKLCH sobre la misma luminosidad.
        neutral: {
          100: 'var(--color-neutral-100)', 200: 'var(--color-neutral-200)',
          300: 'var(--color-neutral-300)', 400: 'var(--color-neutral-400)',
          500: 'var(--color-neutral-500)', 600: 'var(--color-neutral-600)',
          700: 'var(--color-neutral-700)', 800: 'var(--color-neutral-800)',
          900: 'var(--color-neutral-900)'
        }
      },
      fontFamily: {
        sans: ['Archivo', 'system-ui', 'sans-serif'],
        heading: ['Archivo', 'system-ui', 'sans-serif']
      },
      // Reforma 2026-08-28: de esquinas vivas a redondeadas. Igual que colores y
      // sombras, apunta a las variables de styles.css en vez de repetir valores —
      // una sola fuente de verdad. `rounded-full` no se toca: ya funcionaba para
      // avatares y sigue siendo 9999px.
      borderRadius: {
        DEFAULT: 'var(--radius-md)',
        sm: 'var(--radius-sm)',
        md: 'var(--radius-md)',
        lg: 'var(--radius-lg)',
        xl: 'var(--radius-lg)',
        '2xl': 'var(--radius-lg)'
      },
      boxShadow: {
        sm: 'var(--shadow-sm)',
        DEFAULT: 'var(--shadow-md)',
        md: 'var(--shadow-md)',
        lg: 'var(--shadow-lg)'
      }
      // La escala de espaciado del sistema (4/8/12/16/24/32 px) coincide punto por
      // punto con la de Tailwind (1/2/3/4/6/8): no se alias, se usa la de fábrica.
    }
  },
  plugins: []
}
