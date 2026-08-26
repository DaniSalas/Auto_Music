import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class InspectPbj {
    public static void main(String[] args) {
        String videoId = "uzF0M-9fO_M";
        try {
            URL url = new URL("https://www.youtube.com/watch?v=" + videoId + "&pbj=1");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.3");
            conn.setRequestProperty("X-YouTube-Client-Name", "1");
            conn.setRequestProperty("X-YouTube-Client-Version", "2.20240821.01.00");
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("\"itag\":140")) {
                    int itagIdx = line.indexOf("\"itag\":140");
                    System.out.println("Snippet: " + line.substring(itagIdx, Math.min(line.length(), itagIdx + 1500)));
                    return;
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
