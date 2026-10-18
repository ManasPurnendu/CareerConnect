/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        // Stitch Contemporary Design System Tokens
        primary: {
          DEFAULT: "#006948",
          dark: "#005237",
          light: "#00855d",
          accent: "#059669",
          tint: "#ECFDF5",
          fixed: "#85f8c4",
          "fixed-dim": "#68dba9",
        },
        secondary: {
          DEFAULT: "#2b6954",
          container: "#adedd3",
          dark: "#0f513d",
        },
        surface: {
          DEFAULT: "#FFFFFF",
          white: "#FFFFFF",
          warm: "#F7F7F2",
          dim: "#cfdcd3",
          low: "#f2fcf5",
          container: "#e6f0e9",
          highest: "#dbe5de",
        },
        warm: {
          bg: "#F7F7F2",
          border: "#E7E7E0",
          card: "#FFFFFF",
        },
        slate: {
          text: "#17201C",
          on: "#141d1a",
          variant: "#3d4a42",
          muted: "#66736D",
          subtle: "#8A9790",
        },
        outline: {
          DEFAULT: "#6d7a72",
          variant: "#bccac0",
          warm: "#E7E7E0",
        },
        status: {
          review: "#D97706",
          "review-bg": "#FFFBEB",
          "review-border": "#FDE68A",
          interview: "#2563EB",
          "interview-bg": "#EFF6FF",
          "interview-border": "#BFDBFE",
          offered: "#059669",
          "offered-bg": "#ECFDF5",
          "offered-border": "#A7F3D0",
          rejected: "#DC2626",
          "rejected-bg": "#FEF2F2",
          "rejected-border": "#FECACA",
          withdrawn: "#78716C",
          "withdrawn-bg": "#F5F5F4",
          "withdrawn-border": "#E7E5E4",
        },
      },
      fontFamily: {
        sans: ["Geist", "Inter", "-apple-system", "BlinkMacSystemFont", "Segoe UI", "sans-serif"],
        headline: ["Geist", "Inter", "sans-serif"],
        body: ["Inter", "-apple-system", "sans-serif"],
        mono: ["Geist Mono", "ui-monospace", "monospace"],
      },
      maxWidth: {
        content: "1240px",
        page: "1240px",
      },
      borderRadius: {
        DEFAULT: "0.25rem",
        sm: "0.125rem",
        md: "0.375rem",
        lg: "0.5rem",
        xl: "0.75rem",
      },
      boxShadow: {
        subtle: "0 1px 3px rgba(0, 0, 0, 0.05)",
        card: "0 1px 3px rgba(0, 0, 0, 0.04), 0 1px 2px rgba(0, 0, 0, 0.02)",
        elevation: "0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -2px rgba(0, 0, 0, 0.05)",
        dropdown: "0 10px 15px -3px rgba(0, 0, 0, 0.08), 0 4px 6px -4px rgba(0, 0, 0, 0.03)",
      },
    },
  },
  plugins: [],
}
