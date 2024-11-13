import cors from "cors";
import express, { json, urlencoded } from "express";

const app = express();
app.use(urlencoded({ extended: true }));
app.use(cors()); // Enable Cross-Origin Resource Sharing
app.use(json()); // Parse JSON bodies

export default app;
