// Where the Next server proxies /api and /ws when the browser is told to use the same origin
// (NEXT_PUBLIC_API_BASE_URL= and NEXT_PUBLIC_WS_URL= left empty). See README: "Testing on a phone".
const BACKEND_URL = process.env.BACKEND_URL ?? 'http://localhost:8080';

/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,

  // Next blocks dev-server assets (HMR, chunks) for origins other than localhost. Allow the
  // LAN address a phone uses, plus tunnel hosts.
  allowedDevOrigins: [
    '127.0.0.1',
    ...(process.env.DEV_LAN_HOST ?? '192.168.1.3').split(','),
    '*.trycloudflare.com',
    '*.ngrok-free.app',
  ],

  // The API base is read at build time for the server components and at runtime for the
  // browser bundle. Both point at the Spring Boot service.
  env: {
    NEXT_PUBLIC_API_BASE_URL: process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080',
    NEXT_PUBLIC_WS_URL: process.env.NEXT_PUBLIC_WS_URL ?? 'http://localhost:8080/ws',
  },

  images: {
    remotePatterns: [
      { protocol: 'http', hostname: 'localhost' },
      { protocol: 'https', hostname: '**' },
    ],
  },

  async rewrites() {
    return [
      { source: '/api/v1/:path*', destination: `${BACKEND_URL}/api/v1/:path*` },
      { source: '/ws/:path*', destination: `${BACKEND_URL}/ws/:path*` },
    ];
  },

  // Security headers. The API sets its own; these cover the documents Next serves.
  async headers() {
    return [
      {
        source: '/:path*',
        headers: [
          { key: 'X-Content-Type-Options', value: 'nosniff' },
          { key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin' },
          { key: 'X-Frame-Options', value: 'DENY' },
          { key: 'Permissions-Policy', value: 'camera=(self), microphone=(self), geolocation=(self)' },
        ],
      },
    ];
  },
};

export default nextConfig;
