import { drawImageOnCanvas } from "./utils.js";

const canvas = document.getElementById("frameCanvas") as HTMLCanvasElement;
const ctx = canvas.getContext("2d")!;
const frameTypeEl = document.getElementById("frameType")!;
const fpsEl = document.getElementById("fps")!;
const resolutionEl = document.getElementById("resolution")!;
const toggleBtn = document.getElementById("toggleBtn") as HTMLButtonElement;

const rawImage = new Image();
rawImage.src = "./capture_raw.png";

const processedImage = new Image();
processedImage.src = "./capture_edges.png";

let showProcessed = true;
let fps = "28-32";

function drawCurrentFrame() {
    const image = showProcessed ? processedImage : rawImage;

    // Set canvas size equal to image size
    canvas.width = image.naturalWidth;
    canvas.height = image.naturalHeight;

    // Draw the image
    drawImageOnCanvas(ctx, image);

    frameTypeEl.textContent = showProcessed ? "Processed" : "Raw";
    fpsEl.textContent = fps;
    resolutionEl.textContent = `${canvas.width}x${canvas.height}`;
}

// Toggle button
toggleBtn.addEventListener("click", () => {
    showProcessed = !showProcessed;
    drawCurrentFrame();
});

// Wait for both images to load
let imagesLoaded = 0;
[rawImage, processedImage].forEach(img => {
    img.onload = () => {
        imagesLoaded++;
        if (imagesLoaded === 2) {
            drawCurrentFrame();
        }
    };
});


