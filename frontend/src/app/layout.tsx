import type { Metadata, Viewport } from 'next';
import { AppProviders } from '@/providers/AppProviders';
import { themeScript } from '@/providers/ThemeProvider';
import './globals.css';

export const metadata: Metadata = {
  title: {
    default: 'Two & Two',
    template: '%s · Two & Two',
  },
  description: 'Meet people you would actually like. One good match at a time.',
  applicationName: 'Two & Two',
  robots: { index: false, follow: false },
};

export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
  maximumScale: 1,
  themeColor: [
    { media: '(prefers-color-scheme: light)', color: '#fcfbfa' },
    { media: '(prefers-color-scheme: dark)', color: '#0d0b10' },
  ],
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning>
      <head>
        {/*
          Loaded as a stylesheet link rather than next/font so a build or a boot without
          network access still produces a working app - it simply falls back to the system
          stack declared alongside these families in globals.css.
        */}
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="anonymous" />
        {/*
          eslint-disable-next-line @next/next/no-page-custom-font -- the rule is about the
          pages router, where a head link loads per page. In the app router this lives in
          the single root layout, so it is requested once for the whole application.
        */}
        <link
          rel="stylesheet"
          href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&family=Fraunces:opsz,wght@9..144,400;9..144,500;9..144,600;9..144,700&display=swap"
        />
        {/* Sets the palette before the first paint, so a dark-mode device never flashes white. */}
        <script dangerouslySetInnerHTML={{ __html: themeScript }} />
      </head>
      <body>
        <AppProviders>{children}</AppProviders>
      </body>
    </html>
  );
}
