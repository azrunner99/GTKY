import BabyTunesPlayer from "./components/BabyTunesPlayer";

export default function Home() {
  return (
    <main className="flex flex-1 flex-col items-center justify-center min-h-screen bg-gradient-to-b from-purple-100 via-pink-50 to-blue-100 p-8">
      <BabyTunesPlayer />
    </main>
  );
}
