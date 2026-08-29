import type { Metadata } from "next";
import { IBM_Plex_Mono, IBM_Plex_Sans, IBM_Plex_Serif } from "next/font/google";
import "./globals.css";
import { SiteHeader } from "@/components/SiteHeader";
import { SiteFooter } from "@/components/SiteFooter";

/** Eine Schriftfamilie in drei Stimmen: Serife fuer Titel, Sans fuer Fliesstext, Mono fuer Messwerte. */
const display = IBM_Plex_Serif({
  variable: "--font-display",
  subsets: ["latin"],
  weight: ["400", "500", "600"],
});

const sans = IBM_Plex_Sans({
  variable: "--font-sans",
  subsets: ["latin"],
  weight: ["400", "500", "600"],
});

const mono = IBM_Plex_Mono({
  variable: "--font-mono",
  subsets: ["latin"],
  weight: ["400", "500"],
});

export const metadata: Metadata = {
  title: "Tausendfüßler · Leitstand",
  description:
    "Browser-Leitstand für den verteilten Webcrawler Tausendfüßler – Aufträge anlegen, live verfolgen und die gecrawlten Seiten durchsuchen.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="de"
      className={`${display.variable} ${sans.variable} ${mono.variable} h-full antialiased`}
    >
      <body className="flex min-h-full flex-col">
        <a
          href="#inhalt"
          className="mono sr-only bg-[var(--ink)] px-4 py-2 text-[0.75rem] text-white focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-[100]"
        >
          Zum Inhalt springen
        </a>
        <SiteHeader />
        <main
          id="inhalt"
          className="mx-auto w-full max-w-[1160px] flex-1 px-4 pb-20 pt-8 sm:px-6 md:px-10 md:pt-10"
        >
          {children}
        </main>
        <SiteFooter />
      </body>
    </html>
  );
}
