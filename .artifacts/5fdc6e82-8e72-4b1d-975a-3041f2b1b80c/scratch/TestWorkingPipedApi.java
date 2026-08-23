import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class TestWorkingPipedApi {
    public static void main(String[] args) {
        String[] apis = {
            "https://pipedapi.adminforge.de",
            "https://pipedapi.privacydev.net",
            "https://api.piped.projectsegfau.lt"
        };
        for (String api : apis) {
            try {
                String target = api + "/streams/dQw4w9WgXcQ";
                System.out.println("Testing: " + target);
                HttpURLConnection conn = (HttpURLConnection) new URL(target).openConnection();
                conn.setConnectTimeout(4000);
                int code = conn.getResponseCode();
                System.out.println("Code: " + code);
                if (code == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    String json = sb.toString();
                    System.out.println("JSON length: " + json.length());
                    int urlIdx = json.indexOf("\"url\":\"");
                    if (urlIdx != -1) {
                        int urlEnd = json.indexOf("\"", urlIdx + 7);
                        String streamUrl = json.substring(urlIdx + 7, urlEnd);
                        System.out.println("WORKING AUDIO URL FOUND: " + streamUrl);
                        
                        HttpURLConnection audioConn = (HttpURLConnection) new URL(streamUrl).openConnection();
                        audioConn.setRequestMethod("HEAD");
                        System.out.println("Audio Head Response Code: " + audioConn.getResponseCode());
                        return;
                    }
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }
}
