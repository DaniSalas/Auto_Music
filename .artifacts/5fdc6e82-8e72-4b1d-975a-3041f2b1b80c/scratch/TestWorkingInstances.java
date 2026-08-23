import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class TestWorkingInstances {
    public static void main(String[] args) {
        String[] instances = {
            "https://invidious.privacyredirect.com",
            "https://invidious.projectsegfau.lt",
            "https://vid.puffyan.us",
            "https://invidious.fdn.fr",
            "https://inv.us.projectsegfau.lt",
            "https://yt.artemislabs.eu"
        };
        for (String inst : instances) {
            try {
                String target = inst + "/api/v1/videos/dQw4w9WgXcQ";
                System.out.println("Testing: " + target);
                HttpURLConnection conn = (HttpURLConnection) new URL(target).openConnection();
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);
                int code = conn.getResponseCode();
                System.out.println("Code: " + code);
                if (code == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    String json = sb.toString();
                    if (json.contains("adaptiveFormats")) {
                        System.out.println("SUCCESSFULLY FOUND WORKING INVIDIOUS INSTANCE: " + inst);
                        return;
                    }
                }
            } catch (Exception e) {
                System.out.println("Failed: " + e.getMessage());
            }
        }
    }
}
