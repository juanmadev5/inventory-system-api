import "dotenv/config"; // Load environment variables
import { createClient } from "@supabase/supabase-js";

if (!process.env.SUPABASE_URL || !process.env.SUPABASE_API_KEY) {
  throw new Error("Supabase URL or API key is missing");
}

// Create a Supabase client instance
const supabase = createClient(
  process.env.SUPABASE_URL,
  process.env.SUPABASE_API_KEY
);

export default supabase; //export supabase server to use it in controllers that requires it
