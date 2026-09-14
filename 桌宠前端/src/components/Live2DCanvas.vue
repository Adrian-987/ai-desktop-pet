<template>
  <div ref="containerRef" class="live2d-container"></div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from "vue"
import { PixiManager, Live2DManager } from "../live2d"

const props = withDefaults(defineProps<{
  width?: number
  height?: number
  modelName?: string
  modelFile?: string
  scale?: number
  xMultiplier?: number
  yMultiplier?: number
  debugCanvasBg?: boolean
}>(), {
  width: 300,
  height: 400,
  modelName: "LSS"
})

const emit = defineEmits<{
  (e: "loaded", name: string): void
  (e: "hit", areas: string[]): void
}>()

const containerRef = ref<HTMLDivElement>()
const pixiVal = ref<PixiManager | null>(null)
const live2dVal = ref<Live2DManager | null>(null)

onMounted(async () => {
  if (!containerRef.value) return
  const pm = new PixiManager(containerRef.value, props.width, props.height, props.debugCanvasBg)
  pixiVal.value = pm
  const lm = new Live2DManager(pm)
  live2dVal.value = lm
  try {
    await lm.loadModel(props.modelName, props.modelFile, {
      scale: props.scale,
      xMultiplier: props.xMultiplier,
      yMultiplier: props.yMultiplier
    })
    lm.onHit((areas) => emit("hit", areas))
    emit("loaded", props.modelName)
  } catch (e) {
    console.error("Live2D load failed:", e)
  }
})

// HMR: reload model on name change, adjust scale/position/canvas on other changes
watch(() => props.modelName, async (name) => {
  if (!live2dVal.value || !pixiVal.value || !name) return
  try {
    await live2dVal.value.loadModel(name, props.modelFile, {
      scale: props.scale,
      xMultiplier: props.xMultiplier,
      yMultiplier: props.yMultiplier
    })
  } catch (e) { console.error("Live2D reload failed:", e) }
})
watch(() => props.scale, (s) => {
  if (s !== undefined) live2dVal.value?.setScale(s)
})
watch(() => [props.xMultiplier, props.yMultiplier], () => {
  live2dVal.value?.center()
  // Recompute position from multipliers in Live2DManager
  const lm = live2dVal.value
  const pm = pixiVal.value
  if (lm && pm && props.xMultiplier !== undefined && props.yMultiplier !== undefined) {
    lm.setPosition(pm.width * props.xMultiplier, pm.height * props.yMultiplier)
  }
})
watch(() => [props.width, props.height], ([w, h]) => {
  if (w && h) { pixiVal.value?.resize(w, h); live2dVal.value?.center() }
})

onBeforeUnmount(() => {
  live2dVal.value?.destroy()
  pixiVal.value?.destroy()
  live2dVal.value = null
  pixiVal.value = null
})

function resize(w: number, h: number): void {
  pixiVal.value?.resize(w, h)
  live2dVal.value?.center()
}

defineExpose({
  get live2d() { return live2dVal.value },
  get pixi() { return pixiVal.value },
  resize
})
</script>

<style scoped>
.live2d-container {
  width: 100%;
  height: 100%;
  
}
.live2d-container :deep(canvas) {
  pointer-events: auto;
}
</style>
