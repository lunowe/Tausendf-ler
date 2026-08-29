import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // The repo documents the frontend in the root README; no generated agent rule files.
  agentRules: false,
};

export default nextConfig;
