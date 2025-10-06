export function drawImageOnCanvas(ctx: CanvasRenderingContext2D, image: HTMLImageElement) {
    ctx.clearRect(0, 0, ctx.canvas.width, ctx.canvas.height);
    // Draw the image at its original size
    ctx.drawImage(image, 0, 0);
}
