import * as PIXI from "pixi.js"

export class PixiManager {
  readonly app: PIXI.Application
  readonly canvas: HTMLCanvasElement
  private _width: number
  private _height: number

  constructor(container: HTMLElement, width: number, height: number, debugBg = false) {
    this._width = width
    this._height = height
    this.app = new PIXI.Application({
      width,
      height,
      backgroundAlpha: debugBg ? 1 : 0,
      backgroundColor: debugBg ? 0x1a1a2e : 0x000000,
      antialias: true,
      resolution: 2,
      autoDensity: true,
      preserveDrawingBuffer: true,
    })
    this.canvas = this.app.view as HTMLCanvasElement
    this.canvas.style.display = "block"

    // Prevent PIXI v7 event system from traversing into the stage
    this.app.stage.eventMode = "none"
    this.app.stage.interactiveChildren = false

    container.appendChild(this.canvas)
  }

  get width(): number { return this._width }
  get height(): number { return this._height }

  resize(width: number, height: number): void {
    this._width = width
    this._height = height
    this.app.renderer.resize(width, height)
  }

  destroy(): void {
    this.app.destroy(true, { children: true, texture: true })
  }
}
