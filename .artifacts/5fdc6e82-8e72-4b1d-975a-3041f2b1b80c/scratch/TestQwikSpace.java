import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class TestQwikSpace {
    public static void main(String[] args) {
        try {
            String target = "https://pipedapi.qwik.space/streams/dQw4w9WgXcQ";
            HttpURLConnection conn = (HttpURLConnection) new URL(target).openConnection();
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            String json = sb.toString();
            System.out.println("QwikSpace JSON: " + json);
            int urlIdx = json.indexOf("\"url\":\"");
            if (urlIdx != -1) {
                int urlEnd = json.indexOf("\"", urlIdx + 7);
                String streamUrl = json.substring(urlIdx + 7, urlEnd);
                System.out.println("AUDIO STREAM URL: " + streamUrl);
                
                HttpURLConnection audioConn = (HttpURLConnection) new URL(streamUrl).openConnection();
                audioConn.setRequestMethod("HEAD");
                System.out.println("Audio Response Code: " + audioConn.getResponseCode());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
