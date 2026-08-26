import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class DumpWatchPage {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        try {
            URL url = new URL("https://www.youtube.com/watch?v=" + videoId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.3");
            
            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.contains("ytInitialPlayerResponse = ")) {
                        int start = line.indexOf("ytInitialPlayerResponse = ");
                        System.out.println("FOUND PLAYER RESPONSE. Snippet:");
                        System.out.println(line.substring(start, Math.min(line.length(), start + 2000)));
                        
                        if (line.contains("googlevideo.com")) {
                            System.out.println("\n--- FOUND GOOGLEVIDEO ---");
                            int gv = line.indexOf("googlevideo.com");
                            System.out.println(line.substring(Math.max(0, gv - 100), Math.min(line.length(), gv + 500)));
                        }
                        return;
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
