import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class TestProjectSegfauApi {
    public static void main(String[] args) {
        try {
            String target = "https://api.piped.projectsegfau.lt/api/v1/videos/dQw4w9WgXcQ";
            HttpURLConnection conn = (HttpURLConnection) new URL(target).openConnection();
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            String json = sb.toString();
            int idx = json.indexOf("audioStreams");
            System.out.println("Found audioStreams at: " + idx);
            if (idx != -1) {
                int urlIdx = json.indexOf("\"url\":\"", idx);
                int endIdx = json.indexOf("\"", urlIdx + 7);
                String url = json.substring(urlIdx + 7, endIdx);
                System.out.println("WORKING AUDIO URL: " + url);
                
                HttpURLConnection audioConn = (HttpURLConnection) new URL(url).openConnection();
                audioConn.setRequestMethod("HEAD");
                System.out.println("Audio Response Code: " + audioConn.getResponseCode());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
