import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class ScrapeEmbed {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        try {
            URL url = new URL("https://www.youtube.com/embed/" + videoId + "?autoplay=1");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.3");
            
            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("googlevideo.com")) {
                        System.out.println("FOUND GOOGLEVIDEO IN EMBED HTML!");
                        int start = line.indexOf("https://");
                        while(start != -1) {
                            int end = line.indexOf("\"", start);
                            if (end == -1) end = line.indexOf("\\\"", start);
                            if (end != -1) {
                                String streamUrl = line.substring(start, end).replace("\\u0026", "&");
                                if (streamUrl.contains("googlevideo.com")) {
                                    System.out.println("POTENTIAL URL: " + streamUrl.substring(0, 100) + "...");
                                    verify(streamUrl);
                                }
                            }
                            start = line.indexOf("https://", start + 1);
                        }
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static void verify(String u) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(u).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Range", "bytes=0-1024");
            int code = conn.getResponseCode();
            System.out.println("  Code: " + code);
            if (code == 200 || code == 206) System.out.println("  ✅ WORKED!");
        } catch (Exception e) {}
    }
}
