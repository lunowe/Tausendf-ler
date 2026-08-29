import { JobView } from "@/components/JobView";

export default async function JobPage({ params }: PageProps<"/jobs/[id]">) {
  const { id } = await params;
  // key: a different job must start with a fresh live-stream cursor, never a reused one.
  return <JobView key={id} jobId={id} />;
}
