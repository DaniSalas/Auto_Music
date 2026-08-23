import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class TestWebpageScrape {
    public static void main(String[] args) {
        try {
            URL url = new URL("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.3");
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            String html = sb.toString();
            System.out.println("HTML length: " + html.length());
            System.out.println("Contains ytInitialPlayerResponse: " + html.contains("ytInitialPlayerResponse"));
            int idx = html.indexOf("ytInitialPlayerResponse = ");
            if (idx != -1) {
                int endIdx = html.indexOf(";</script>", idx);
                String json = html.substring(idx + 26, endIdx);
                System.out.println("Extracted player response JSON length: " + json.length());
                System.out.println("Contains streamingData: " + json.contains("streamingData"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
