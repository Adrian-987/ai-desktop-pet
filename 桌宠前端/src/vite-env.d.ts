/// <reference types="vite/client" />
declare module "*.vue" {
  import type { DefineComponent } from "vue"
  const component: DefineComponent<{}, {}, any>
  export default component
}
declare module "audiobuffer-to-wav" {
    function toWav(buffer: AudioBuffer): ArrayBuffer
    export default toWav
}
declare module "hark" {
    interface HarkOptions { threshold?: number; interval?: number }
    interface HarkInstance {
        on(event: "speaking" | "stopped_speaking", cb: () => void): void
        stop(): void
    }
    function hark(stream: MediaStream, options?: HarkOptions): HarkInstance
    export default hark
}