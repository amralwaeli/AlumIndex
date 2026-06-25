import type { Config } from 'tailwindcss'
import animate from 'tailwindcss-animate'

const config: Config = {
  content: [
    './index.html',
    './src/**/*.{ts,tsx}',
  ],
  theme: {
    extend: {
      colors: {
        // Super Admin shell — light, UTM-branded (was navy; lightened per supervisor)
        ink: {
          DEFAULT: '#F5F4EF',
          panel: '#FFFFFF',
          line: '#E5E2D9',
          text: '#172230',
          muted: '#657182',
        },
        // University light shell — bone
        bone: '#F5F4EF',
        surface: '#FFFFFF',
        line: '#E5E2D9',
        text: '#172230',
        muted: '#657182',
        // Primary — UTM maroon
        sapphire: {
          DEFAULT: '#8C1D40',
          dark: '#6B1531',
          soft: '#F7E7EC',
        },
        // UTM maroon alias (for new UTM-branded components)
        utm: {
          DEFAULT: '#8C1D40',
          dark: '#6B1531',
          soft: '#F7E7EC',
        },
        // Accents
        gold: {
          DEFAULT: '#A9791F',
          soft: '#F3E9CF',
        },
        emerald: '#1C8A5A',
        amber: '#C9791C',
        danger: '#BB3B2E',
        violet: '#6D4AA6',
      },
      fontFamily: {
        display: ['Fraunces', 'Georgia', 'serif'],
        sans: ['Inter', 'system-ui', 'sans-serif'],
        mono: ['IBM Plex Mono', 'monospace'],
      },
    },
  },
  plugins: [animate],
}

export default config
