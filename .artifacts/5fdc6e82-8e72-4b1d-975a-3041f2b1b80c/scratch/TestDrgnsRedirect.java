import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class TestDrgnsRedirect {
    public static void main(String[] args) {
        try {
            URL url = new URL("https://pipedapi.drgns.space/streams/dQw4w9WgXcQ");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(true);
            System.out.println("Final URL: " + conn.getURL());
            System.out.println("Code: " + conn.getResponseCode());
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
                System.out.println("URL: " + json.substring(urlIdx + 7, urlEnd));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
