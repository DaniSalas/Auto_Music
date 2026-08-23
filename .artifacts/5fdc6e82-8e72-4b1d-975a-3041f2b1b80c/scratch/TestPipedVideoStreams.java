import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class TestPipedVideoStreams {
    public static void main(String[] args) {
        try {
            String target = "https://piped.video/streams/dQw4w9WgXcQ";
            HttpURLConnection conn = (HttpURLConnection) new URL(target).openConnection();
            System.out.println("Response Code: " + conn.getResponseCode());
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            String json = sb.toString();
            System.out.println("JSON length: " + json.length());
            System.out.println("Contains audioStreams: " + json.contains("audioStreams"));
            int idx = json.indexOf("audioStreams");
            if (idx != -1) {
                System.out.println(json.substring(idx, Math.min(json.length(), idx + 500)));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
