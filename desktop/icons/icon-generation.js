import { createCanvas } from 'canvas';
const canvas = createCanvas(256, 256);
const ctx = canvas.getContext('2d');
ctx.fillStyle = '#111827'; ctx.fillRect(0, 0, 256, 256);
ctx.fillStyle = '#22d3ee'; ctx.beginPath(); ctx.roundRect(32, 32, 192, 192, 44); ctx.fill();
ctx.fillStyle = '#0f172a'; ctx.beginPath(); ctx.moveTo(70, 128); ctx.lineTo(118, 78); ctx.lineTo(118, 108); ctx.lineTo(186, 108); ctx.lineTo(186, 148); ctx.lineTo(118, 148); ctx.lineTo(118, 178); ctx.closePath(); ctx.fill();
