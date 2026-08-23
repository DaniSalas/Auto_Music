import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class TestRealPipedInstances {
    public static void main(String[] args) {
        String[] apis = {
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.drgns.space",
            "https://api.piped.privacy.com.de",
            "https://pipedapi.adminforge.de",
            "https://pipedapi.privacydev.net",
            "https://api.piped.projectsegfau.lt"
        };
        for (String api : apis) {
            try {
                String target = api + "/healthcheck";
                HttpURLConnection conn = (HttpURLConnection) new URL(target).openConnection();
                conn.setConnectTimeout(3000);
                if (conn.getResponseCode() == 200) {
                    System.out.println("HEALTHY API: " + api);
                }
            } catch (Exception e) {}
        }
    }
}
