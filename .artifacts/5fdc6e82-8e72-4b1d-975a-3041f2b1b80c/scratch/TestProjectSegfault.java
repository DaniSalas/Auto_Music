import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class TestProjectSegfault {
    public static void main(String[] args) {
        try {
            String target = "https://invidious.projectsegfau.lt/api/v1/videos/dQw4w9WgXcQ";
            HttpURLConnection conn = (HttpURLConnection) new URL(target).openConnection();
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            String json = sb.toString();
            int idx = json.indexOf("adaptiveFormats");
            System.out.println("Found adaptiveFormats at index: " + idx);
            if (idx != -1) {
                int urlIdx = json.indexOf("\"url\":\"", idx);
                if (urlIdx != -1) {
                    int endIdx = json.indexOf("\"", urlIdx + 7);
                    String url = json.substring(urlIdx + 7, endIdx);
                    System.out.println("EXTRACTED AUDIO URL: " + url);
                    
                    HttpURLConnection audioConn = (HttpURLConnection) new URL(url).openConnection();
                    audioConn.setRequestMethod("HEAD");
                    System.out.println("Audio URL Response Code: " + audioConn.getResponseCode());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
